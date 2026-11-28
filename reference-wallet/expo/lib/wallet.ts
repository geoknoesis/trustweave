/**
 * Wallet facade — Expo build. Mirrors the web/Android/iOS facades with:
 *  - Fail-closed cryptographic & issuer trust verification on import (parity with Android/Web)
 *  - Honest trust source tracking (never claims an unverified issuer is trusted)
 *  - Local activity log
 *  - Credential-only backup export & verified restore (NEVER exports private keys)
 */
import {
  generateEd25519KeyPair,
  publicKeyToDidKey,
  signJws,
  b64uEncode,
  b64uDecode,
  b64uDecodeString,
} from './crypto'
import { credentialBusinessKey } from './credentialDedup'
import { verifyImportedCredential } from './credentialVerification'
import { assertCredentialBoundToHolder, isCredentialBoundToHolder } from './holderBinding'
import {
  buildIssuerTrustPolicy,
  configuredTrustedIssuers,
  resolveIssuerTrustSource,
  UntrustedIssuerError,
  type IssuerTrustSource,
} from './issuerTrust'
import { decodeSdJwtVc, presentSdJwtVc } from './sdjwt'
import {
  loadHolder,
  saveHolder,
  loadCredentials,
  upsertCredential,
  deleteCredential as deleteCredFromStorage,
  resetWallet as resetStorage,
  loadAcceptedIssuers,
  addAcceptedIssuer,
  removeAcceptedIssuer as removeAcceptedIssuerFromStorage,
  loadActivity,
  appendActivity,
  clearActivity,
  loadPreferences,
  savePreferences,
  type ActivityEntry,
  type ActivityKind,
  type HolderIdentity,
  type StoredCredential,
  type WalletPreferences,
} from './storage'
import * as ExpoCrypto from 'expo-crypto'

export interface WalletState {
  holder: HolderIdentity
  credentials: StoredCredential[]
}

export interface StoreTrustOptions {
  /** Issuers belonging to the wallet's own configured backend (read from `/api/demo-issuer/identity`). */
  backendIssuers?: string[]
  /** Issuer DID explicitly confirmed by the user for this import. Persisted ONLY after all checks pass. */
  confirmedIssuer?: string | null
  /** Optional display name for the issuer (never used to bypass trust checks). */
  issuerName?: string
  /** Optional configured allow-list override (for tests). */
  configuredIssuers?: string[]
}

export async function bootstrap(): Promise<WalletState> {
  const holder = await loadOrCreateHolder()
  await pruneCredentialsNotBoundToHolder(holder.did)
  return { holder, credentials: await loadCredentials() }
}

/** Load holder from secure storage, or create one only when the wallet is empty. */
async function loadOrCreateHolder(): Promise<HolderIdentity> {
  const existing = await loadHolder()
  if (existing) {
    const derivedDid = publicKeyToDidKey(b64uDecode(existing.publicKey))
    if (derivedDid !== existing.did) {
      throw new Error('Your wallet key data could not be verified. Reset your wallet in Settings to start fresh.')
    }
    return existing
  }

  const creds = await loadCredentials()
  if (creds.length > 0) {
    throw new Error(
      'Your device signing key is missing while saved credentials remain. Reset your wallet in Settings, then ask issuers to reissue your credentials.',
    )
  }

  const kp = generateEd25519KeyPair()
  const holder: HolderIdentity = {
    did: publicKeyToDidKey(kp.publicKey),
    publicKey: b64uEncode(kp.publicKey),
    privateKey: b64uEncode(kp.privateKey),
    createdAt: new Date().toISOString(),
  }
  await saveHolder(holder)
  return holder
}

/** Remove credentials issued to a previous wallet identity (e.g. after reset). */
async function pruneCredentialsNotBoundToHolder(holderDid: string): Promise<void> {
  const all = await loadCredentials()
  for (const cred of all) {
    if (!isCredentialBoundToHolder(cred, holderDid)) {
      await deleteCredFromStorage(cred.id)
    }
  }
}

