import { withWalletLock } from './wallet-lock'
import { verifyImportedCredential } from './credential-verification'
import { MissingHolderKeysError, importHolderKeys, loadHolderKeys, signHolderJws, clearHolderKeys } from './key-store'
/**
 * Wallet facade — the holder-side API surface for the reference wallet.
 *
 * Phase 2.5 update: store() now handles both `vc+jwt` and `vc+sd-jwt`.
 * createPresentation() builds a plain VP-JWT for legacy `vc+jwt` credentials
 * and an SD-JWT VC presentation with KB-JWT for `vc+sd-jwt`.
 *
 * Shape deliberately mirrors the Kotlin `wallet-core-mp` capability interfaces
 * (CredentialStorage + CredentialPresentation + DidManagement) so that a future
 * TypeScript port of the Kotlin SDK can drop in as a direct replacement.
 */
import {
  generateEd25519KeyPair,
  publicKeyToDidKey,
  signJws,
  b64uEncode,
  b64uDecode,
  b64uDecodeString,
  signEd25519,
  b64uEncodeString,
} from './crypto'
import { decodeSdJwtVc, parseDisclosure } from './sdjwt'
import { sha256 } from '@noble/hashes/sha256'
import {
  unwrapClaimKey,
  decryptClaimJwe,
  isClaimJwePayload,
} from './claim-jwe'
import {
  loadHolder,
  saveHolder,
  saveCredentials,
  loadCredentials,
  upsertCredential,
  deleteCredential as deleteCredFromStorage,
  resetWallet as resetWalletStorage,
  type HolderIdentity,
  type StoredCredential,
} from './storage'
import { credentialDedupKey, credentialBusinessKey } from './credential-dedup'
import { assertCredentialBoundToHolder, isCredentialBoundToHolder } from './holder-binding'
import { randomUuid } from './uuid'

export interface WalletState {
  holder: HolderIdentity
  credentials: StoredCredential[]
}

let holderInitialization: Promise<HolderIdentity> | undefined

/** Bootstrap. Idempotent — generates a holder identity on first run. */
export async function bootstrap(): Promise<WalletState> {
  const holder = await (holderInitialization ??= withWalletLock(loadOrCreateHolder).finally(() => { holderInitialization = undefined }))
  // Never delete user data merely by opening the wallet; selection enforces holder binding.
  return { holder, credentials: loadCredentials() }
}

async function loadOrCreateHolder(): Promise<HolderIdentity> {
  // Validate credentials before migrating keys or writing holder metadata.
  loadCredentials()
  const existing = loadHolder()
  if (existing) {
    const derivedDid = publicKeyToDidKey(b64uDecode(existing.publicKey))
    if (derivedDid !== existing.did) {
      throw new Error('Wallet identity is corrupted. Reset wallet and start again.')
    }
    const legacySeed = (existing as HolderIdentity & { privateKey?: string }).privateKey
    if (legacySeed) {
      await importHolderKeys(existing.did, b64uDecode(legacySeed))
      saveHolder(existing)
    } else {
      await loadHolderKeys(existing.did)
    }
    return { did: existing.did, publicKey: existing.publicKey, createdAt: existing.createdAt }
  }

  if (loadCredentials().length > 0) {
    throw new Error(
      'Wallet identity was lost but credentials remain. Reset wallet, then Add → scan issuer QR again.',
    )
  }

  const keyPair = generateEd25519KeyPair()
  const holder: HolderIdentity = {
    did: publicKeyToDidKey(keyPair.publicKey),
    publicKey: b64uEncode(keyPair.publicKey),
    createdAt: new Date().toISOString(),
  }
  await importHolderKeys(holder.did, keyPair.privateKey)
  saveHolder(holder)
  return holder
}

/** Only an absent key qualifies; storage errors and malformed metadata must not rotate identity. */
export async function canReplaceLostKey(): Promise<boolean> {
  const holder = loadHolder()
  if (!holder || (holder as HolderIdentity & { privateKey?: string }).privateKey) return false
  if (publicKeyToDidKey(b64uDecode(holder.publicKey)) !== holder.did) return false
  loadCredentials()
  try { await loadHolderKeys(holder.did); return false }
  catch (error) { if (error instanceof MissingHolderKeysError) return true; throw error }
}

export async function replaceLostKey(): Promise<WalletState> {
  return withWalletLock(async () => {
    if (!await canReplaceLostKey()) throw new Error('Replacement is available only when this wallet key is missing.')
    const credentials = loadCredentials()
    const pair = generateEd25519KeyPair()
    const holder: HolderIdentity = { did: publicKeyToDidKey(pair.publicKey), publicKey: b64uEncode(pair.publicKey), createdAt: new Date().toISOString() }
    // Persist keys first, then atomically replace one metadata entry. Never delete old credentials.
    // If the metadata write fails, the old identity/data remain; an unused key may remain in IndexedDB.
    await importHolderKeys(holder.did, pair.privateKey)
    saveHolder(holder)
    return { holder, credentials }
  })
}

