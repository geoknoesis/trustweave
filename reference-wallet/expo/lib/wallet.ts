/**
 * Wallet facade — Expo build. Mirrors the web/Android/iOS facades but with async
 * methods because expo-secure-store is async.
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
import { assertCredentialBoundToHolder, isCredentialBoundToHolder } from './holderBinding'
import { decodeSdJwtVc, presentSdJwtVc } from './sdjwt'
import {
  loadHolder,
  saveHolder,
  loadCredentials,
  upsertCredential,
  deleteCredential as deleteCredFromStorage,
  resetWallet as resetStorage,
  type HolderIdentity,
  type StoredCredential,
} from './storage'
import * as ExpoCrypto from 'expo-crypto'

export interface WalletState {
  holder: HolderIdentity
  credentials: StoredCredential[]
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
      throw new Error('Wallet identity is corrupted. Reset wallet and start again.')
    }
    return existing
  }

  const creds = await loadCredentials()
  if (creds.length > 0) {
    throw new Error(
      'Wallet identity was lost but credentials remain. Reset wallet, then Add → scan issuer QR again.',
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
): Promise<StoreResult> {
  const holder = await loadOrCreateHolder()
  const meta = extractMeta(credential, format)
  return persistCredential(holder.did, credential, format, meta, selectivelyDisclosable)
}

/** Store a credential returned from a multi-protocol offer claim (includes VC-LD from SaaS). */
export async function storeFromClaim(claim: {
  format: StoredCredential['format']
  credential: string
  issuer: string
  subjectDid?: string
  type?: string[]
  selectivelyDisclosable?: string[]
  preview?: { title: string; subtitle?: string }
}): Promise<StoreResult> {
  const holder = await loadOrCreateHolder()
  const meta: Meta = {
    issuerDid: claim.issuer,
    subjectDid: claim.subjectDid ?? holder.did,
    types: claim.type ?? ['VerifiableCredential'],
    preview: claim.preview ?? { title: claim.type?.[1] ?? 'Credential' },
  }
  return persistCredential(
    holder.did,
    claim.credential,
    claim.format,
    meta,
    claim.selectivelyDisclosable ?? [],
  )
}

async function persistCredential(
  holderDid: string,
  credential: string,
  format: StoredCredential['format'],
  meta: Meta,
  selectivelyDisclosable: string[],
): Promise<StoreResult> {
  const cred: StoredCredential = {
    id: ExpoCrypto.randomUUID(),
    format,
    credential,
    receivedAt: new Date().toISOString(),
    issuerDid: meta.issuerDid,
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
    throw new Error('Credential was not issued to this wallet. Scan the issuer QR again.')
  }
  return result
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
  await deleteCredFromStorage(id)
}

export async function resetWallet(): Promise<void> {
  await resetStorage()
}

export async function createPresentation(
  credentialIds: string[],
  verifierUri: string,
  challenge: string,
  disclose: string[] = [],
): Promise<string> {
  const holder = await loadHolder()
  if (!holder) throw new Error('Wallet not bootstrapped')
  const all = await loadCredentials()
  const creds = all.filter((c) => credentialIds.includes(c.id))
  if (creds.length === 0) throw new Error('No matching credentials to present')

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

export type { HolderIdentity, StoredCredential }
