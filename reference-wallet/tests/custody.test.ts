import { beforeEach, describe, expect, it, vi } from 'vitest'
import { webcrypto } from 'node:crypto'
import 'fake-indexeddb/auto'
import { generateEd25519KeyPair, publicKeyToDidKey, b64uEncode, verifyJws, signJws } from '../lib/crypto'
import { clearHolderKeys, importHolderKeys, loadHolderKeys, signHolderJws } from '../lib/key-store'
import { loadCredentials, loadHolder, exportWalletData } from '../lib/storage'
import { bootstrap, store, createPresentation, restoreCredentials, canReplaceLostKey, replaceLostKey, resetWallet } from '../lib/wallet'

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
  await clearHolderKeys()
})

describe('holder custody and recovery', () => {
  it('clears local wallet records even when IndexedDB is unavailable and reports incomplete key erasure', async () => {
    await bootstrap()
    storage.setItem('trustweave-wallet-credentials', 'corrupt recovery data')
    const open = vi.spyOn(indexedDB, 'open').mockImplementation(() => { throw new Error('storage unavailable') })
    try {
      expect(await resetWallet()).toEqual({ keysCleared: false })
      expect(storage.getItem('trustweave-wallet-holder')).toBeNull()
      expect(storage.getItem('trustweave-wallet-credentials')).toBeNull()
    } finally { open.mockRestore() }
  })

  it.each([null, '1', '2'])('restores credential exports from schema %s without replacing custody', async version => {
    const holder = (await bootstrap()).holder
    const exported = JSON.parse(exportWalletData())
    exported.version = version
    expect(await restoreCredentials(JSON.stringify(exported))).toEqual({ added: 0, skipped: 0 })
    expect(loadHolder()?.did).toBe(holder.did)
  })

  async function replaceStoredKeys(did: string, keys: unknown) {
    await new Promise<void>((resolve, reject) => {
      const request = indexedDB.open('trustweave-holder-keys', 1)
      request.onsuccess = () => {
        const db = request.result
        const tx = db.transaction('keys', 'readwrite')
        tx.objectStore('keys').put(keys, did)
        tx.oncomplete = () => { db.close(); resolve() }
        tx.onabort = () => { db.close(); reject(tx.error) }
      }
      request.onerror = () => reject(request.error)
    })
  }

  it.each(['signing', 'agreement'] as const)('rejects a substituted %s key without rotating or deleting credentials', async key => {
    const holder = (await bootstrap()).holder
    const original = await loadHolderKeys(holder.did)
    const attacker = generateEd25519KeyPair()
    const attackerDid = publicKeyToDidKey(attacker.publicKey)
    await importHolderKeys(attackerDid, attacker.privateKey)
    const other = await loadHolderKeys(attackerDid)
    const before = exportWalletData()
    await replaceStoredKeys(holder.did, { ...original, [key]: other[key] })
    await expect(loadHolderKeys(holder.did)).rejects.toThrow(`Stored ${key} key does not match`)
    await expect(canReplaceLostKey()).rejects.toThrow('does not match')
    expect(exportWalletData()).toBe(before)
  })

  it('rejects malformed custody records as corruption rather than missing keys', async () => {
    const holder = (await bootstrap()).holder
    for (const record of [null, false, 0, { signing: { extractable: false }, agreement: { extractable: false } }]) {
      await replaceStoredKeys(holder.did, record)
      await expect(signHolderJws({}, holder.did)).rejects.toThrow('Unsafe key storage')
      await expect(canReplaceLostKey()).rejects.toThrow('Unsafe key storage')
    }
  })

  it('permits a migration retry but never overwrites corrupt existing keys', async () => {
    const pair = generateEd25519KeyPair()
    const seed = pair.privateKey.slice()
    const did = publicKeyToDidKey(pair.publicKey)
    await importHolderKeys(did, pair.privateKey)
    await importHolderKeys(did, seed.slice())
    await replaceStoredKeys(did, {})
    const retry = seed.slice()
    await expect(importHolderKeys(did, retry)).rejects.toThrow('Unsafe key storage')
    expect(retry.every(byte => byte === 0)).toBe(true)
    await expect(loadHolderKeys(did)).rejects.toThrow('Unsafe key storage')
    seed.fill(0)
  })

  it('clears invalid seed material even when validation fails before import', async () => {
    const seed = new Uint8Array(31).fill(7)
    await expect(importHolderKeys('did:key:invalid', seed)).rejects.toThrow()
    expect(seed.every(byte => byte === 0)).toBe(true)
  })

  it('persists non-extractable keys that still produce verifiable signatures', async () => {
    const pair = generateEd25519KeyPair()
    const did = publicKeyToDidKey(pair.publicKey)
    await importHolderKeys(did, pair.privateKey)
    expect(pair.privateKey.every(byte => byte === 0)).toBe(true)
    const keys = await loadHolderKeys(did)
    await expect(crypto.subtle.exportKey('pkcs8', keys.signing)).rejects.toThrow()
    await expect(crypto.subtle.exportKey('pkcs8', keys.agreement)).rejects.toThrow()
    const jwt = await signHolderJws({ nonce: 'fresh', aud: 'verifier' }, did)
    expect(verifyJws(jwt, pair.publicKey).nonce).toBe('fresh')
  })

  it('migrates a legacy seed without rotating identity or retaining the seed', async () => {
    const pair = generateEd25519KeyPair()
    const did = publicKeyToDidKey(pair.publicKey)
    storage.setItem('trustweave-wallet-holder', JSON.stringify({ did, publicKey: b64uEncode(pair.publicKey), privateKey: b64uEncode(pair.privateKey), createdAt: '2024-01-01' }))
    const state = await bootstrap()
    expect(state.holder.did).toBe(did)
    expect(storage.getItem('trustweave-wallet-holder')).not.toContain('privateKey')
    expect((await bootstrap()).holder.did).toBe(did)
  })

  it('migrates v1 credentials without wiping the holder', () => {
    storage.setItem('trustweave-wallet-schema-version', '1')
    storage.setItem('trustweave-wallet-holder', JSON.stringify({ did: 'did:key:original', publicKey: 'public', createdAt: '2024-01-01' }))
    storage.setItem('trustweave-wallet-credentials', JSON.stringify([{ id: 'record', vcJwt: 'a.b.c', issuerDid: 'did:key:issuer', subjectDid: 'did:key:original', receivedAt: '2024-01-01', type: ['Employee'], preview: { title: 'Employee' } }]))
    expect(loadCredentials()[0].credential).toBe('a.b.c')
    expect(loadHolder()?.did).toBe('did:key:original')
  })

  it.each(['99', 'broken'])('preserves unknown schema %s for recovery', version => {
    storage.setItem('trustweave-wallet-schema-version', version)
    storage.setItem('trustweave-wallet-credentials', '[{"id":"keep"}]')
    const original = storage.getItem('trustweave-wallet-credentials')
    expect(() => loadCredentials()).toThrow('not supported')
    expect(storage.getItem('trustweave-wallet-credentials')).toBe(original)
    expect(exportWalletData()).toContain('keep')
  })

  it('issues, imports and signs a presentation without an exportable holder key', async () => {
    const holder = (await bootstrap()).holder
    const issuer = generateEd25519KeyPair()
    const issuerDid = publicKeyToDidKey(issuer.publicKey)
    const credential = signJws({ iss: issuerDid, sub: holder.did, vc: { type: ['VerifiableCredential', 'Employee'], credentialSubject: { id: holder.did } } }, issuer.privateKey, issuerDid)
    const stored = await store(credential, 'vc+jwt')
    const presentation = await createPresentation([stored.credential.id], 'verifier', 'nonce')
    const payload = verifyJws(presentation, (await import('../lib/crypto')).b64uDecode(holder.publicKey))
    expect(payload.nonce).toBe('nonce')
    expect((payload.vp as { verifiableCredential: string[] }).verifiableCredential).toEqual([credential])
  })
  it('rejects a forged issuer signature without storing the credential', async () => {
    const holder = (await bootstrap()).holder
    const issuer = generateEd25519KeyPair()
    const attacker = generateEd25519KeyPair()
    const did = publicKeyToDidKey(issuer.publicKey)
    const forged = signJws({ iss: did, sub: holder.did, vc: { type: ['Employee'] } }, attacker.privateKey, did)
    await expect(store(forged, 'vc+jwt')).rejects.toThrow()
    expect(loadCredentials()).toEqual([])
  })

  it('keeps both concurrent imports and rejects multi SD-JWT disclosure', async () => {
    const holder = (await bootstrap()).holder
    const issuer = generateEd25519KeyPair()
    const did = publicKeyToDidKey(issuer.publicKey)
    const { issueSdJwtVc } = await import('../lib/sdjwt')
    const make = (vct: string) => issueSdJwtVc({ issuerDid: did, issuerPrivateKey: issuer.privateKey,
      issuerKid: did, holderDid: holder.did, alwaysVisible: {}, selectivelyDisclosable: [{ name: 'secret', value: vct }],
      vct, now: Math.floor(Date.now() / 1000) })
    const records = await Promise.all([store(make('Employee'), 'vc+sd-jwt'), store(make('Degree'), 'vc+sd-jwt')])
    expect(loadCredentials()).toHaveLength(2)
    await expect(createPresentation(records.map(r => r.credential.id), 'verifier', 'nonce', [])).rejects.toThrow('one at a time')
    const presentation = await createPresentation([records[0].credential.id], 'verifier', 'nonce', [])
    expect((await import('../lib/sdjwt')).decodeSdJwtVc(presentation).disclosures).toHaveLength(0)
  })

  it.each(['{}', '[null]', '[{"id":"broken"}]'])('preserves malformed stored data %s', raw => {
    storage.setItem('trustweave-wallet-schema-version', '2')
    storage.setItem('trustweave-wallet-credentials', raw)
    expect(() => loadCredentials()).toThrow()
    expect(storage.getItem('trustweave-wallet-credentials')).toBe(raw)
  })

  it('shares a selected content key while preserving issuer-committed encrypted disclosures', async () => {
    const holder = (await bootstrap()).holder
    const { x25519, edwardsToMontgomeryPub } = await import('@noble/curves/ed25519')
    const { hkdf } = await import('@noble/hashes/hkdf')
    const { sha256 } = await import('@noble/hashes/sha256')
    const { b64uDecode } = await import('../lib/crypto')
    const { issueSdJwtVc, decodeSdJwtVc, disclosureHash } = await import('../lib/sdjwt')
    const ephemeral = x25519.utils.randomPrivateKey()
    const shared = x25519.getSharedSecret(ephemeral, edwardsToMontgomeryPub(b64uDecode(holder.publicKey)))
    const wrap = hkdf(sha256, shared, new Uint8Array(), new TextEncoder().encode('TrustWeave-ClaimJWE-v1-wrap'), 32)
    const cek = crypto.getRandomValues(new Uint8Array(32))
    const encrypt = async (raw: Uint8Array, key: Uint8Array) => {
      const iv = crypto.getRandomValues(new Uint8Array(12))
      const imported = await crypto.subtle.importKey('raw', key, 'AES-GCM', false, ['encrypt'])
      const data = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, imported, raw))
      return { iv: b64uEncode(iv), ciphertext: b64uEncode(data.slice(0,-16)), tag: b64uEncode(data.slice(-16)) }
    }
    const body = await encrypt(new TextEncoder().encode('private value'), cek), key = await encrypt(cek, wrap)
    const envelope = { alg: 'X25519-HKDF-SHA256-A256GCM', epk: b64uEncode(x25519.getPublicKey(ephemeral)), ...body,
      encrypted_key: key.ciphertext, key_iv: key.iv, key_tag: key.tag }
    const issuer = generateEd25519KeyPair(), did = publicKeyToDidKey(issuer.publicKey)
    const issued = issueSdJwtVc({ issuerDid: did, issuerPrivateKey: issuer.privateKey, issuerKid: did, holderDid: holder.did,
      vct: 'EncryptedCredential', alwaysVisible: {}, selectivelyDisclosable: [{ name: 'private', value: envelope }], now: Math.floor(Date.now()/1000) })
    const record = (await store(issued, 'vc+sd-jwt')).credential
    const hidden = decodeSdJwtVc(await createPresentation([record.id], 'verifier', 'nonce', []))
    expect(hidden.disclosures).toHaveLength(0)
    expect(verifyJws(hidden.kbJwt!, b64uDecode(holder.publicKey)).trustweave_claim_keys).toBeUndefined()
    const presented = decodeSdJwtVc(await createPresentation([record.id], 'verifier', 'nonce', ['private']))
    expect(presented.disclosures[0].raw).toBe(decodeSdJwtVc(issued).disclosures[0].raw)
    const proof = verifyJws(presented.kbJwt!, b64uDecode(holder.publicKey))
    expect((proof.trustweave_claim_keys as Record<string,string>)[disclosureHash(presented.disclosures[0].raw)]).toBe(b64uEncode(cek))
    expect(verifyJws(presented.issuerJwt, issuer.publicKey)._sd).toContain(presented.disclosures[0].hash)
  })

})


