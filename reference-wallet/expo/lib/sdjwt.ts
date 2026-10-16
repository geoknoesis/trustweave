/**
 * SD-JWT VC helpers — straight port of `reference-wallet/lib/sdjwt.ts` (web).
 * No RN-specific changes; the only runtime dep is the @noble/hashes sha256 which
 * works in Hermes/JavaScriptCore identically to V8.
 */
import { sha256 } from '@noble/hashes/sha256'
import {
  b64uEncode,
  b64uEncodeString,
  b64uDecodeString,
  signEd25519,
  signJws,
} from './crypto'
import * as ExpoCrypto from 'expo-crypto'

function sha256OfString(s: string): Uint8Array {
  return sha256(new TextEncoder().encode(s))
}

export function disclosureHash(disclosure: string): string {
  return b64uEncode(sha256OfString(disclosure))
}

function randomSalt(): string {
  // expo-crypto gives platform-native secure random on iOS/Android; this is the
  // canonical way to get cryptographic randomness in Expo without polyfills.
  return b64uEncode(ExpoCrypto.getRandomBytes(16))
}

export function createObjectDisclosure(
  name: string,
  value: unknown,
): { disclosure: string; hash: string; salt: string } {
  const salt = randomSalt()
  const disclosure = b64uEncodeString(JSON.stringify([salt, name, value]))
  return { disclosure, hash: disclosureHash(disclosure), salt }
}

export function parseDisclosure(d: string): [string, string, unknown] {
  const arr = JSON.parse(b64uDecodeString(d)) as unknown[]
  if (arr.length !== 3) throw new Error('Object disclosure must be [salt, name, value]')
  return [String(arr[0]), String(arr[1]), arr[2]]
}

export interface SelectivelyDisclosableClaim { name: string; value: unknown }

/** Holder presents an SD-JWT VC: chosen disclosures + KB-JWT. */
export function presentSdJwtVc(args: {
  sdJwtVc: string
  selectDisclose: string[]
  holderPrivateKey: Uint8Array
  holderDid: string
  audience: string
  nonce: string
  now: number
}): string {
  const parts = args.sdJwtVc.split('~').filter((p) => p.length > 0)
  if (parts.length < 1) throw new Error('Empty SD-JWT VC')
  const issuerJwt = parts[0]
  const allDisclosures = parts.slice(1)

  const selected: string[] = []
  for (const d of allDisclosures) {
    const [, name] = parseDisclosure(d)
    if (args.selectDisclose.includes(name)) selected.push(d)
  }
  const prefix = [issuerJwt, ...selected, ''].join('~')
  const sdHash = b64uEncode(sha256OfString(prefix))

  const kbPayload = { iat: args.now, aud: args.audience, nonce: args.nonce, sd_hash: sdHash }
  const kbJwt = signJws(kbPayload, args.holderPrivateKey, args.holderDid, 'kb+jwt')
  return prefix + kbJwt
}

export interface DecodedSdJwtVc {
  issuerJwt: string
  issuerPayload: Record<string, unknown>
  disclosures: Array<{ raw: string; name: string; value: unknown; hash: string }>
  kbJwt?: string
}

export function decodeSdJwtVc(sdJwtVc: string): DecodedSdJwtVc {
  const parts = sdJwtVc.split('~')
  if (parts.length < 1) throw new Error('Empty SD-JWT VC')
  const issuerJwt = parts[0]
  let kbJwt: string | undefined
  let lastIdx = parts.length - 1
  if (parts[lastIdx] !== '') {
    kbJwt = parts[lastIdx]
    lastIdx -= 1
  }
  const disclosures = parts.slice(1, lastIdx + 1).filter((p) => p.length > 0).map((raw) => {
    const [, name, value] = parseDisclosure(raw)
    return { raw, name, value, hash: disclosureHash(raw) }
  })

  const jwtParts = issuerJwt.split('.')
  if (jwtParts.length !== 3) throw new Error('Issuer JWT must have three parts')
  const issuerPayload = JSON.parse(b64uDecodeString(jwtParts[1])) as Record<string, unknown>
  return { issuerJwt, issuerPayload, disclosures, kbJwt }
}

// Silence unused-import lint for signEd25519 (it's re-exported below for parity).
export { signEd25519 }