export interface StoreResult {
  credential: StoredCredential
  replaced: boolean
}

export async function store(
  credential: string,
  format: StoredCredential['format'],
  selectivelyDisclosable: string[] = [],
  options: StoreTrustOptions = {},
): Promise<StoreResult> {
  const holder = await loadOrCreateHolder()
  const meta = extractMeta(credential, format)
  return persistVerifiedCredential(holder.did, credential, format, meta, selectivelyDisclosable, options)
}

/** Store a credential returned from a multi-protocol offer claim (includes VC-LD from SaaS). */
export async function storeFromClaim(
  claim: {
    format: StoredCredential['format']
    credential: string
    issuer: string
    subjectDid?: string
    type?: string[]
    selectivelyDisclosable?: string[]
    preview?: { title: string; subtitle?: string }
  },
  options: StoreTrustOptions = {},
): Promise<StoreResult> {
  const holder = await loadOrCreateHolder()
  const extracted = tryExtractMeta(claim.credential, claim.format)
  const meta: Meta = {
    issuerDid: extracted?.issuerDid || claim.issuer,
    subjectDid: extracted?.subjectDid || claim.subjectDid || holder.did,
    types: claim.type ?? extracted?.types ?? ['VerifiableCredential'],
    preview: claim.preview ?? extracted?.preview ?? { title: claim.type?.[1] ?? 'Credential' },
  }
  return persistVerifiedCredential(
    holder.did,
    claim.credential,
    claim.format,
    meta,
    claim.selectivelyDisclosable ?? [],
    options,
  )
}

async function persistVerifiedCredential(
  holderDid: string,
  credential: string,
  format: StoredCredential['format'],
  meta: Meta,
  selectivelyDisclosable: string[],
  options: StoreTrustOptions,
): Promise<StoreResult> {
  const configured = options.configuredIssuers ?? configuredTrustedIssuers()
  const backend = options.backendIssuers ?? []
  const accepted = await loadAcceptedIssuers()
  const policy = buildIssuerTrustPolicy({
    configured,
    backend,
    accepted,
    confirmedIssuer: options.confirmedIssuer,
  })

  // Fail-closed verification: signature, issuer trust, holder binding, validity window, SD-JWT digests.
  verifyImportedCredential(credential, format, {
    issuerPolicy: policy,
    holderDid,
    credentialTitle: meta.preview.title,
  })

  const trustSource: IssuerTrustSource =
    format === 'vc-ld'
      ? resolveIssuerTrustSource(meta.issuerDid, {
          configured,
          backend,
          accepted,
          confirmedIssuer: options.confirmedIssuer,
        })
      : resolveIssuerTrustSource(meta.issuerDid, {
          configured,
          backend,
          accepted,
          confirmedIssuer: options.confirmedIssuer,
        })

  const cred: StoredCredential = {
    id: ExpoCrypto.randomUUID(),
    format,
    credential,
    receivedAt: new Date().toISOString(),
    issuerDid: meta.issuerDid,
    issuerName: options.issuerName,
    trustSource,
    subjectDid: meta.subjectDid,
    type: meta.types,
    preview: meta.preview,
    selectivelyDisclosable,
  }
  const result = await upsertCredential(cred)
  await pruneStaleCredentialsForBusinessIdentity(cred)
  await pruneCredentialsNotBoundToHolder(holderDid)

  if (!isCredentialBoundToHolder(result.credential, holderDid)) {
    await deleteCredFromStorage(result.credential.id)
    throw new Error('This credential was issued to a different wallet. Ask the issuer for a new QR code and scan again.')
  }

  // Persist user-confirmed issuer trust ONLY now that every check passed and the credential was stored.
  const basePolicy = buildIssuerTrustPolicy({ configured, backend, accepted })
  if (
    options.confirmedIssuer &&
    options.confirmedIssuer === meta.issuerDid &&
    !basePolicy.isTrusted(options.confirmedIssuer)
  ) {
    await addAcceptedIssuer(options.confirmedIssuer)
    await recordWalletActivity({
      kind: 'trusted_issuer',
      title: 'Trusted new issuer',
      subtitle: meta.preview.title,
      counterparty: options.issuerName ?? meta.issuerDid,
      status: 'success',
    })
  }

  await recordWalletActivity({
    kind: result.replaced ? 'updated' : 'added',
    title: meta.preview.title,
    subtitle: meta.preview.subtitle,
    counterparty: options.issuerName ?? meta.issuerDid,
    status: 'success',
  })

  return result
}

