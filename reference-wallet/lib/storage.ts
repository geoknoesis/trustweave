/**
 * Browser localStorage adapter for the reference wallet.
 *
 * Phase 1 → 2.5: storage shape extended to remember which disclosures the issuer
 * said are selectively-disclosable. The wallet uses that list to drive the
 * presentation consent UI (checkbox per disclosable claim).
 *
 * SECURITY NOTE: localStorage is NOT a secure key store. The holder private key
 * sits in cleartext, accessible to any JS running in the page (and to extensions).
 * Acceptable for a Phase 1/2 walking-skeleton demo; would NOT be acceptable for
 * any production wallet. Phase 2.5 moves the holder key behind WebAuthn-bound
 * non-extractable WebCrypto keys for the web build.
 */

const HOLDER_KEY = 'trustweave-wallet-holder'
const CREDENTIALS_KEY = 'trustweave-wallet-credentials'
const VERSION_KEY = 'trustweave-wallet-schema-version'
const CURRENT_VERSION = 2

export interface HolderIdentity {
  did: string
  publicKey: string  // base64url
  privateKey: string  // base64url
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

function ensureSchemaVersion(): void {
  if (!isBrowser()) return
  const existing = window.localStorage.getItem(VERSION_KEY)
  if (!existing) {
    window.localStorage.setItem(VERSION_KEY, String(CURRENT_VERSION))
  } else if (Number(existing) !== CURRENT_VERSION) {
    // v1 → v2: storage shape changed (added `format`, `credential` replaces `vcJwt`,
    // added `selectivelyDisclosable`). No automatic migration — wipe and let the user
    // re-receive. Acceptable for a demo wallet; a real wallet would migrate in place.
    window.localStorage.removeItem(HOLDER_KEY)
    window.localStorage.removeItem(CREDENTIALS_KEY)
    window.localStorage.setItem(VERSION_KEY, String(CURRENT_VERSION))
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

export function resetWallet(): void {
  if (!isBrowser()) return
  window.localStorage.removeItem(HOLDER_KEY)
  window.localStorage.removeItem(CREDENTIALS_KEY)
  window.localStorage.removeItem(VERSION_KEY)
}
