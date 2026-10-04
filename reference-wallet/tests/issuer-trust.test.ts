import { beforeEach, describe, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import 'fake-indexeddb/auto'
import { generateEd25519KeyPair, publicKeyToDidKey, signJws } from '../lib/crypto'
import { clearHolderKeys } from '../lib/key-store'
import {
  MAX_ACCEPTED_ISSUERS, addAcceptedIssuer, loadAcceptedIssuers, loadCredentials, removeAcceptedIssuer,
} from '../lib/storage'
import { BACKEND_IDENTITY_PATH, loadBackendIssuers, resetBackendIssuersCache, reviewableIssuers } from '../lib/issuer-trust'
import { IssuerTrustPolicy, UntrustedIssuerError, verifyImportedCredential } from '../lib/credential-verification'
import { bootstrap, createPresentation, removeIssuer, restoreCredentials, store } from '../lib/wallet'

class MemoryStorage {
  values = new Map<string, string>()
  getItem(key: string) { return this.values.get(key) ?? null }
  setItem(key: string, value: string) { this.values.set(key, value) }
  removeItem(key: string) { this.values.delete(key) }
}
let storage: MemoryStorage
beforeEach(async () => {
  vi.stubGlobal('crypto', webcrypto)
  storage = new MemoryStorage()
  vi.stubGlobal('window', { localStorage: storage })
  let pending = Promise.resolve()
  vi.stubGlobal('navigator', { locks: { request: (_name: string, operation: () => Promise<unknown>) => {
    const result = pending.then(operation); pending = result.then(() => undefined, () => undefined); return result
  } } })
  vi.unstubAllEnvs()
  resetBackendIssuersCache()
  await clearHolderKeys()
})

const mint = (holderDid: string, extra: Record<string, unknown> = {}) => {
  const key = generateEd25519KeyPair()
  const issuerDid = publicKeyToDidKey(key.publicKey)
  const credential = signJws({ iss: issuerDid, sub: holderDid, vc: { type: ['VerifiableCredential', 'Employee'], credentialSubject: { id: holderDid } }, ...extra }, key.privateKey, issuerDid)
  return { credential, issuerDid }
}

describe('issuer trust is never established by the offer', () => {
  it('rejects a valid credential from an unknown issuer with UntrustedIssuerError and persists nothing', async () => {
    const holder = (await bootstrap()).holder
    const { credential, issuerDid } = mint(holder.did)
    const failure = await store(credential, 'vc+jwt').catch(e => e)
    expect(failure).toBeInstanceOf(UntrustedIssuerError)
    expect(failure.issuerDid).toBe(issuerDid)
    expect(loadCredentials()).toEqual([])
    expect(loadAcceptedIssuers()).toEqual([])
  })

  it('refuses a legacy string in the options slot instead of trusting the offer-named issuer', async () => {
    const holder = (await bootstrap()).holder
    const { credential, issuerDid } = mint(holder.did)
    await expect(store(credential, 'vc+jwt', [], issuerDid as never)).rejects.toThrow('no longer trusted')
    expect(loadCredentials()).toEqual([])
  })

  it('persists the issuer only after confirmation AND a fully valid credential', async () => {
    const holder = (await bootstrap()).holder
    const { credential, issuerDid } = mint(holder.did)
    await store(credential, 'vc+jwt', [], { confirmedIssuer: issuerDid })
    expect(loadAcceptedIssuers()).toEqual([issuerDid])
    // the next import from that issuer needs no confirmation
    await store(mint(holder.did).credential, 'vc+jwt').catch(() => undefined)
    const again = signJws({ iss: issuerDid, sub: holder.did, vc: { type: ['VerifiableCredential', 'Degree'], credentialSubject: { id: holder.did } } }, generateEd25519KeyPair().privateKey, issuerDid)
    await expect(store(again, 'vc+jwt')).rejects.not.toBeInstanceOf(UntrustedIssuerError) // fails on signature, not trust
  })

  it('does not persist a confirmed issuer when the credential is bound to another holder', async () => {
    await bootstrap()
    const { credential, issuerDid } = mint('did:key:someoneElse')
    await expect(store(credential, 'vc+jwt', [], { confirmedIssuer: issuerDid })).rejects.toThrow('bound to')
    expect(loadAcceptedIssuers()).toEqual([])
    expect(loadCredentials()).toEqual([])
  })

  it('confirming issuer A does not trust issuer B', async () => {
    const holder = (await bootstrap()).holder
    const a = mint(holder.did), b = mint(holder.did)
    await expect(store(b.credential, 'vc+jwt', [], { confirmedIssuer: a.issuerDid })).rejects.toBeInstanceOf(UntrustedIssuerError)
    expect(loadAcceptedIssuers()).toEqual([])
  })

  it('trusts the configured allow-list without persisting it', async () => {
    const holder = (await bootstrap()).holder
    const { credential, issuerDid } = mint(holder.did)
    vi.stubEnv('NEXT_PUBLIC_TRUSTED_ISSUERS', `did:key:other, ${issuerDid}`)
    await store(credential, 'vc+jwt')
    expect(loadAcceptedIssuers()).toEqual([])
  })

  it('trusts the backend identity read from the same-origin endpoint, never from the offer', async () => {
    const holder = (await bootstrap()).holder
    const { credential, issuerDid } = mint(holder.did)
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({ issuerDids: [issuerDid] })))
    vi.stubGlobal('fetch', fetchMock)
    await store(credential, 'vc+jwt')
    expect(fetchMock).toHaveBeenCalledWith(BACKEND_IDENTITY_PATH, expect.objectContaining({ credentials: 'omit' }))
    expect(loadAcceptedIssuers()).toEqual([])
  })

  it.each([
    ['an HTTP error', async () => new Response('nope', { status: 500 })],
    ['a network failure', async () => { throw new TypeError('Failed to fetch') }],
    ['a non-JSON body', async () => new Response('<html>')],
    ['a wrong shape', async () => new Response(JSON.stringify({ issuerDids: 'did:key:x' }))],
    ['implausible DIDs', async () => new Response(JSON.stringify({ issuerDids: ['not a did'] }))],
  ])('fails closed (no backend issuers) on %s and retries later', async (_label, impl) => {
    vi.stubGlobal('fetch', vi.fn(impl))
    expect(await loadBackendIssuers()).toEqual([])
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ issuerDids: ['did:key:z6MkGood'] }))))
    expect(await loadBackendIssuers()).toEqual(['did:key:z6MkGood'])
  })
})