function tryExtractMeta(credential: string, format: StoredCredential['format']): Meta | null {
  try {
    return extractMeta(credential, format)
  } catch {
    return null
  }
}

function extractMeta(credential: string, format: StoredCredential['format']): Meta {
  if (format === 'vc+sd-jwt') return extractSdJwtMeta(credential)
  if (format === 'vc-ld') return extractVcLdMeta(credential)
  return extractVcJwtMeta(credential)
}

async function pruneStaleCredentialsForBusinessIdentity(latest: StoredCredential): Promise<void> {
  const businessKey = credentialBusinessKey(latest)
  const all = await loadCredentials()
  for (const existing of all) {
    if (existing.id === latest.id) continue
    if (
      credentialBusinessKey(existing) === businessKey &&
      existing.subjectDid !== latest.subjectDid
    ) {
      await deleteCredFromStorage(existing.id)
    }
  }
}

export async function list(): Promise<StoredCredential[]> {
  return loadCredentials()
}

export async function deleteCredential(id: string): Promise<void> {
  const existing = (await loadCredentials()).find((c) => c.id === id)
  await deleteCredFromStorage(id)
  if (existing) {
    await recordWalletActivity({
      kind: 'removed',
      title: existing.preview.title,
      subtitle: existing.preview.subtitle,
      status: 'success',
    })
  }
}

export async function resetWallet(): Promise<void> {
  await resetStorage()
}

export async function acceptedIssuers(): Promise<string[]> {
  return loadAcceptedIssuers()
}

export async function removeAcceptedIssuer(did: string): Promise<string[]> {
  return removeAcceptedIssuerFromStorage(did)
}

export async function reviewableIssuers(): Promise<Array<{ did: string; source: 'configured' | 'accepted' }>> {
  const configured = configuredTrustedIssuers()
  const accepted = (await loadAcceptedIssuers()).filter((did) => !configured.includes(did))
  return [
    ...configured.map((did) => ({ did, source: 'configured' as const })),
    ...accepted.map((did) => ({ did, source: 'accepted' as const })),
  ]
}

export async function recordWalletActivity(entry: {
  kind: ActivityKind
  title: string
  subtitle?: string
  counterparty?: string
  detailsShared?: number
  totalDetails?: number
  status: 'success' | 'failed'
}): Promise<void> {
  await appendActivity({
    id: ExpoCrypto.randomUUID(),
    timestamp: new Date().toISOString(),
    ...entry,
  })
}

export async function getActivityLog(): Promise<ActivityEntry[]> {
  return loadActivity()
}

export async function clearActivityLog(): Promise<void> {
  await clearActivity()
}

export async function getPreferences(): Promise<WalletPreferences> {
  return loadPreferences()
}

export async function updatePreferences(patch: Partial<WalletPreferences>): Promise<WalletPreferences> {
  return savePreferences(patch)
}

/**
 * Check whether a stored credential's issuer is still trusted (so removing an issuer
 * from Settings -> Trusted Issuers prevents presenting that issuer's credentials,
 * matching README/Android/Web trust policy).
 */