describe('credential backup restoration', () => {
  const backup = (did: string, credentials: unknown[]) => JSON.stringify({ version: '2', holder: { did }, credentials: JSON.stringify(credentials) })
  const issued = (did: string, name = 'Employee') => {
    const issuer = generateEd25519KeyPair()
    const issuerDid = publicKeyToDidKey(issuer.publicKey)
    return { credential: signJws({ iss: issuerDid, sub: did, vc: { type: ['VerifiableCredential', name], credentialSubject: { id: did } } }, issuer.privateKey, issuerDid), format: 'vc+jwt' }
  }
  it('restores an export without replacing keys or duplicating existing records', async () => {
    const holder = (await bootstrap()).holder
    const credential = issued(holder.did)
    await store(credential.credential, 'vc+jwt')
    const exported = exportWalletData()
    const originalKeys = await loadHolderKeys(holder.did)
    storage.setItem('trustweave-wallet-credentials', '[]')
    expect(await restoreCredentials(exported)).toEqual({ added: 1, skipped: 0 })
    expect(await restoreCredentials(exported)).toEqual({ added: 0, skipped: 1 })
    expect(loadHolder()?.did).toBe(holder.did)
    expect((await loadHolderKeys(holder.did)).signing.extractable).toBe(originalKeys.signing.extractable)
    expect(loadCredentials()[0].credential).toBe(credential.credential)
  })
  it('rejects a bad signature after a good record without partial writes', async () => {
    const holder = (await bootstrap()).holder
    const good = issued(holder.did)
    const bad = { ...issued(holder.did), credential: 'bad.signature.value' }
    const before = storage.getItem('trustweave-wallet-credentials')
    await expect(restoreCredentials(backup(holder.did, [good, bad]))).rejects.toThrow()
    expect(storage.getItem('trustweave-wallet-credentials')).toBe(before)
  })
  it('rejects another identity and refuses forged holder metadata', async () => {
    const holder = (await bootstrap()).holder
    await expect(restoreCredentials(backup('did:key:other', []))).rejects.toThrow('different wallet')
    await expect(restoreCredentials(backup(holder.did, [issued('did:key:other')]))).rejects.toThrow()
    expect(loadCredentials()).toHaveLength(0)
  })
  it('reconstructs labels and disclosure choices from verified content', async () => {
    const holder = (await bootstrap()).holder
    const issuer = generateEd25519KeyPair()
    const did = publicKeyToDidKey(issuer.publicKey)
    const { issueSdJwtVc } = await import('../lib/sdjwt')
    const credential = issueSdJwtVc({ issuerDid: did, issuerPrivateKey: issuer.privateKey, issuerKid: did, holderDid: holder.did,
      alwaysVisible: {}, selectivelyDisclosable: [{ name: 'secret', value: 'hidden' }], vct: 'Employee', now: Math.floor(Date.now() / 1000) })
    await restoreCredentials(backup(holder.did, [{ credential, format: 'vc+sd-jwt', issuerDid: 'attacker', preview: { title: 'forged' }, selectivelyDisclosable: ['forged'] }]))
    expect(loadCredentials()[0].issuerDid).toBe(did)
    expect(loadCredentials()[0].preview.title).not.toBe('forged')
    expect(loadCredentials()[0].selectivelyDisclosable).toEqual(['secret'])
  })
  it('bounds untrusted input and preserves the existing wallet', async () => {
    const holder = (await bootstrap()).holder
    await expect(restoreCredentials('x'.repeat(5 * 1024 * 1024 + 1))).rejects.toThrow('5 MB')
    await expect(restoreCredentials(backup(holder.did, Array(501).fill({})))).rejects.toThrow('500')
    await expect(restoreCredentials(JSON.stringify({ version: '99' }))).rejects.toThrow('Unsupported')
    expect(loadHolder()?.did).toBe(holder.did)
  })
})


