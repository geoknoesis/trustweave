/**
 * Crypto helpers for the Expo wallet.
 *
 * Mostly a port of `reference-wallet/lib/crypto.ts` (web). Two differences:
 *  - SHA-512 for @noble/ed25519 is wired from @noble/hashes (web does the same).
 *  - getRandomValues is provided by the `react-native-get-random-values` polyfill
 *    imported in `polyfills.ts` (loaded first thing in the app entry).
 */
import * as ed25519 from '@noble/ed25519'
import { sha512 } from '@noble/hashes/sha512'
import bs58 from 'bs58'

ed25519.etc.sha512Sync = (...m) => sha512(ed25519.etc.concatBytes(...m))

export interface Ed25519KeyPair {
  publicKey: Uint8Array
  privateKey: Uint8Array
}

export function generateEd25519KeyPair(): Ed25519KeyPair {
  const privateKey = ed25519.utils.randomPrivateKey()
  const publicKey = ed25519.getPublicKey(privateKey)
  return { privateKey, publicKey }
}

/** did:key encoding per W3C method-key — Ed25519 multicodec 0xED 0x01 + Base58btc. */
export function publicKeyToDidKey(publicKey: Uint8Array): string {
  if (publicKey.length !== 32) {
    throw new Error(`Ed25519 public key must be 32 bytes, got ${publicKey.length}`)
  }
  const prefixed = new Uint8Array(2 + 32)
  prefixed[0] = 0xed
  prefixed[1] = 0x01
  prefixed.set(publicKey, 2)
  return `did:key:z${bs58.encode(prefixed)}`
}

export function didKeyToPublicKey(did: string): Uint8Array {
  if (!did.startsWith('did:key:z')) throw new Error(`Not a did:key: ${did}`)
  const decoded = bs58.decode(did.slice('did:key:z'.length))
  if (decoded.length !== 34 || decoded[0] !== 0xed || decoded[1] !== 0x01) {
    throw new Error(`Not an Ed25519 did:key: ${did}`)
  }
  return decoded.slice(2)
}

export function signEd25519(payload: string, privateKey: Uint8Array): Uint8Array {
  return ed25519.sign(new TextEncoder().encode(payload), privateKey)
}

export function verifyEd25519(signature: Uint8Array, payload: string, publicKey: Uint8Array): boolean {
  return ed25519.verify(signature, new TextEncoder().encode(payload), publicKey)
}

// ----- base64url (RFC 4648 §5, no padding) -----

/**
 * React Native lacks `btoa`/`atob` on older RNs. The new RN runtime (0.74+) ships
 * them, but using Buffer-style chunked encoding here keeps us safe on either.
 */
export function b64uEncode(bytes: Uint8Array): string {
  // Use atob/btoa if available, otherwise manual.
  let binary = ''
  const CHUNK = 0x8000
  for (let i = 0; i < bytes.length; i += CHUNK) {
    binary += String.fromCharCode.apply(null, Array.from(bytes.subarray(i, i + CHUNK)))
  }
  const b64 = typeof btoa === 'function' ? btoa(binary) : nodeBtoa(binary)
  return b64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export function b64uDecode(s: string): Uint8Array {
  const padded = s.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((s.length + 3) % 4)
  const binary = typeof atob === 'function' ? atob(padded) : nodeAtob(padded)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

export function b64uEncodeString(s: string): string { return b64uEncode(new TextEncoder().encode(s)) }
export function b64uDecodeString(s: string): string { return new TextDecoder().decode(b64uDecode(s)) }

// Fallbacks for runtimes without atob/btoa. RN 0.74+ has them; older Hermes may not.
function nodeBtoa(s: string): string {
  // @ts-ignore — Buffer present at runtime on Hermes if older
  return Buffer.from(s, 'binary').toString('base64')
}
function nodeAtob(s: string): string {
  // @ts-ignore
  return Buffer.from(s, 'base64').toString('binary')
}

// ----- compact JWS -----

export function signJws(payload: Record<string, unknown>, privateKey: Uint8Array, kid: string, typ: string = 'JWT'): string {
  const header = { alg: 'EdDSA', typ, kid }
  const encodedHeader = b64uEncodeString(JSON.stringify(header))
  const encodedPayload = b64uEncodeString(JSON.stringify(payload))
  const signingInput = `${encodedHeader}.${encodedPayload}`
  const signature = signEd25519(signingInput, privateKey)
  return `${signingInput}.${b64uEncode(signature)}`
}

export function verifyJws(jws: string, publicKey: Uint8Array): Record<string, unknown> {
  const parts = jws.split('.')
  if (parts.length !== 3) throw new Error('JWS must have three parts')
  const signingInput = `${parts[0]}.${parts[1]}`
  const signature = b64uDecode(parts[2])
  if (!verifyEd25519(signature, signingInput, publicKey)) {
    throw new Error('JWS signature verification failed')
  }
  return JSON.parse(b64uDecodeString(parts[1]))
}
