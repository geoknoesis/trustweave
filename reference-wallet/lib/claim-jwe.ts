/**
 * JWE-like claim decryption for sensitive SD-JWT disclosures (ID photos).
 * Mirrors trustweave-saas/frontend/src/utils/claimJwe.ts.
 */
import { edwardsToMontgomeryPriv, x25519 } from '@noble/curves/ed25519'
import { sha256 } from '@noble/hashes/sha256'
import { b64uDecode, b64uEncodeString } from './crypto'
import { createObjectDisclosure } from './sdjwt'

export type ClaimJwePayload = {
  alg: 'ECDH-ES+A256GCM'
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
  return o.alg === 'ECDH-ES+A256GCM'
    && typeof o.epk === 'string'
    && typeof o.iv === 'string'
    && typeof o.ciphertext === 'string'
    && typeof o.tag === 'string'
    && typeof o.encrypted_key === 'string'
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

/** Decrypt a claim JWE payload using the holder's Ed25519 private key (32 bytes). */
export async function decryptClaimJwe(jwe: ClaimJwePayload, holderEd25519PrivateKey: Uint8Array): Promise<string> {
  const holderXPriv = edwardsToMontgomeryPriv(holderEd25519PrivateKey)
  const ephemeralPub = b64uDecode(jwe.epk)
  const shared = x25519.getSharedSecret(holderXPriv, ephemeralPub)
  const wrapKey = hkdfSha256(shared, 'TrustWeave-ClaimJWE-v1-wrap')
  const cek = await aesGcmDecrypt(
    b64uDecode(jwe.encrypted_key),
    b64uDecode(jwe.key_tag),
    wrapKey,
    b64uDecode(jwe.key_iv),
  )
  const plain = await aesGcmDecrypt(
    b64uDecode(jwe.ciphertext),
    b64uDecode(jwe.tag),
    cek,
    b64uDecode(jwe.iv),
  )
  return new TextDecoder().decode(plain)
}

/** Build a presentation disclosure with decrypted plaintext (Option A). */
export function buildPlaintextDisclosure(claimName: string, plaintext: string): string {
  return createObjectDisclosure(claimName, plaintext).disclosure
}

export { b64uEncodeString }