describe('lost-key replacement', () => {
  it('keeps old credentials, rejects their presentation and accepts reissuance to the new identity', async () => {
    const old = (await bootstrap()).holder
    const issuer = generateEd25519KeyPair()
    const did = publicKeyToDidKey(issuer.publicKey)
    const issue = (subject: string) => signJws({ iss: did, sub: subject, vc: { type: ['Employee'], credentialSubject: { id: subject } } }, issuer.privateKey, did)
    const previous = (await store(issue(old.did), 'vc+jwt')).credential
    await clearHolderKeys()
    expect(await canReplaceLostKey()).toBe(true)
    const replacement = await replaceLostKey()
    expect(replacement.holder.did).not.toBe(old.did)
    expect(replacement.credentials).toEqual([previous])
    expect((await bootstrap()).holder.did).toBe(replacement.holder.did)
    await expect(createPresentation([previous.id], 'verifier', 'nonce')).rejects.toThrow('another holder')
    const reissued = (await store(issue(replacement.holder.did), 'vc+jwt')).credential
    expect(await createPresentation([reissued.id], 'verifier', 'nonce')).toBeTruthy()
  })
  it('refuses replacement while the existing key is usable', async () => {
    const before = (await bootstrap()).holder
    expect(await canReplaceLostKey()).toBe(false)
    await expect(replaceLostKey()).rejects.toThrow('only when')
    expect(loadHolder()).toEqual(before)
  })
  it('retains identity and credentials if replacement metadata cannot be committed', async () => {
    await bootstrap(); await clearHolderKeys()
    const before = exportWalletData()
    const write = storage.setItem.bind(storage)
    vi.spyOn(storage, 'setItem').mockImplementation((key, value) => { if (key === 'trustweave-wallet-holder') throw new Error('quota'); write(key, value) })
    await expect(replaceLostKey()).rejects.toThrow('quota')
    expect(exportWalletData()).toBe(before)
  })
  it('serializes competing replacement requests so only one new identity wins', async () => {
    await bootstrap(); await clearHolderKeys()
    const results = await Promise.allSettled([replaceLostKey(), replaceLostKey()])
    expect(results.filter(result => result.status === 'fulfilled')).toHaveLength(1)
    expect(await canReplaceLostKey()).toBe(false)
  })
})
