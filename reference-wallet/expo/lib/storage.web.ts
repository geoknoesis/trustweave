/**
 * Web-only storage adapter — uses localStorage instead of expo-secure-store.
 * localStorage is NOT secure (no hardware backing, visible to JS on the same origin)
 * but it works in the browser for development and demo purposes.
 * On native (iOS/Android) the bundler picks storage.ts instead of this file.
 */
import { credentialDedupKey } from './credentialDedup'
import { sanitizeAcceptedIssuers, withAcceptedIssuer, type IssuerTrustSource } from './issuerTrust'

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
    localStorage.setItem(CRED_PREFIX + updated.id, updated.credential)
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
  localStorage.removeItem(CRED_PREFIX + id)
  const index = (await loadIndex()).filter((c) => c.id !== id)
  await saveIndex(index)
}

export async function loadAcceptedIssuers(): Promise<string[]> {
  try {
    const raw = localStorage.getItem(ACCEPTED_ISSUERS_KEY)
    if (!raw) return []
    return sanitizeAcceptedIssuers(JSON.parse(raw))
  } catch {
    return []
  }
}

export async function addAcceptedIssuer(did: string): Promise<string[]> {
  const next = withAcceptedIssuer(await loadAcceptedIssuers(), did)
  localStorage.setItem(ACCEPTED_ISSUERS_KEY, JSON.stringify(next))
  return next
}

export async function removeAcceptedIssuer(did: string): Promise<string[]> {
  const next = (await loadAcceptedIssuers()).filter((item) => item !== did)
  localStorage.setItem(ACCEPTED_ISSUERS_KEY, JSON.stringify(next))
  return next
}

export async function loadActivity(): Promise<ActivityEntry[]> {
  try {
    const raw = localStorage.getItem(ACTIVITY_KEY)
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
  localStorage.setItem(ACTIVITY_KEY, JSON.stringify(next))
}

export async function clearActivity(): Promise<void> {
  localStorage.removeItem(ACTIVITY_KEY)
}

export async function loadPreferences(): Promise<WalletPreferences> {
  try {
    const raw = localStorage.getItem(PREFS_KEY)
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
  localStorage.setItem(PREFS_KEY, JSON.stringify(next))
  return next
}

export async function resetWallet(): Promise<void> {
  const index = await loadIndex()
  index.forEach((c) => localStorage.removeItem(CRED_PREFIX + c.id))
  localStorage.removeItem(INDEX_KEY)
  localStorage.removeItem(HOLDER_KEY)
  localStorage.removeItem(ACCEPTED_ISSUERS_KEY)
  localStorage.removeItem(ACTIVITY_KEY)
}