export async function isCredentialIssuerCurrentlyTrusted(
  cred: Pick<StoredCredential, 'issuerDid' | 'trustSource'>,
  backendIssuers: string[] = [],
): Promise<boolean> {
  const configured = configuredTrustedIssuers()
  const accepted = await loadAcceptedIssuers()
  const policy = buildIssuerTrustPolicy({ configured, backend: backendIssuers, accepted })
  if (policy.isTrusted(cred.issuerDid)) return true
  // Credentials previously verified via backend may be presented offline if their trustSource was 'backend'
  // and backend isn't reachable right now; configured/accepted removals always take effect.
  return cred.trustSource === 'backend' && backendIssuers.length === 0
}

export async function createPresentation(
  credentialIds: string[],
  verifierUri: string,
  challenge: string,
  disclose: string[] = [],
  options: { backendIssuers?: string[]; verifierLabel?: string } = {},
): Promise<string> {
  const holder = await loadHolder()
  if (!holder) throw new Error('Wallet is not ready yet. Please try again.')
  const all = await loadCredentials()
  const creds = all.filter((c) => credentialIds.includes(c.id))
  if (creds.length === 0) throw new Error('No matching credentials found to share.')

  for (const c of creds) {
    const trusted = await isCredentialIssuerCurrentlyTrusted(c, options.backendIssuers ?? [])
    if (!trusted) {
      throw new Error(
        'This credential’s issuer was removed from your trusted issuers list and can no longer be shared.',
      )
    }
  }

  const privateKey = b64uDecode(holder.privateKey)
  const now = Math.floor(Date.now() / 1000)

  if (creds.length === 1 && creds[0].format === 'vc+sd-jwt') {
    assertCredentialBoundToHolder(creds[0].credential, creds[0].format, holder.did)
    return presentSdJwtVc({
      sdJwtVc: creds[0].credential,
      selectDisclose: disclose,
      holderPrivateKey: privateKey,
      holderDid: holder.did,
      audience: verifierUri,
      nonce: challenge,
      now,
    })
  }

  // Legacy VP-JWT path.
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
  return signJws(payload, privateKey, `${holder.did}#${holder.did.slice('did:key:'.length)}`)
}

// ----- Credential-only Backup & Restore (NEVER includes private keys) -----

export interface CredentialBackupPayload {
  schema: 'trustweave-credential-backup-v1'
  exportedAt: string
  holderDid: string
  containsPrivateKeys: false
  credentials: Array<{
    format: StoredCredential['format']
    credential: string
    selectivelyDisclosable: string[]
    issuerName?: string
  }>
}

/** Export credential data ONLY (no private key material). */
export async function exportCredentialBackup(): Promise<string> {
  const state = await bootstrap()
  const payload: CredentialBackupPayload = {
    schema: 'trustweave-credential-backup-v1',
    exportedAt: new Date().toISOString(),
    holderDid: state.holder.did,
    containsPrivateKeys: false,
    credentials: state.credentials.map((c) => ({
      format: c.format,
      credential: c.credential,
      selectivelyDisclosable: c.selectivelyDisclosable,
      issuerName: c.issuerName,
    })),
  }
  await recordWalletActivity({
    kind: 'backup',
    title: 'Exported credential backup',
    subtitle: `${payload.credentials.length} credential${payload.credentials.length === 1 ? '' : 's'} (no private keys included)`,
    status: 'success',
  })
  return JSON.stringify(payload, null, 2)
}

export interface RestoreBackupResult {
  restored: number
  skipped: number
  untrustedIssuers: string[]
}

/**
 * Restore credentials from a credential-only backup.
 * Enforces matching holder identity + full fail-closed cryptographic & issuer trust verification.
 */