describe('restore and presentation use the same trust sources', () => {
  const backup = (did: string, credentials: unknown[]) => JSON.stringify({ version: '2', holder: { did }, credentials: JSON.stringify(credentials) })

  it('does not trust an issuer because it signed a credential already stored', async () => {
    const holder = (await bootstrap()).holder
    const { credential, issuerDid } = mint(holder.did)
    await store(credential, 'vc+jwt', [], { confirmedIssuer: issuerDid })
    await removeIssuer(issuerDid)
    await expect(createPresentation([loadCredentials()[0].id], 'verifier', 'n')).rejects.toBeInstanceOf(UntrustedIssuerError)
    storage.setItem('trustweave-wallet-credentials', '[]')
    await expect(restoreCredentials(backup(holder.did, [{ credential, format: 'vc+jwt' }]))).rejects.toBeInstanceOf(UntrustedIssuerError)
  })

  it('restore with a confirmed issuer persists it only after the write succeeded', async () => {
    const holder = (await bootstrap()).holder
    const { credential, issuerDid } = mint(holder.did)
    await expect(restoreCredentials(backup(holder.did, [{ credential, format: 'vc+jwt' }]))).rejects.toBeInstanceOf(UntrustedIssuerError)
    expect(loadAcceptedIssuers()).toEqual([])
    expect(await restoreCredentials(backup(holder.did, [{ credential, format: 'vc+jwt' }]), { confirmedIssuers: [issuerDid] })).toEqual({ added: 1, skipped: 0 })
    expect(loadAcceptedIssuers()).toEqual([issuerDid])
  })

  it('rejects an invalid confirmed issuer identifier', async () => {
    const holder = (await bootstrap()).holder
    await expect(restoreCredentials(backup(holder.did, []), { confirmedIssuers: ['<script>'] })).rejects.toThrow('valid issuer')
  })
})

