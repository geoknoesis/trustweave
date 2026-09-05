/**
 * JWE-like claim decryption for sensitive SD-JWT disclosures (ID photos).
 * Mirrors trustweave-saas/frontend/src/utils/claimJwe.ts.
 */
import { holderSharedSecret } from './key-store'
import { sha256 } from '@noble/hashes/sha256'
import { hkdf } from '@noble/hashes/hkdf'
import { b64uDecode, b64uEncodeString } from './crypto'

export type ClaimJwePayload = {
  alg: 'ECDH-ES+A256GCM' | 'X25519-HKDF-SHA256-A256GCM'
  epk: string
  iv: string
  ciphertext: string
  tag: string
  encrypted_key: string
  key_iv: string
  key_tag: string
}

export function isClaimJwePayload(value: unknown): value is ClaimJwePayload {
  if (!value || typeof value !== 'object') return false
  const o = value as Record<string, unknown>
  return (o.alg === 'ECDH-ES+A256GCM' || o.alg === 'X25519-HKDF-SHA256-A256GCM')
    && typeof o.epk === 'string'
    && typeof o.iv === 'string'
    && typeof o.ciphertext === 'string'
    && typeof o.tag === 'string'
    && typeof o.encrypted_key === 'string'
    && typeof o.key_iv === 'string'
    && typeof o.key_tag === 'string'
}

function hkdfSha256(shared: Uint8Array, info: string, length = 32): Uint8Array {
  const infoBytes = new TextEncoder().encode(info)
  const input = new Uint8Array(shared.length + infoBytes.length)
  input.set(shared)
  input.set(infoBytes, shared.length)
  return sha256(input).slice(0, length)
}

async function importAesKey(raw: Uint8Array): Promise<CryptoKey> {
  return crypto.subtle.importKey('raw', raw, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt'])
}

async function aesGcmDecrypt(ciphertext: Uint8Array, tag: Uint8Array, key: Uint8Array, iv: Uint8Array): Promise<Uint8Array> {
  const cryptoKey = await importAesKey(key)
  const combined = new Uint8Array(ciphertext.length + tag.length)
  combined.set(ciphertext)
  combined.set(tag, ciphertext.length)
  return new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, cryptoKey, combined))
}

/** Decrypt using the device-bound agreement key; no raw holder key leaves custody. */
export async function decryptClaimJwe(jwe: ClaimJwePayload, holderDid: string): Promise<string> {
  const cek = await unwrapClaimKey(jwe, holderDid)
  try {
  const plain = await aesGcmDecrypt(
    b64uDecode(jwe.ciphertext),
    b64uDecode(jwe.tag),
    cek,
    b64uDecode(jwe.iv),
  )
  return new TextDecoder().decode(plain)
  } finally { cek.fill(0) }
}

export { b64uEncodeString }

/** The content key may be disclosed only with an explicitly selected issuer-bound encrypted claim. */
export async function unwrapClaimKey(jwe: ClaimJwePayload, holderDid: string): Promise<Uint8Array> {
  const shared = await holderSharedSecret(holderDid, jwe.epk)
  const info = 'TrustWeave-ClaimJWE-v1-wrap'
  const wrapKey = jwe.alg === 'X25519-HKDF-SHA256-A256GCM'
    ? hkdf(sha256, shared, new Uint8Array(0), new TextEncoder().encode(info), 32)
    : hkdfSha256(shared, info) // Read-only compatibility for the legacy demo envelope.
  try {
    const cek = await aesGcmDecrypt(b64uDecode(jwe.encrypted_key), b64uDecode(jwe.key_tag), wrapKey, b64uDecode(jwe.key_iv))
    if (cek.length !== 32) throw new Error('Invalid encrypted claim key')
    return cek
  } finally { shared.fill(0); wrapKey.fill(0) }
}
