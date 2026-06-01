/**
 * Storage adapter for the Expo wallet.
 *
 * Holder identity (the small JSON with did + key bytes) lives in expo-secure-store,
 * which is Keychain-backed on iOS and EncryptedSharedPreferences-backed on Android.
 *
 * Credential payloads (VC-JWT, SD-JWT VC, VC-LD JSON) can exceed SecureStore's ~2KB
 * Android limit when claims embed demo portraits — those live in AsyncStorage instead.
 * The credential INDEX (IDs + preview metadata) stays in SecureStore for fast listing.
 */
import AsyncStorage from '@react-native-async-storage/async-storage'
import { Platform } from 'react-native'
import * as SecureStore from 'expo-secure-store'

// expo-secure-store has no web implementation; fall back to localStorage for web preview.
const store =
  Platform.OS === 'web'
    ? {
        getItemAsync: async (key: string) => localStorage.getItem(key),
        setItemAsync: async (key: string, value: string) => {
          localStorage.setItem(key, value)
        },
        deleteItemAsync: async (key: string) => {
          localStorage.removeItem(key)
        },
      }
    : SecureStore

import { credentialDedupKey } from './credentialDedup'

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
  format: 'vc+jwt' | 'vc+sd-jwt' | 'vc-ld'
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
  const raw = await store.getItemAsync(HOLDER_KEY)
  return raw ? (JSON.parse(raw) as HolderIdentity) : null
}

export async function saveHolder(holder: HolderIdentity): Promise<void> {
  await store.setItemAsync(HOLDER_KEY, JSON.stringify(holder))
}

async function loadIndex(): Promise<IndexEntry[]> {
  const raw = await store.getItemAsync(INDEX_KEY)
  return raw ? (JSON.parse(raw) as IndexEntry[]) : []
}

async function saveIndex(entries: IndexEntry[]): Promise<void> {
  await store.setItemAsync(INDEX_KEY, JSON.stringify(entries))
}

async function loadCredentialBody(id: string): Promise<string | null> {
  const key = CRED_PREFIX + id
  const fromAsync = await AsyncStorage.getItem(key)
  if (fromAsync) return fromAsync
  // Migrate credentials stored before AsyncStorage split (small VCs only).
  const legacy = await store.getItemAsync(key)
  if (legacy) {
    await AsyncStorage.setItem(key, legacy)
    await store.deleteItemAsync(key)
    return legacy
  }
  return null
}

async function saveCredentialBody(id: string, credential: string): Promise<void> {
  await AsyncStorage.setItem(CRED_PREFIX + id, credential)
}

async function deleteCredentialBody(id: string): Promise<void> {
  await AsyncStorage.removeItem(CRED_PREFIX + id)
  await store.deleteItemAsync(CRED_PREFIX + id)
}

export async function loadCredentials(): Promise<StoredCredential[]> {
  const index = await loadIndex()
  const results: StoredCredential[] = []
  for (const meta of index) {
    const cred = await loadCredentialBody(meta.id)
    if (cred) results.push({ ...meta, credential: cred })
  }
  return results
}

export async function addCredential(cred: StoredCredential): Promise<void> {
  await saveCredentialBody(cred.id, cred.credential)
  const index = await loadIndex()
  const { credential: _, ...meta } = cred
  index.push(meta)
  await saveIndex(index)
}

/** Insert or replace a credential with the same logical identity (issuer, subject, type, and stable claims). */
export async function upsertCredential(
  cred: StoredCredential,
): Promise<{ credential: StoredCredential; replaced: boolean }> {
  const all = await loadCredentials()
  const key = credentialDedupKey(cred)
  const existing = all.find((c) => credentialDedupKey(c) === key)
  if (existing) {
    const updated: StoredCredential = {
      ...existing,
      ...cred,
      id: existing.id,
      receivedAt: new Date().toISOString(),
    }
    await saveCredentialBody(updated.id, updated.credential)
    const index = await loadIndex()
    const idx = index.findIndex((c) => c.id === existing.id)
    if (idx >= 0) {
      const { credential: _, ...meta } = updated
      index[idx] = meta
      await saveIndex(index)
    }
    return { credential: updated, replaced: true }
  }
  await addCredential(cred)
  return { credential: cred, replaced: false }
}

export async function deleteCredential(id: string): Promise<void> {
  await deleteCredentialBody(id)
  const index = (await loadIndex()).filter((c) => c.id !== id)
  await saveIndex(index)
}

export async function resetWallet(): Promise<void> {
  const index = await loadIndex()
  await Promise.all(index.map((c) => deleteCredentialBody(c.id)))
  await store.deleteItemAsync(INDEX_KEY)
  await store.deleteItemAsync(HOLDER_KEY)
}