export async function restoreCredentialBackup(
  rawJson: string,
  options: StoreTrustOptions = {},
): Promise<RestoreBackupResult> {
  const state = await bootstrap()
  let parsed: Partial<CredentialBackupPayload>
  try {
    parsed = JSON.parse(rawJson) as Partial<CredentialBackupPayload>
  } catch {
    throw new Error('Backup text is not valid JSON.')
  }
  if (parsed.schema !== 'trustweave-credential-backup-v1' || !Array.isArray(parsed.credentials)) {
    throw new Error('Unrecognized backup format.')
  }
  if (parsed.holderDid && parsed.holderDid !== state.holder.did) {
    throw new Error(
      'This backup belongs to a different wallet key. Because backups do not contain private keys, ask your issuers to reissue credentials for this device.',
    )
  }

  let restored = 0
  let skipped = 0
  const untrustedIssuers = new Set<string>()

  for (const item of parsed.credentials) {
    try {
      await store(item.credential, item.format, item.selectivelyDisclosable ?? [], {
        ...options,
        issuerName: item.issuerName,
      })
      restored += 1
    } catch (e) {
      if (e instanceof UntrustedIssuerError) {
        untrustedIssuers.add(e.issuerDid)
      }
      skipped += 1
    }
  }

  if (restored > 0) {
    await recordWalletActivity({
      kind: 'restored',
      title: 'Restored credential backup',
      subtitle: `${restored} credential${restored === 1 ? '' : 's'} verified and restored`,
      status: 'success',
    })
  }

  return { restored, skipped, untrustedIssuers: [...untrustedIssuers] }
}

// ----- meta extraction -----

interface Meta {
  issuerDid: string
  subjectDid: string
  types: string[]
  preview: StoredCredential['preview']
}

function extractVcJwtMeta(vcJwt: string): Meta {
  const parts = vcJwt.split('.')
  if (parts.length !== 3) throw new Error('VC-JWT must have three parts')
  const payload = JSON.parse(b64uDecodeString(parts[1])) as Record<string, unknown>
  const vc = payload.vc as Record<string, unknown> | undefined
  const issuerDid = String(payload.iss ?? vc?.issuer ?? '')
  const subjectDid = String(payload.sub ?? '')
  const t = vc?.type
  const types = Array.isArray(t) ? t.map(String) : t ? [String(t)] : ['VerifiableCredential']
  const subject = (vc?.credentialSubject ?? {}) as Record<string, unknown>
  const title = types.find((x) => x !== 'VerifiableCredential') ?? 'Credential'
  const subtitle = String(subject.name ?? subject.degree ?? subject.title ?? '') || undefined
  return { issuerDid, subjectDid, types, preview: { title, subtitle } }
}

function extractSdJwtMeta(sdJwtVc: string): Meta {
  const decoded = decodeSdJwtVc(sdJwtVc)
  const issuerDid = String(decoded.issuerPayload.iss ?? '')
  const subjectDid = String(decoded.issuerPayload.sub ?? '')
  const vct = String(decoded.issuerPayload.vct ?? 'Credential')
  const map: Record<string, unknown> = {}
  for (const d of decoded.disclosures) map[d.name] = d.value
  const subtitle = String(map.name ?? map.degree ?? map.title ?? '') || undefined
  return { issuerDid, subjectDid, types: [vct], preview: { title: vct, subtitle } }
}

function extractVcLdMeta(rawJson: string): Meta {
  const parsed = JSON.parse(rawJson) as Record<string, unknown>
  const issuer = parsed.issuer
  const issuerDid = typeof issuer === 'string' ? issuer : String((issuer as { id?: string })?.id ?? '')
  const subject = parsed.credentialSubject as Record<string, unknown> | undefined
  const subjectDid = String(subject?.id ?? '')
  const t = parsed.type
  const types = Array.isArray(t) ? t.map(String) : t ? [String(t)] : ['VerifiableCredential']
  const title = types.find((x) => x !== 'VerifiableCredential') ?? 'Credential'
  const subtitle = String(subject?.name ?? subject?.degree ?? subject?.title ?? '') || undefined
  return { issuerDid, subjectDid, types, preview: { title, subtitle } }
}

export { UntrustedIssuerError }
export type { ActivityEntry, ActivityKind, HolderIdentity, StoredCredential, WalletPreferences }
