/**
 * Storage adapter for the Expo wallet.
 *
 * Holder identity (small JSON with did + key bytes), accepted issuers,
 * preferences, and the credential INDEX live in expo-secure-store
 * (Keychain on iOS, EncryptedSharedPreferences on Android).
 *
 * Credential payloads (VC-JWT, SD-JWT VC, VC-LD JSON) and activity history
 * live in AsyncStorage so embedded portraits or history logs never exceed
 * SecureStore's ~2KB Android per-item limit.
 */
import AsyncStorage from '@react-native-async-storage/async-storage'
import { Platform } from 'react-native'
import * as SecureStore from 'expo-secure-store'
import { credentialDedupKey } from './credentialDedup'
import { sanitizeAcceptedIssuers, withAcceptedIssuer, type IssuerTrustSource } from './issuerTrust'

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

const HOLDER_KEY = 'trustweave-wallet-holder'
const INDEX_KEY = 'trustweave-wallet-credential-index'
const CRED_PREFIX = 'trustweave-wallet-cred-'
const ACCEPTED_ISSUERS_KEY = 'trustweave-wallet-accepted-issuers'
const ACTIVITY_KEY = 'trustweave-wallet-activity-v1'
const PREFS_KEY = 'trustweave-wallet-prefs-v1'
const MAX_ACTIVITY_ENTRIES = 100

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
  issuerName?: string
  trustSource?: IssuerTrustSource
  type: string[]
  subjectDid: string
  preview: { title: string; subtitle?: string }
  selectivelyDisclosable: string[]
}

export type ActivityKind =
  | 'added'
  | 'updated'
  | 'shared'
  | 'removed'
  | 'trusted_issuer'
  | 'backup'
  | 'restored'

export interface ActivityEntry {
  id: string
  timestamp: string
  kind: ActivityKind
  title: string
  subtitle?: string
  counterparty?: string
  detailsShared?: number
  totalDetails?: number
  status: 'success' | 'failed'
}

export interface WalletPreferences {
  onboardingComplete: boolean
  biometricLockEnabled: boolean
  developerMode: boolean
}

export const DEFAULT_PREFERENCES: WalletPreferences = {
  onboardingComplete: false,
  biometricLockEnabled: true,
  developerMode: false,
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

export async function loadAcceptedIssuers(): Promise<string[]> {
  try {
    const raw = await store.getItemAsync(ACCEPTED_ISSUERS_KEY)
    if (!raw) return []
    return sanitizeAcceptedIssuers(JSON.parse(raw))
  } catch {
    return []
  }
}

export async function addAcceptedIssuer(did: string): Promise<string[]> {
  const next = withAcceptedIssuer(await loadAcceptedIssuers(), did)
  await store.setItemAsync(ACCEPTED_ISSUERS_KEY, JSON.stringify(next))
  return next
}

export async function removeAcceptedIssuer(did: string): Promise<string[]> {
  const next = (await loadAcceptedIssuers()).filter((item) => item !== did)
  await store.setItemAsync(ACCEPTED_ISSUERS_KEY, JSON.stringify(next))
  return next
}

export async function loadActivity(): Promise<ActivityEntry[]> {
  try {
    const raw = await AsyncStorage.getItem(ACTIVITY_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? (parsed as ActivityEntry[]) : []
  } catch {
    return []
  }
}

export async function appendActivity(entry: ActivityEntry): Promise<void> {
  const existing = await loadActivity()
  const next = [entry, ...existing].slice(0, MAX_ACTIVITY_ENTRIES)
  await AsyncStorage.setItem(ACTIVITY_KEY, JSON.stringify(next))
}

export async function clearActivity(): Promise<void> {
  await AsyncStorage.removeItem(ACTIVITY_KEY)
}

export async function loadPreferences(): Promise<WalletPreferences> {
  try {
    const raw = await AsyncStorage.getItem(PREFS_KEY)
    if (!raw) return { ...DEFAULT_PREFERENCES }
    const parsed = JSON.parse(raw) as Partial<WalletPreferences>
    return {
      onboardingComplete: Boolean(parsed.onboardingComplete),
      biometricLockEnabled:
        typeof parsed.biometricLockEnabled === 'boolean'
          ? parsed.biometricLockEnabled
          : DEFAULT_PREFERENCES.biometricLockEnabled,
      developerMode: Boolean(parsed.developerMode),
    }
  } catch {
    return { ...DEFAULT_PREFERENCES }
  }
}

export async function savePreferences(patch: Partial<WalletPreferences>): Promise<WalletPreferences> {
  const current = await loadPreferences()
  const next: WalletPreferences = { ...current, ...patch }
  await AsyncStorage.setItem(PREFS_KEY, JSON.stringify(next))
  return next
}

export async function resetWallet(): Promise<void> {
  const index = await loadIndex()
  await Promise.all(index.map((c) => deleteCredentialBody(c.id)))
  await store.deleteItemAsync(INDEX_KEY)
  await store.deleteItemAsync(HOLDER_KEY)
  await store.deleteItemAsync(ACCEPTED_ISSUERS_KEY)
  await AsyncStorage.removeItem(ACTIVITY_KEY)
}
