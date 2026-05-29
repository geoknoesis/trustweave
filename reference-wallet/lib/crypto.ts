/**
 * Crypto helpers for the reference wallet.
 *
 * Wraps @noble/ed25519 for keygen and signing, with did:key encoding and resolution.
 * did:key spec: https://w3c-ccg.github.io/did-method-key/
 * Ed25519 multicodec prefix: 0xED 0x01 (varint), then 32-byte raw pubkey, then base58btc.
 */
import * as ed25519 from '@noble/ed25519'
import { sha512 } from '@noble/hashes/sha512'
import bs58 from 'bs58'

// @noble/ed25519 v2.x requires a SHA-512 implementation to be wired up explicitly.
ed25519.etc.sha512Sync = (...m) => sha512(ed25519.etc.concatBytes(...m))

/** Raw 32-byte Ed25519 key pair. */
export interface Ed25519KeyPair {
  publicKey: Uint8Array
  privateKey: Uint8Array
}

/** Generate a new Ed25519 key pair using a cryptographically secure RNG. */
export function generateEd25519KeyPair(): Ed25519KeyPair {
  const privateKey = ed25519.utils.randomPrivateKey()
  const publicKey = ed25519.getPublicKey(privateKey)
  return { privateKey, publicKey }
}

/**
 * Encode a raw Ed25519 public key as a did:key per W3C DID method-key spec.
 * Format: did:key:z<base58btc(0xED 0x01 || pubkey)>
 */
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

/**
 * Resolve a did:key back to its raw Ed25519 public key.
 * Throws if the DID is not a did:key with the Ed25519 multicodec prefix.
 */
export function didKeyToPublicKey(did: string): Uint8Array {
  if (!did.startsWith('did:key:z')) {
    throw new Error(`Not a did:key: ${did}`)
  }
  const multibase = did.slice('did:key:z'.length)
  const decoded = bs58.decode(multibase)
  if (decoded.length !== 34 || decoded[0] !== 0xed || decoded[1] !== 0x01) {
    throw new Error(`Not an Ed25519 did:key: ${did}`)
  }
  return decoded.slice(2)
}

/** Sign a string payload with an Ed25519 private key, returning base64url-encoded signature. */
export function signEd25519(payload: string, privateKey: Uint8Array): Uint8Array {
  return ed25519.sign(new TextEncoder().encode(payload), privateKey)
}

/** Verify an Ed25519 signature over a string payload. */
export function verifyEd25519(
  signature: Uint8Array,
  payload: string,
  publicKey: Uint8Array
): boolean {
  return ed25519.verify(signature, new TextEncoder().encode(payload), publicKey)
}

/** Base64url-encode a Uint8Array (RFC 4648 §5, no padding). */
export function b64uEncode(bytes: Uint8Array): string {
  let binary = ''
  for (const b of bytes) binary += String.fromCharCode(b)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** Base64url-decode to a Uint8Array. */
export function b64uDecode(s: string): Uint8Array {
  const padded = s.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((s.length + 3) % 4)
  const binary = atob(padded)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}

/** Encode a UTF-8 string as base64url. */
export function b64uEncodeString(s: string): string {
  return b64uEncode(new TextEncoder().encode(s))
}

/** Decode a base64url string as UTF-8. */
export function b64uDecodeString(s: string): string {
  return new TextDecoder().decode(b64uDecode(s))
}

/**
 * Build a compact JWS (Ed25519, alg=EdDSA) over a JSON payload.
 *
 * Returns the three-part `header.payload.signature` string. Used for both VC-JWTs
 * (issued by demo issuer) and VP-JWTs (presented by holder wallet).
 */
export function signJws(
  payload: Record<string, unknown>,
  privateKey: Uint8Array,
  kid: string,
): string {
  const header = { alg: 'EdDSA', typ: 'JWT', kid }
  const encodedHeader = b64uEncodeString(JSON.stringify(header))
  const encodedPayload = b64uEncodeString(JSON.stringify(payload))
  const signingInput = `${encodedHeader}.${encodedPayload}`
  const signature = signEd25519(signingInput, privateKey)
  return `${signingInput}.${b64uEncode(signature)}`
}

/**
 * Verify a compact JWS, returning the parsed payload on success.
 *
 * Throws if the signature is invalid or the JWS is malformed. Caller is responsible
 * for resolving the public key from the JWS `kid` (typically a DID URL).
 */
export function verifyJws(
  jws: string,
  publicKey: Uint8Array,
): Record<string, unknown> {
  const parts = jws.split('.')
  if (parts.length !== 3) {
    throw new Error('JWS must have three parts')
  }
  const [encodedHeader, encodedPayload, encodedSignature] = parts
  const signingInput = `${encodedHeader}.${encodedPayload}`
  const signature = b64uDecode(encodedSignature)
  if (!verifyEd25519(signature, signingInput, publicKey)) {
    throw new Error('JWS signature verification failed')
  }
  return JSON.parse(b64uDecodeString(encodedPayload))
}

/** Parse a JWS header (without verifying). Used to extract `kid` for key resolution. */
export function parseJwsHeader(jws: string): Record<string, unknown> {
  const parts = jws.split('.')
  if (parts.length !== 3) throw new Error('JWS must have three parts')
  return JSON.parse(b64uDecodeString(parts[0]))
}
