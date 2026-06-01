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
  buildPlaintextDisclosure,
  decryptClaimJwe,
  isClaimJwePayload,
} from './claim-jwe'
import {
  loadHolder,
  saveHolder,
  loadCredentials,
  upsertCredential,
  deleteCredential as deleteCredFromStorage,
  resetWallet as resetWalletStorage,
  type HolderIdentity,
  type StoredCredential,
} from './storage'
import { credentialBusinessKey } from './credential-dedup'
import { assertCredentialBoundToHolder, isCredentialBoundToHolder } from './holder-binding'
import { randomUuid } from './uuid'

export interface WalletState {
  holder: HolderIdentity
  credentials: StoredCredential[]
}

/** Bootstrap. Idempotent — generates a holder identity on first run. */
export function bootstrap(): WalletState {
  const holder = loadOrCreateHolder()
  pruneCredentialsNotBoundToHolder(holder.did)
  return { holder, credentials: loadCredentials() }
}

function loadOrCreateHolder(): HolderIdentity {
  const existing = loadHolder()
  if (existing) {
    const derivedDid = publicKeyToDidKey(b64uDecode(existing.publicKey))
    if (derivedDid !== existing.did) {
      throw new Error('Wallet identity is corrupted. Reset wallet and start again.')
    }
    return existing
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
    privateKey: b64uEncode(keyPair.privateKey),
    createdAt: new Date().toISOString(),
  }
  saveHolder(holder)
  return holder
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
export function store(
  credential: string,
  format: StoredCredential['format'],
  selectivelyDisclosable: string[] = [],
): StoreResult {
  const holder = loadOrCreateHolder()
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
  const result = upsertCredential(cred)
  pruneStaleCredentialsForBusinessIdentity(cred)
  pruneCredentialsNotBoundToHolder(holder.did)
  if (!isCredentialBoundToHolder(result.credential, holder.did)) {
    deleteCredFromStorage(result.credential.id)
    throw new Error('Credential was not issued to this wallet. Scan the issuer QR again.')
  }
  return result
}

/** Drop older copies of the same credential issued to a previous wallet identity. */
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

export function deleteCredential(id: string): void {
  deleteCredFromStorage(id)
}

export function resetWallet(): void {
  resetWalletStorage()
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
  const holder = loadHolder()
  if (!holder) throw new Error('Wallet not bootstrapped')
  const creds = loadCredentials().filter((c) => credentialIds.includes(c.id))
  if (creds.length === 0) throw new Error('No matching credentials to present')

  const privateKey = b64uDecode(holder.privateKey)
  const now = Math.floor(Date.now() / 1000)

  // SD-JWT VC: spec-compliant single-credential path with KB-JWT.
  if (creds.length === 1 && creds[0].format === 'vc+sd-jwt') {
    assertCredentialBoundToHolder(creds[0].credential, creds[0].format, holder.did)
    return presentSdJwtVcWithDecryption({
      sdJwtVc: creds[0].credential,
      selectDisclose: disclose,
      holderPrivateKey: privateKey,
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
  return signJws(payload, privateKey, `${holder.did}#${holder.did.slice('did:key:'.length)}`)
}

/** Present SD-JWT VC; decrypts JWE claim values to plaintext when sharing (Option A). */
async function presentSdJwtVcWithDecryption(args: {
  sdJwtVc: string
  selectDisclose: string[]
  holderPrivateKey: Uint8Array
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
  for (const d of allDisclosures) {
    const [, name, value] = parseDisclosure(d)
    if (!args.selectDisclose.includes(name)) continue
    if (isClaimJwePayload(value)) {
      const plaintext = await decryptClaimJwe(value, args.holderPrivateKey)
      selected.push(buildPlaintextDisclosure(name, plaintext))
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
  }
  const header = { alg: 'EdDSA', typ: 'kb+jwt', kid: args.holderDid }
  const encodedHeader = b64uEncodeString(JSON.stringify(header))
  const encodedPayload = b64uEncodeString(JSON.stringify(kbPayload))
  const signingInput = `${encodedHeader}.${encodedPayload}`
  const signature = signEd25519(signingInput, args.holderPrivateKey)
  const kbJwt = `${signingInput}.${b64uEncode(signature)}`

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
