import { describe, expect, it } from 'vitest'
import { decryptDisclosedClaims } from '../lib/claim-jwe'
import { b64uEncode } from '../lib/crypto'

async function fixture() {
  const key = crypto.getRandomValues(new Uint8Array(32))
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const aes = await crypto.subtle.importKey('raw', key, 'AES-GCM', false, ['encrypt'])
  const sealed = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, aes, new TextEncoder().encode('private photo')))
  const value = { alg: 'X25519-HKDF-SHA256-A256GCM', epk: '', encrypted_key: '', key_iv: '', key_tag: '', iv: b64uEncode(iv), ciphertext: b64uEncode(sealed.slice(0, -16)), tag: b64uEncode(sealed.slice(-16)) }
  return { disclosures: [{ raw: 'issuer-bound', hash: 'disclosure-hash', name: 'photo', value }], payload: { trustweave_claim_keys: { 'disclosure-hash': b64uEncode(key) } } }
}

describe('verified encrypted disclosures', () => {
  it('decrypts only the explicitly disclosed claim using its holder-bound content key', async () => {
    const f = await fixture()
    expect(await decryptDisclosedClaims(f.disclosures, f.payload)).toEqual({ photo: 'private photo' })
  })
  it('rejects a missing key, an unrelated key, and modified ciphertext', async () => {
    const f = await fixture()
    await expect(decryptDisclosedClaims(f.disclosures, {})).rejects.toThrow('Missing')
    await expect(decryptDisclosedClaims(f.disclosures, { trustweave_claim_keys: { other: 'key' } })).rejects.toThrow('undisclosed')
    f.disclosures[0].value.ciphertext = b64uEncode(new Uint8Array(13))
    await expect(decryptDisclosedClaims(f.disclosures, f.payload)).rejects.toThrow()
  })
  it('rejects duplicate names and does not treat claim names as object prototype setters', async () => {
    const d = { raw: '', hash: 'hash', name: '__proto__', value: { safe: true } }
    const result = await decryptDisclosedClaims([d], {})
    expect(Object.getPrototypeOf(result)).toBeNull()
    expect(Object.hasOwn(result, '__proto__')).toBe(true)
    await expect(decryptDisclosedClaims([d, d], {})).rejects.toThrow('Duplicate')
  })
})
