/**
 * Storage adapter for the Expo wallet.
 *
 * Holder identity (the small JSON with did + key bytes) lives in expo-secure-store,
 * which is Keychain-backed on iOS and EncryptedSharedPreferences-backed on Android.
 * SecureStore items are limited to ~2KB on Android; the holder record fits, and
 * we store credentials individually keyed by ID so each one fits too.
 *
 * The credential INDEX (just the list of IDs + preview metadata) lives in a single
 * SecureStore item so listing is fast. Each credential's `credential` field (the
 * raw VC-JWT or SD-JWT VC string, ~1.5KB) lives in its own item keyed by ID.
 */
import * as SecureStore from 'expo-secure-store'

const HOLDER_KEY = 'trustweave-wallet-holder'
const INDEX_KEY = 'trustweave-wallet-credential-index'
const CRED_PREFIX = 'trustweave-wallet-cred-'

export interface HolderIdentity {
  did: string
  publicKey: string
  privateKey: string
  createdAt: string
}

export interface StoredCredential {
  id: string
  format: 'vc+jwt' | 'vc+sd-jwt'
  credential: string
  receivedAt: string
  issuerDid: string
  type: string[]
  subjectDid: string
  preview: { title: string; subtitle?: string }
  selectivelyDisclosable: string[]
}

type IndexEntry = Omit<StoredCredential, 'credential'>

export async function loadHolder(): Promise<HolderIdentity | null> {
  const raw = await SecureStore.getItemAsync(HOLDER_KEY)
  return raw ? (JSON.parse(raw) as HolderIdentity) : null
}

export async function saveHolder(holder: HolderIdentity): Promise<void> {
  await SecureStore.setItemAsync(HOLDER_KEY, JSON.stringify(holder))
}

async function loadIndex(): Promise<IndexEntry[]> {
  const raw = await SecureStore.getItemAsync(INDEX_KEY)
  return raw ? (JSON.parse(raw) as IndexEntry[]) : []
}

async function saveIndex(entries: IndexEntry[]): Promise<void> {
  await SecureStore.setItemAsync(INDEX_KEY, JSON.stringify(entries))
}

export async function loadCredentials(): Promise<StoredCredential[]> {
  const index = await loadIndex()
  const results: StoredCredential[] = []
  for (const meta of index) {
    const cred = await SecureStore.getItemAsync(CRED_PREFIX + meta.id)
    if (cred) results.push({ ...meta, credential: cred })
  }
  return results
}

export async function addCredential(cred: StoredCredential): Promise<void> {
  await SecureStore.setItemAsync(CRED_PREFIX + cred.id, cred.credential)
  const index = await loadIndex()
  const { credential: _, ...meta } = cred
  index.push(meta)
  await saveIndex(index)
}

export async function deleteCredential(id: string): Promise<void> {
  await SecureStore.deleteItemAsync(CRED_PREFIX + id)
  const index = (await loadIndex()).filter((c) => c.id !== id)
  await saveIndex(index)
}

export async function resetWallet(): Promise<void> {
  const index = await loadIndex()
  await Promise.all(index.map((c) => SecureStore.deleteItemAsync(CRED_PREFIX + c.id)))
  await SecureStore.deleteItemAsync(INDEX_KEY)
  await SecureStore.deleteItemAsync(HOLDER_KEY)
}