function pruneCredentialsNotBoundToHolder(holderDid: string): void {
  for (const cred of loadCredentials()) {
    if (!isCredentialBoundToHolder(cred, holderDid)) {
      deleteCredFromStorage(cred.id)
    }
  }
}

export interface StoreResult {
  credential: StoredCredential
  /** True when an existing wallet record was updated instead of creating a new one. */
  replaced: boolean
}

/**
 * Store a received credential. Accepts either VC-JWT or SD-JWT VC.
 *
 * Re-scanning or re-receiving the same logical credential (same issuer, subject, type,
 * and identifying claims) updates the existing record instead of adding a duplicate.
 *
 * @param credential the credential string (compact JWS or SD-JWT VC compact form)
 * @param format media type identifier
 * @param selectivelyDisclosable for SD-JWT VC, the issuer-declared list of
 *   selectively-disclosable claim names; ignored for VC-JWT
 */
export async function store(
  credential: string,
  format: StoredCredential['format'],
  selectivelyDisclosable: string[] = [],
): Promise<StoreResult> {
  return withWalletLock(() => {
  const holder = loadHolder()
  if (!holder) throw new Error("Open the wallet before importing a credential")
  verifyImportedCredential(credential, format)
  const meta = format === 'vc+sd-jwt'
    ? extractSdJwtMeta(credential)
    : extractVcJwtMeta(credential)
  const cred: StoredCredential = {
    id: randomUuid(),
    format,
    credential,
    receivedAt: new Date().toISOString(),
    issuerDid: meta.issuerDid,
    subjectDid: meta.subjectDid,
    type: meta.types,
    preview: meta.preview,
    selectivelyDisclosable,
  }
  if (!isCredentialBoundToHolder(cred, holder.did)) throw new Error("Credential was not issued to this wallet")
  const result = upsertCredential(cred)
  pruneStaleCredentialsForBusinessIdentity(cred)
  // Never delete user data merely by opening the wallet; selection enforces holder binding.
  if (!isCredentialBoundToHolder(result.credential, holder.did)) {
    deleteCredFromStorage(result.credential.id)
    throw new Error('Credential was not issued to this wallet. Scan the issuer QR again.')
  }
  return result
  })
}

/** Drop older copies of the same credential issued to a previous wallet identity. */
/** Restore verified credentials only; identity and non-extractable keys are never replaced. */
export async function restoreCredentials(backup: string): Promise<{ added: number; skipped: number }> {
  if (new TextEncoder().encode(backup).byteLength > 5 * 1024 * 1024) throw new Error('Backup exceeds the 5 MB limit')
  const data = JSON.parse(backup)
  if (!data || ![null, '1', '2'].includes(data.version) || typeof data.credentials !== 'string' || typeof data.holder?.did !== 'string') {
    throw new Error('Unsupported backup. Use a supported credential export from this wallet.')
  }
  const records: unknown = JSON.parse(data.credentials)
  if (!Array.isArray(records) || records.length > 500) throw new Error('A backup must contain at most 500 credentials')
  return withWalletLock(async () => {
    const holder = loadHolder()
    if (!holder || holder.did !== data.holder.did) throw new Error('This backup belongs to a different wallet identity. Ask the issuer to reissue credentials to this wallet.')
    await loadHolderKeys(holder.did)
    const existing = loadCredentials()
    const seen = new Set(existing.map(credentialDedupKey))
    const additions: StoredCredential[] = []
    let skipped = 0
    for (const raw of records) {
      const record = raw && typeof raw === 'object' ? { ...raw, credential: raw.credential ?? raw.vcJwt, format: raw.format ?? 'vc+jwt' } : raw;
      if (!record || typeof record.credential !== 'string' || !['vc+jwt', 'vc+sd-jwt'].includes(record.format)) throw new Error('Invalid credential in backup. Nothing was restored.')
      // Never trust labels, holder fields or disclosure hints from the backup envelope.
      verifyImportedCredential(record.credential, record.format)
      const meta = record.format === 'vc+sd-jwt' ? extractSdJwtMeta(record.credential) : extractVcJwtMeta(record.credential)
      const credential: StoredCredential = {
        id: randomUuid(), format: record.format, credential: record.credential,
        receivedAt: new Date().toISOString(), issuerDid: meta.issuerDid,
        subjectDid: meta.subjectDid, type: meta.types, preview: meta.preview,
        selectivelyDisclosable: record.format === 'vc+sd-jwt' ? decodeSdJwtVc(record.credential).disclosures.map(d => d.name) : [],
      }
      if (!isCredentialBoundToHolder(credential, holder.did)) throw new Error("Credential was not issued to this wallet. Nothing was restored.")
      const key = credentialDedupKey(credential)
      if (seen.has(key)) { skipped++; continue }
      seen.add(key)
      additions.push(credential)
    }
    // One storage write, after every signature and holder check; existing records win duplicates.
    if (additions.length) saveCredentials([...existing, ...additions])
    return { added: additions.length, skipped }
  })
}

