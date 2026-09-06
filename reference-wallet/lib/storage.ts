/**
 * Browser localStorage adapter for the reference wallet.
 * said are selectively-disclosable. The wallet uses that list to drive the
 * presentation consent UI (checkbox per disclosable claim).
 *
 * Holder metadata is public. Signing/agreement keys live as non-extractable
 * CryptoKeys in IndexedDB; legacy raw seeds are migrated by wallet.bootstrap().
 */

import { credentialDedupKey } from './credential-dedup'

const HOLDER_KEY = 'trustweave-wallet-holder'
const CREDENTIALS_KEY = 'trustweave-wallet-credentials'
const VERSION_KEY = 'trustweave-wallet-schema-version'
const CURRENT_VERSION = 2

export interface HolderIdentity {
  did: string
  publicKey: string  // base64url
  createdAt: string
}

export interface StoredCredential {
  id: string  // local UUID for the wallet's record
  format: 'vc+jwt' | 'vc+sd-jwt'
  credential: string  // the credential as-issued (VC-JWT or SD-JWT VC compact form)
  receivedAt: string
  issuerDid: string
  type: string[]  // either `vc.type` (VC-JWT) or `[vct]` (SD-JWT VC)
  subjectDid: string
  preview: {
    title: string
    subtitle?: string
  }
  /**
   * For SD-JWT VC: names of claims the issuer marked as selectively disclosable.
   * Empty for plain VC-JWT (no selective disclosure).
   */
  selectivelyDisclosable: string[]
}

function isBrowser(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
}

function validateCredentials(value: unknown): asserts value is StoredCredential[] {
  if (!Array.isArray(value)) throw new Error('Invalid wallet credential collection. Export it before recovery.')
  const seen = new Set<string>()
  for (const item of value) {
    if (!item || typeof item !== 'object' || typeof item.id !== 'string' || seen.has(item.id) ||
        typeof item.credential !== 'string' || !['vc+jwt', 'vc+sd-jwt'].includes(item.format) ||
        typeof item.issuerDid !== 'string' || typeof item.subjectDid !== 'string' || typeof item.receivedAt !== 'string' ||
        !Array.isArray(item.type) || item.type.some((v: unknown) => typeof v !== 'string') ||
        !Array.isArray(item.selectivelyDisclosable) || item.selectivelyDisclosable.some((v: unknown) => typeof v !== 'string') ||
        !item.preview || typeof item.preview.title !== 'string' ||
        (item.preview.subtitle !== undefined && typeof item.preview.subtitle !== 'string'))
      throw new Error('Invalid stored credential. Your original data has been preserved; export it before recovery.')
    seen.add(item.id)
  }
}

function ensureSchemaVersion(): void {
  if (!isBrowser()) return
  const existing = window.localStorage.getItem(VERSION_KEY)
  if (!existing && !window.localStorage.getItem(CREDENTIALS_KEY)) {
    window.localStorage.setItem(VERSION_KEY, String(CURRENT_VERSION))
  } else if (!existing || existing === '1') {
    const raw = window.localStorage.getItem(CREDENTIALS_KEY)
    const credentials: unknown = raw ? JSON.parse(raw) : []
    if (!Array.isArray(credentials)) throw new Error('Invalid legacy wallet data. Export it before recovery.')
    const migrated = credentials.map((credential: Record<string, unknown>) => {
      const compact = credential.credential ?? credential.vcJwt
      if (typeof compact !== 'string' || typeof credential.id !== 'string') throw new Error('Unsupported legacy credential. Your original data has been preserved.')
      return { ...credential, credential: compact, format: credential.format ?? 'vc+jwt', selectivelyDisclosable: credential.selectivelyDisclosable ?? [] }
    })
    validateCredentials(migrated)
    // Save version last: an interrupted upgrade can be safely repeated. Never rotate the holder.
    window.localStorage.setItem(CREDENTIALS_KEY, JSON.stringify(migrated))
    window.localStorage.setItem(VERSION_KEY, String(CURRENT_VERSION))
  } else if (existing !== String(CURRENT_VERSION)) {
    throw new Error(`Wallet schema ${existing} is not supported by this version. Open the version that created it or export your credentials.`)
  }
}

export function loadHolder(): HolderIdentity | null {
  if (!isBrowser()) return null
  ensureSchemaVersion()
  const raw = window.localStorage.getItem(HOLDER_KEY)
  if (!raw) return null
  const holder = JSON.parse(raw)
  if (!holder || typeof holder.did !== 'string' || typeof holder.publicKey !== 'string' || typeof holder.createdAt !== 'string')
    throw new Error('Invalid wallet identity metadata. Export your credentials before recovery.')
  return holder as HolderIdentity
}

export function saveHolder(holder: HolderIdentity): void {
  if (!isBrowser()) throw new Error('saveHolder requires a browser environment')
  ensureSchemaVersion()
  window.localStorage.setItem(HOLDER_KEY, JSON.stringify({ did: holder.did, publicKey: holder.publicKey, createdAt: holder.createdAt }))
}

export function loadCredentials(): StoredCredential[] {
  if (!isBrowser()) return []
  ensureSchemaVersion()
  const raw = window.localStorage.getItem(CREDENTIALS_KEY)
  const records: unknown = raw ? JSON.parse(raw) : []
  validateCredentials(records)
  return records
}

export function saveCredentials(creds: StoredCredential[]): void {
  if (!isBrowser()) throw new Error('saveCredentials requires a browser environment')
  ensureSchemaVersion()
  validateCredentials(creds)
  window.localStorage.setItem(CREDENTIALS_KEY, JSON.stringify(creds))
}

export function addCredential(cred: StoredCredential): void {
  const all = loadCredentials()
  all.push(cred)
  saveCredentials(all)
}

/** Insert or replace a credential with the same logical identity (issuer, subject, type, and stable claims). */
export function upsertCredential(cred: StoredCredential): { credential: StoredCredential; replaced: boolean } {
  const all = loadCredentials()
  const key = credentialDedupKey(cred)
  const idx = all.findIndex((c) => credentialDedupKey(c) === key)
  if (idx >= 0) {
    const updated: StoredCredential = {
      ...all[idx],
      ...cred,
      id: all[idx].id,
      receivedAt: new Date().toISOString(),
    }
    all[idx] = updated
    saveCredentials(all)
    return { credential: updated, replaced: true }
  }
  all.push(cred)
  saveCredentials(all)
  return { credential: cred, replaced: false }
}

export function deleteCredential(id: string): void {
  const all = loadCredentials().filter((c) => c.id !== id)
  saveCredentials(all)
}

export function resetWallet(): void {
  if (!isBrowser()) return
  window.localStorage.removeItem(HOLDER_KEY)
  window.localStorage.removeItem(CREDENTIALS_KEY)
  window.localStorage.removeItem(VERSION_KEY)
}

/** Recovery export intentionally excludes private key material, including legacy seeds. */
export function exportWalletData(): string {
  const raw = window.localStorage.getItem(HOLDER_KEY)
  let holder: Partial<HolderIdentity> = {}
  try { const parsed = JSON.parse(raw ?? '{}'); holder = { did: parsed.did, publicKey: parsed.publicKey, createdAt: parsed.createdAt } } catch { /* preserve credential data even if identity metadata is corrupt */ }
  return JSON.stringify({ version: window.localStorage.getItem(VERSION_KEY), holder, credentials: window.localStorage.getItem(CREDENTIALS_KEY) ?? '[]' }, null, 2)
}
