import { describe, expect, it, vi } from 'vitest'
import { p256 } from '@noble/curves/p256'
import { b64uEncode, b64uEncodeString } from '../lib/crypto'
import { verifyPasskeyProof, type PasskeyIdentity } from '../lib/custody/passkey'
import { verifyPasskeyPayload } from '../lib/custody'

const hash = async (bytes: Uint8Array) => new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
async function fixture(flags = 5, origin = 'https://wallet.example') {
  const pair = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])
  const identity: PasskeyIdentity = { profile: 'passkey', credentialId: 'credential', rpId: 'wallet.example', origin: 'https://wallet.example', publicKeySpki: b64uEncode(new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey))) }
  const payload = JSON.stringify({ nonce: 'one-use', aud: 'verifier', claim: 'sensitive' })
  const challenge = await hash(new TextEncoder().encode(payload))
  const clientDataJSON = b64uEncodeString(JSON.stringify({ type: 'webauthn.get', origin, challenge: b64uEncode(challenge), crossOrigin: false }))
  const auth = new Uint8Array(37); auth.set(await hash(new TextEncoder().encode(identity.rpId))); auth[32] = flags; auth[36] = 1
  const clientRaw = new TextEncoder().encode(JSON.stringify({ type: 'webauthn.get', origin, challenge: b64uEncode(challenge), crossOrigin: false }))
  const input = new Uint8Array(69); input.set(auth); input.set(await hash(clientRaw), 37)
  const raw = new Uint8Array(await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, pair.privateKey, input))
  const proof = { credentialId: identity.credentialId, clientDataJSON, authenticatorData: b64uEncode(auth), signature: b64uEncode(p256.Signature.fromCompact(raw).toDERRawBytes()) }
  return { identity, challenge, proof, envelope: { profile: 'passkey' as const, payload, proof } }
}
describe('passkey verifier policy', () => {
  it.each([0, 1, 4, 13, 29])('rejects signed flags %i without UV/UP or with backup eligibility', async flags => {
    const f = await fixture(flags)
    await expect(verifyPasskeyProof(f.identity, f.proof, f.challenge)).rejects.toThrow()
  })
  it('rejects a signature from the wrong origin even when the key is correct', async () => {
    const f = await fixture(5, 'https://attacker.example')
    await expect(verifyPasskeyProof(f.identity, f.proof, f.challenge)).rejects.toThrow('client binding')
  })
  it('consumes only a valid audience-bound proof and rejects replay', async () => {
    const f = await fixture()
    const expected = { nonce: 'one-use', audience: 'verifier', expiresAt: Date.now() + 60_000 }
    let consumed = false
    const consume = vi.fn(async () => { if (consumed) return false; consumed = true; return true })
    await expect(verifyPasskeyPayload(f.identity, f.envelope, { ...expected, audience: 'wrong' }, consume)).rejects.toThrow('audience')
    expect(consume).not.toHaveBeenCalled()
    expect((await verifyPasskeyPayload(f.identity, f.envelope, expected, consume)).claim).toBe('sensitive')
    await expect(verifyPasskeyPayload(f.identity, f.envelope, expected, consume)).rejects.toThrow('already consumed')
    expect(consume).toHaveBeenCalledWith('one-use', 'credential', 1)
    await expect(verifyPasskeyPayload(f.identity, f.envelope, { ...expected, expiresAt: 0 }, consume)).rejects.toThrow('expired')
  })
})
