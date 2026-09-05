/** Browser-managed, non-extractable signing and agreement keys. Never persist raw seed material. */
import { ed25519, edwardsToMontgomeryPriv } from '@noble/curves/ed25519'
import { b64uDecode, b64uEncode, b64uEncodeString, publicKeyToDidKey } from './crypto'

type HolderKeys = { signing: CryptoKey; agreement: CryptoKey }
const DATABASE = 'trustweave-holder-keys'

async function database(): Promise<IDBDatabase> {
  if (!globalThis.crypto?.subtle || typeof indexedDB === 'undefined') {
    throw new Error('This wallet needs a secure browser with WebCrypto and IndexedDB. Existing data has been preserved.')
  }
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE, 1)
    request.onupgradeneeded = () => request.result.createObjectStore('keys')
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function transaction<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await database()
  try {
    return await new Promise<T>((resolve, reject) => {
      const tx = db.transaction('keys', mode)
      const request = action(tx.objectStore('keys'))
      tx.oncomplete = () => resolve(request.result)
      tx.onerror = () => reject(tx.error)
      tx.onabort = () => reject(tx.error ?? new Error('Key storage transaction was interrupted'))
    })
  } finally { db.close() }
}

export async function loadHolderKeys(did: string): Promise<HolderKeys> {
  const keys = await transaction<HolderKeys | undefined>('readonly', store => store.get(did))
  if (!keys) throw new Error('The signing key is unavailable on this device. Export your credentials and ask their issuers to reissue them to a new identity.')
  if (keys.signing.extractable || keys.agreement.extractable) throw new Error('Unsafe key storage; refusing to sign')
  return keys
}

/** Import a legacy/generated seed once, then discard it. Metadata is updated only after commit. */
export async function importHolderKeys(did: string, seed: Uint8Array): Promise<void> {
  if (publicKeyToDidKey(ed25519.getPublicKey(seed)) !== did) {
    seed.fill(0)
    throw new Error('Legacy private key does not match the wallet identity. Original data has been preserved.')
  }
  const agreementSeed = edwardsToMontgomeryPriv(seed)
  // RFC 8410 PKCS#8 wrapper around a 32-byte Ed25519 / X25519 private seed.
  const pkcs8 = (raw: Uint8Array, oid: number) => {
    const bytes = new Uint8Array(48)
    bytes.set([0x30, 0x2e, 2, 1, 0, 0x30, 5, 6, 3, 0x2b, 0x65, oid, 4, 0x22, 4, 0x20])
    bytes.set(raw, 16)
    return bytes
  }
  const signingBytes = pkcs8(seed, 0x70)
  const agreementBytes = pkcs8(agreementSeed, 0x6e)
  try {
    const signing = await crypto.subtle.importKey('pkcs8', signingBytes, 'Ed25519', false, ['sign'])
    const agreement = await crypto.subtle.importKey('pkcs8', agreementBytes, 'X25519', false, ['deriveBits'])
    await transaction('readwrite', store => store.put({ signing, agreement }, did))
  } finally {
    signingBytes.fill(0); agreementBytes.fill(0); agreementSeed.fill(0); seed.fill(0)
  }
}

export async function signHolderJws(payload: Record<string, unknown>, did: string, typ = 'JWT'): Promise<string> {
  const { signing } = await loadHolderKeys(did)
  const header = { alg: 'EdDSA', typ, kid: `${did}#${did.slice('did:key:'.length)}` }
  const input = `${b64uEncodeString(JSON.stringify(header))}.${b64uEncodeString(JSON.stringify(payload))}`
  const signature = await crypto.subtle.sign('Ed25519', signing, new TextEncoder().encode(input))
  return `${input}.${b64uEncode(new Uint8Array(signature))}`
}

export async function holderSharedSecret(did: string, publicKey: string): Promise<Uint8Array> {
  const { agreement } = await loadHolderKeys(did)
  const publicCryptoKey = await crypto.subtle.importKey('raw', b64uDecode(publicKey), 'X25519', false, [])
  return new Uint8Array(await crypto.subtle.deriveBits({ name: 'X25519', public: publicCryptoKey }, agreement, 256))
}

export async function clearHolderKeys(): Promise<void> {
  await transaction('readwrite', store => store.clear())
}