describe('accepted-issuer list integrity limits', () => {
  it('drops malformed, oversized and duplicate entries on read', () => {
    storage.setItem('trustweave-wallet-accepted-issuers', JSON.stringify(['did:key:z6MkA', 'did:key:z6MkA', 5, '<script>alert(1)</script>', `did:key:${'a'.repeat(300)}`, 'did:web:example.com']))
    expect(loadAcceptedIssuers()).toEqual(['did:key:z6MkA', 'did:web:example.com'])
  })
  it('caps the list size on read and write', () => {
    storage.setItem('trustweave-wallet-accepted-issuers', JSON.stringify(Array.from({ length: 5000 }, (_, i) => `did:key:z6Mk${i}`)))
    expect(loadAcceptedIssuers()).toHaveLength(MAX_ACCEPTED_ISSUERS)
    expect(() => addAcceptedIssuer('did:key:z6MkNew')).toThrow('At most')
  })
  it('refuses to add a non-DID and treats non-array JSON as empty', () => {
    expect(() => addAcceptedIssuer('javascript:alert(1)')).toThrow('valid issuer')
    storage.setItem('trustweave-wallet-accepted-issuers', '{"a":1}')
    expect(loadAcceptedIssuers()).toEqual([])
  })
  it('lists configured entries as read-only and accepted ones as removable', () => {
    vi.stubEnv('NEXT_PUBLIC_TRUSTED_ISSUERS', 'did:key:z6MkConfigured')
    addAcceptedIssuer('did:key:z6MkAccepted'); addAcceptedIssuer('did:key:z6MkConfigured')
    expect(reviewableIssuers()).toEqual([{ did: 'did:key:z6MkConfigured', source: 'configured' }, { did: 'did:key:z6MkAccepted', source: 'accepted' }])
    removeAcceptedIssuer('did:key:z6MkAccepted')
    expect(loadAcceptedIssuers()).toEqual(['did:key:z6MkConfigured'])
  })
})

describe('holder binding and time claims', () => {
  const key = generateEd25519KeyPair(), did = publicKeyToDidKey(key.publicKey)
  const sign = (claims: Record<string, unknown>) => signJws({ iss: did, sub: 'did:key:holder', vc: { type: ['T'] }, ...claims }, key.privateKey, did)
  const trusting = { issuerPolicy: IssuerTrustPolicy.allowList([did]) }
  const now = Math.floor(Date.now() / 1000)

  it('enforces holderDid when supplied (parity with Android)', () => {
    expect(() => verifyImportedCredential(sign({}), 'vc+jwt', { ...trusting, holderDid: 'did:key:holder' })).not.toThrow()
    expect(() => verifyImportedCredential(sign({}), 'vc+jwt', { ...trusting, holderDid: 'did:key:other' })).toThrow('bound to did:key:holder')
  })
  it('truncates time claims to whole seconds like Android toLong()', () => {
    // 0.9 s in the future truncates to "now": accepted, where a float comparison would reject.
    expect(() => verifyImportedCredential(sign({ nbf: now + 0.9 }), 'vc+jwt', trusting)).not.toThrow()
    // exp = now + 0.5 truncates to now => expired.
    expect(() => verifyImportedCredential(sign({ exp: now + 0.5 }), 'vc+jwt', trusting)).toThrow('expired')
    // nbf 100.9 vs exp 100.2 both truncate to 100 => inconsistent interval.
    expect(() => verifyImportedCredential(sign({ nbf: now + 0.2, exp: now + 3600.5, iat: now + 3600.9 }), 'vc+jwt', trusting)).toThrow()
  })
  it('does not trust the signer when no policy is supplied', () => {
    expect(() => verifyImportedCredential(sign({}), 'vc+jwt')).toThrow(UntrustedIssuerError)
  })
})
