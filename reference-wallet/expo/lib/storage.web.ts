/**
 * Web-only storage adapter — uses localStorage instead of expo-secure-store.
 * localStorage is NOT secure (no hardware backing, visible to JS on the same origin)
 * but it works in the browser for development and demo purposes.
 * On native (iOS/Android) the bundler picks storage.ts instead of this file.
 */

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
  const raw = localStorage.getItem(HOLDER_KEY)
  return raw ? (JSON.parse(raw) as HolderIdentity) : null
}

export async function saveHolder(holder: HolderIdentity): Promise<void> {
  localStorage.setItem(HOLDER_KEY, JSON.stringify(holder))
}

async function loadIndex(): Promise<IndexEntry[]> {
  const raw = localStorage.getItem(INDEX_KEY)
  return raw ? (JSON.parse(raw) as IndexEntry[]) : []
}

async function saveIndex(entries: IndexEntry[]): Promise<void> {
  localStorage.setItem(INDEX_KEY, JSON.stringify(entries))
}

export async function loadCredentials(): Promise<StoredCredential[]> {
  const index = await loadIndex()
  const results: StoredCredential[] = []
  for (const meta of index) {
    const cred = localStorage.getItem(CRED_PREFIX + meta.id)
    if (cred) results.push({ ...meta, credential: cred })
  }
  return results
}

export async function addCredential(cred: StoredCredential): Promise<void> {
  localStorage.setItem(CRED_PREFIX + cred.id, cred.credential)
  const index = await loadIndex()
  const { credential: _, ...meta } = cred
  index.push(meta)
  await saveIndex(index)
}

export async function deleteCredential(id: string): Promise<void> {
  localStorage.removeItem(CRED_PREFIX + id)
  const index = (await loadIndex()).filter((c) => c.id !== id)
  await saveIndex(index)
}

export async function resetWallet(): Promise<void> {
  const index = await loadIndex()
  index.forEach((c) => localStorage.removeItem(CRED_PREFIX + c.id))
  localStorage.removeItem(INDEX_KEY)
  localStorage.removeItem(HOLDER_KEY)
}