function pruneStaleCredentialsForBusinessIdentity(latest: StoredCredential): void {
  const businessKey = credentialBusinessKey(latest)
  for (const existing of loadCredentials()) {
    if (existing.id === latest.id) continue
    if (
      credentialBusinessKey(existing) === businessKey &&
      existing.subjectDid !== latest.subjectDid
    ) {
      deleteCredFromStorage(existing.id)
    }
  }
}

export function list(): StoredCredential[] {
  return loadCredentials()
}

export async function deleteCredential(id: string): Promise<void> {
  await withWalletLock(() => deleteCredFromStorage(id))
}

export async function resetWallet(): Promise<{ keysCleared: boolean }> {
  return withWalletLock(async () => {
    let keysCleared = true
    try { await clearHolderKeys() } catch { keysCleared = false }
    resetWalletStorage()
    return { keysCleared }
  })
}

/**
 * Build a presentation containing one or more credentials.
 *
 * For an all-VC-JWT presentation: builds a single VP-JWT (legacy path,
 * unchanged from Phase 1).
 *
 * For an SD-JWT VC presentation: returns the SD-JWT VC compact form with
 * only the chosen disclosures, plus a key-binding JWT signed by the holder.
 * MUST be exactly one credential per presentation (SD-JWT VC presentations
 * are not composable in the IETF draft — a multi-credential presentation
 * needs OID4VP envelope which is out of scope for the demo).
 *
 * @param credentialIds local wallet IDs of credentials to include
 * @param verifierUri the verifier's identifier (audience binding)
 * @param challenge nonce from the verifier (replay protection)
 * @param disclose for SD-JWT VC: names of claims to disclose. If empty,
 *   discloses nothing — verifier sees only the credential type + issuer + holder
 *   binding. Ignored for VC-JWT presentations (all claims always disclosed).
 */
export async function createPresentation(
  credentialIds: string[],
  verifierUri: string,
  challenge: string,
  disclose: string[] = [],
): Promise<string> {
  return withWalletLock(async () => {
  const holder = loadHolder()
  if (!holder) throw new Error('Wallet not bootstrapped')
  const creds = loadCredentials().filter((c) => credentialIds.includes(c.id))
  if (credentialIds.length === 0 || new Set(credentialIds).size !== credentialIds.length || creds.length !== credentialIds.length)
    throw new Error('Select existing credentials exactly once')
  if (creds.length > 1 && creds.some(credential => credential.format === 'vc+sd-jwt'))
    throw new Error('Share SD-JWT credentials one at a time; multiple selective-disclosure credentials are not supported')
  for (const credential of creds) {
    verifyImportedCredential(credential.credential, credential.format)
    if (!isCredentialBoundToHolder(credential, holder.did)) throw new Error('Credential belongs to another holder')
  }

  const now = Math.floor(Date.now() / 1000)

  // SD-JWT VC: spec-compliant single-credential path with KB-JWT.
  if (creds.length === 1 && creds[0].format === 'vc+sd-jwt') {
    assertCredentialBoundToHolder(creds[0].credential, creds[0].format, holder.did)
    return presentSdJwtVcWithDecryption({
      sdJwtVc: creds[0].credential,
      selectDisclose: disclose,
      holderDid: holder.did,
      audience: verifierUri,
      nonce: challenge,
      now,
    })
  }

  // Legacy VC-JWT VP path — discloses all claims, no selective disclosure.
  const payload = {
    iss: holder.did,
    sub: holder.did,
    aud: verifierUri,
    nonce: challenge,
    iat: now,
    exp: now + 300,
    vp: {
      '@context': ['https://www.w3.org/ns/credentials/v2'],
      type: ['VerifiablePresentation'],
      holder: holder.did,
      verifiableCredential: creds.map((c) => c.credential),
    },
  }
  return signHolderJws(payload, holder.did)
  })
}

