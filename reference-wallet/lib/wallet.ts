/**
 * Wallet facade — the holder-side API surface for the reference wallet.
 *
 * Shape deliberately mirrors the Kotlin `wallet-core-mp` capability interfaces
 * (CredentialStorage + CredentialPresentation + DidManagement) so that a future
 * TypeScript port of the Kotlin SDK can drop in as a direct replacement.
 */
import {
  generateEd25519KeyPair,
  publicKeyToDidKey,
  signJws,
  parseJwsHeader,
  b64uEncode,
  b64uDecode,
} from './crypto'
import {
  loadHolder,
  saveHolder,
  loadCredentials,
  addCredential,
  deleteCredential as deleteCredFromStorage,
  resetWallet as resetWalletStorage,
  type HolderIdentity,
  type StoredCredential,
} from './storage'

/** Result of bootstrapping the wallet on first use. */
export interface WalletState {
  holder: HolderIdentity
  credentials: StoredCredential[]
}

/**
 * Bootstrap the wallet. Idempotent — generates a holder identity on first run,
 * loads it from storage on subsequent runs.
 */
export function bootstrap(): WalletState {
  let holder = loadHolder()
  if (!holder) {
    const keyPair = generateEd25519KeyPair()
    holder = {
      did: publicKeyToDidKey(keyPair.publicKey),
      publicKey: b64uEncode(keyPair.publicKey),
      privateKey: b64uEncode(keyPair.privateKey),
      createdAt: new Date().toISOString(),
    }
    saveHolder(holder)
  }
  return { holder, credentials: loadCredentials() }
}

/** Store a received credential. Extracts metadata for the wallet's preview card. */
export function store(vcJwt: string): StoredCredential {
  const payload = decodeJwtPayload(vcJwt)
  const vc = payload.vc as Record<string, unknown> | undefined
  const issuerDid = String(payload.iss ?? vc?.issuer ?? '')
  const subjectDid = String(payload.sub ?? '')
  const types = extractTypes(vc)
  const cred: StoredCredential = {
    id: crypto.randomUUID(),
    vcJwt,
    receivedAt: new Date().toISOString(),
    issuerDid,
    subjectDid,
    type: types,
    preview: buildPreview(types, vc),
  }
  addCredential(cred)
  return cred
}

/** Read-only view of all credentials in the wallet. */
export function list(): StoredCredential[] {
  return loadCredentials()
}

/** Delete a credential. Irrecoverable. */
export function deleteCredential(id: string): void {
  deleteCredFromStorage(id)
}

/** Wipe everything — holder identity AND credentials. Irrecoverable. */
export function resetWallet(): void {
  resetWalletStorage()
}

/**
 * Build a Verifiable Presentation containing one or more credentials, signed by the
 * holder. Returns a compact JWS (VP-JWT). The verifier validates both the outer VP
 * signature (proves holder possession of the holder DID) and each inner VC signature.
 *
 * @param credentialIds local wallet IDs of credentials to include
 * @param verifierUri the verifier's identifier (aud claim binding)
 * @param challenge nonce from the verifier to prevent replay
 */
export function createPresentation(
  credentialIds: string[],
  verifierUri: string,
  challenge: string,
): string {
  const holder = loadHolder()
  if (!holder) throw new Error('Wallet not bootstrapped')
  const creds = loadCredentials().filter((c) => credentialIds.includes(c.id))
  if (creds.length === 0) throw new Error('No matching credentials to present')

  const now = Math.floor(Date.now() / 1000)
  const payload = {
    iss: holder.did,
    sub: holder.did,
    aud: verifierUri,
    nonce: challenge,
    iat: now,
    exp: now + 300,  // 5 minute window
    vp: {
      '@context': ['https://www.w3.org/ns/credentials/v2'],
      type: ['VerifiablePresentation'],
      holder: holder.did,
      verifiableCredential: creds.map((c) => c.vcJwt),
    },
  }

  const privateKey = b64uDecode(holder.privateKey)
  return signJws(payload, privateKey, `${holder.did}#${holder.did.slice('did:key:'.length)}`)
}

// ----- internal helpers -----

function decodeJwtPayload(jwt: string): Record<string, unknown> {
  const parts = jwt.split('.')
  if (parts.length !== 3) throw new Error('Not a JWT')
  return JSON.parse(new TextDecoder().decode(b64uDecode(parts[1])))
}

function extractTypes(vc: Record<string, unknown> | undefined): string[] {
  if (!vc) return ['VerifiableCredential']
  const t = vc.type
  if (Array.isArray(t)) return t.map(String)
  if (typeof t === 'string') return [t]
  return ['VerifiableCredential']
}

function buildPreview(
  types: string[],
  vc: Record<string, unknown> | undefined,
): StoredCredential['preview'] {
  const credentialType = types.find((t) => t !== 'VerifiableCredential') ?? 'Credential'
  const subject = (vc?.credentialSubject ?? {}) as Record<string, unknown>
  const subtitle = String(
    subject.name ??
    subject.degree ??
    subject.title ??
    Object.values(subject).find((v) => typeof v === 'string') ??
    '',
  )
  return { title: credentialType, subtitle: subtitle || undefined }
}

// Re-export storage types for consumers.
export type { HolderIdentity, StoredCredential }

// Silence parseJwsHeader unused-import warning — kept for future verifier integration.
export const _unusedExportForFutureUse = parseJwsHeader
