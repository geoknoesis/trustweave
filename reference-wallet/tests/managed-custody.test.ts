import { afterEach, describe, expect, it, vi } from 'vitest'
import { b64uEncode, b64uEncodeString, generateEd25519KeyPair, signEd25519 } from '../lib/crypto'
import { signWithManagedKey, type ManagedIdentity } from '../lib/custody/managed'
import { signCustodyPayload } from '../lib/custody'

const pair = generateEd25519KeyPair()
const identity: ManagedIdentity = { profile: 'managed-kms', endpoint: 'https://custody.example/sign', keyId: 'holder-1', algorithm: 'EdDSA', publicKey: b64uEncode(pair.publicKey) }
const payload = { nonce: 'issued-once', aud: 'verifier', claim: 'value' }
function reply(encodedPayload: string, key = pair.privateKey) {
  const input = `${b64uEncodeString(JSON.stringify({ alg: 'EdDSA', kid: identity.keyId }))}.${encodedPayload}`
  return new Response(JSON.stringify({ jws: `${input}.${b64uEncode(signEd25519(input, key))}` }))
}
afterEach(() => vi.unstubAllGlobals())
describe('managed custody request boundary', () => {
  it('verifies ES256 output against the enrolled public key', async () => {
    const key = await crypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify'])
    const enrolled: ManagedIdentity = { ...identity, algorithm: 'ES256', publicKey: b64uEncode(new Uint8Array(await crypto.subtle.exportKey('spki', key.publicKey))) }
    vi.stubGlobal('fetch', async (_url: unknown, request: RequestInit) => {
      const input = `${b64uEncodeString(JSON.stringify({ alg: 'ES256', kid: identity.keyId }))}.${JSON.parse(request.body as string).encodedPayload}`
      const signature = await crypto.subtle.sign({ name: 'ECDSA', hash: 'SHA-256' }, key.privateKey, new TextEncoder().encode(input))
      return new Response(JSON.stringify({ jws: `${input}.${b64uEncode(new Uint8Array(signature))}` }))
    })
    expect(await signWithManagedKey(enrolled, payload, 'token')).toContain('.')
  })
  it('bounds streamed responses even without a Content-Length header', async () => {
    vi.stubGlobal('fetch', async () => new Response('x'.repeat(1_500_001)))
    await expect(signWithManagedKey(identity, payload, 'token')).rejects.toThrow('too large')
  })
  it('verifies the exact requested payload and pinned public key', async () => {
    const mock = vi.fn(async (_url: unknown, request: RequestInit) => {
      expect(request.redirect).toBe('error')
      expect(request.credentials).toBe('omit')
      expect((request.headers as Record<string,string>).Authorization).toBe('Bearer session-token')
      return reply(JSON.parse(request.body as string).encodedPayload)
    })
    vi.stubGlobal('fetch', mock)
    expect((await signCustodyPayload(identity, payload, 'session-token')).profile).toBe('managed-kms')
    expect(mock).toHaveBeenCalledTimes(1)
  })
  it('rejects altered payloads and signatures from a different key', async () => {
    vi.stubGlobal('fetch', async () => reply(b64uEncodeString(JSON.stringify({ ...payload, claim: 'changed' }))))
    await expect(signWithManagedKey(identity, payload, 'token')).rejects.toThrow('changed')
    vi.stubGlobal('fetch', async () => reply(b64uEncodeString(JSON.stringify(payload)), generateEd25519KeyPair().privateKey))
    await expect(signWithManagedKey(identity, payload, 'token')).rejects.toThrow('Invalid custody')
  })
  it('fails on authorization denial and network failure without retry or fallback', async () => {
    const mock = vi.fn(async () => new Response('', { status: 403 }))
    vi.stubGlobal('fetch', mock)
    await expect(signCustodyPayload(identity, payload, 'token')).rejects.toThrow('403')
    expect(mock).toHaveBeenCalledTimes(1)
    vi.stubGlobal('fetch', async () => { throw new Error('offline') })
    await expect(signCustodyPayload(identity, payload, 'token')).rejects.toThrow('offline')
  })
  it('rejects unsafe endpoint, missing authorization and unsupported profiles before network use', async () => {
    const mock = vi.fn(); vi.stubGlobal('fetch', mock)
    await expect(signWithManagedKey({ ...identity, endpoint: 'http://custody.example/sign' }, payload, 'token')).rejects.toThrow()
    await expect(signCustodyPayload(identity, payload)).rejects.toThrow()
    await expect(signCustodyPayload({ profile: 'unknown' } as never, payload)).rejects.toThrow('Unsupported')
    expect(mock).not.toHaveBeenCalled()
  })
})