/** Preserve issuer disclosures; selected encrypted claims carry a content key in the signed KB-JWT extension. */
async function presentSdJwtVcWithDecryption(args: {
  sdJwtVc: string
  selectDisclose: string[]
  holderDid: string
  audience: string
  nonce: string
  now: number
}): Promise<string> {
  const parts = args.sdJwtVc.split('~').filter((p) => p.length > 0)
  if (parts.length < 1) throw new Error('Empty SD-JWT VC')
  const issuerJwt = parts[0]
  const allDisclosures = parts.slice(1)

  const selected: string[] = []
  const claimKeys: Record<string, string> = {}
  for (const d of allDisclosures) {
    const [, name, value] = parseDisclosure(d)
    if (!args.selectDisclose.includes(name)) continue
    if (isClaimJwePayload(value)) {
      const key = await unwrapClaimKey(value, args.holderDid)
      try { claimKeys[b64uEncode(sha256(new TextEncoder().encode(d)))] = b64uEncode(key) }
      finally { key.fill(0) }
      selected.push(d) // Preserve the exact issuer-committed salt, name, and ciphertext.
    } else {
      selected.push(d)
    }
  }

  const prefix = [issuerJwt, ...selected, ''].join('~')
  const sdHash = b64uEncode(sha256(new TextEncoder().encode(prefix)))

  const kbPayload = {
    iat: args.now,
    aud: args.audience,
    nonce: args.nonce,
    sd_hash: sdHash,
    ...(Object.keys(claimKeys).length ? { trustweave_claim_keys: claimKeys } : {}),
  }
  const kbJwt = await signHolderJws(kbPayload, args.holderDid, 'kb+jwt')

  return prefix + kbJwt
}

// ----- internal helpers -----

interface CredentialMeta {
  issuerDid: string
  subjectDid: string
  types: string[]
  preview: StoredCredential['preview']
}

function extractVcJwtMeta(vcJwt: string): CredentialMeta {
  const parts = vcJwt.split('.')
  if (parts.length !== 3) throw new Error('VC-JWT must have three parts')
  const payload = JSON.parse(b64uDecodeString(parts[1])) as Record<string, unknown>
  const vc = payload.vc as Record<string, unknown> | undefined
  const issuerDid = String(payload.iss ?? vc?.issuer ?? '')
  const subjectDid = String(payload.sub ?? '')
  const t = vc?.type
  const types = Array.isArray(t) ? t.map(String) : t ? [String(t)] : ['VerifiableCredential']
  const subject = (vc?.credentialSubject ?? {}) as Record<string, unknown>
  const credentialType = types.find((x) => x !== 'VerifiableCredential') ?? 'Credential'
  const subtitle = String(
    subject.name ?? subject.degree ?? subject.title ?? '',
  ) || undefined
  return { issuerDid, subjectDid, types, preview: { title: credentialType, subtitle } }
}

function extractSdJwtMeta(sdJwtVc: string): CredentialMeta {
  const decoded = decodeSdJwtVc(sdJwtVc)
  const issuerDid = String(decoded.issuerPayload.iss ?? '')
  const subjectDid = String(decoded.issuerPayload.sub ?? '')
  const vct = String(decoded.issuerPayload.vct ?? 'Credential')

  // Build a friendly subtitle from any disclosure we can see (we have all of them
  // at storage time because the issuer just sent them all to us). At presentation
  // time the verifier may only see a subset.
  const disclosureMap: Record<string, unknown> = {}
  for (const d of decoded.disclosures) disclosureMap[d.name] = d.value
  if (decoded.issuerPayload.registrationNumber) {
    disclosureMap.registrationNumber = decoded.issuerPayload.registrationNumber
  }
  if (decoded.issuerPayload.personnelId) {
    disclosureMap.personnelId = decoded.issuerPayload.personnelId
  }
  const title = vct === 'ActivityAuthorizationCredential' && disclosureMap.callsign
    ? `Airspace: ${disclosureMap.callsign}`
    : vct === 'DroneIdentificationCredential'
      ? `FAA: ${disclosureMap.registrationNumber ?? disclosureMap.callsign ?? 'Drone ID'}`
      : vct === 'CommonAccessCardCredential'
        ? `CAC: ${disclosureMap.name ?? disclosureMap.personnelId ?? 'Personnel'}`
        : vct
  const subtitle = String(
    disclosureMap.callsign ?? disclosureMap.name ?? disclosureMap.degree ?? disclosureMap.droneId
      ?? disclosureMap.rank
      ?? (disclosureMap.make && disclosureMap.model ? `${disclosureMap.make} ${disclosureMap.model}` : '')
      ?? '',
  ) || undefined

  return {
    issuerDid,
    subjectDid,
    types: [vct],
    preview: { title, subtitle },
  }
}

// Re-export storage types for consumers.
export type { HolderIdentity, StoredCredential }
