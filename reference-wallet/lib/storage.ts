/**
 * Browser localStorage adapter for the reference wallet.
 *
 * Phase 1 only — Phase 2 (mobile) uses Keychain / Secure Enclave. The shape of this
 * module deliberately mirrors what a real `SecureKeyStore` + `CredentialStore` pair
 * would look like, so swapping the implementation later is a focused refactor.
 *
 * SECURITY NOTE: localStorage is NOT a secure key store. The holder private key sits
 * in cleartext, accessible to any JS running in the page (and to extensions). This is
 * acceptable for a Phase 1 walking-skeleton demo; it would not be acceptable for any
 * production wallet. Phase 1.1 should move the holder key behind WebAuthn-bound non-
 * extractable WebCrypto keys.
 */

const HOLDER_KEY = 'trustweave-wallet-holder'
const CREDENTIALS_KEY = 'trustweave-wallet-credentials'
const VERSION_KEY = 'trustweave-wallet-schema-version'
const CURRENT_VERSION = 1

export interface HolderIdentity {
  did: string
  publicKey: string  // base64url
  privateKey: string  // base64url
  createdAt: string
}

export interface StoredCredential {
  id: string  // local UUID for the wallet's record
  vcJwt: string  // the VC-JWT itself
  receivedAt: string
  issuerDid: string
  type: string[]
  subjectDid: string
  preview: {
    title: string
    subtitle?: string
  }
}

function isBrowser(): boolean {
  return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
}

function ensureSchemaVersion(): void {
  if (!isBrowser()) return
  const existing = window.localStorage.getItem(VERSION_KEY)
  if (!existing) {
    window.localStorage.setItem(VERSION_KEY, String(CURRENT_VERSION))
  } else if (Number(existing) !== CURRENT_VERSION) {
    throw new Error(
      `Wallet storage schema mismatch: have v${existing}, expected v${CURRENT_VERSION}. ` +
      `Use Settings → Reset Wallet to start over.`,
    )
  }
}

export function loadHolder(): HolderIdentity | null {
  if (!isBrowser()) return null
  ensureSchemaVersion()
  const raw = window.localStorage.getItem(HOLDER_KEY)
  return raw ? (JSON.parse(raw) as HolderIdentity) : null
}

export function saveHolder(holder: HolderIdentity): void {
  if (!isBrowser()) throw new Error('saveHolder requires a browser environment')
  ensureSchemaVersion()
  window.localStorage.setItem(HOLDER_KEY, JSON.stringify(holder))
}

export function loadCredentials(): StoredCredential[] {
  if (!isBrowser()) return []
  ensureSchemaVersion()
  const raw = window.localStorage.getItem(CREDENTIALS_KEY)
  return raw ? (JSON.parse(raw) as StoredCredential[]) : []
}

export function saveCredentials(creds: StoredCredential[]): void {
  if (!isBrowser()) throw new Error('saveCredentials requires a browser environment')
  ensureSchemaVersion()
  window.localStorage.setItem(CREDENTIALS_KEY, JSON.stringify(creds))
}

export function addCredential(cred: StoredCredential): void {
  const all = loadCredentials()
  all.push(cred)
  saveCredentials(all)
}

export function deleteCredential(id: string): void {
  const all = loadCredentials().filter((c) => c.id !== id)
  saveCredentials(all)
}

/** Wipe the wallet — irrecoverable. Used by the Settings → Reset action. */
export function resetWallet(): void {
  if (!isBrowser()) return
  window.localStorage.removeItem(HOLDER_KEY)
  window.localStorage.removeItem(CREDENTIALS_KEY)
  window.localStorage.removeItem(VERSION_KEY)
}
