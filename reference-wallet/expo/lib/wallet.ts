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
import { decodeSdJwtVc, presentSdJwtVc } from './sdjwt'
import {
  loadHolder,
  saveHolder,
  loadCredentials,
  addCredential,
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
  let holder = await loadHolder()
  if (!holder) {
    const kp = generateEd25519KeyPair()
    holder = {
      did: publicKeyToDidKey(kp.publicKey),
      publicKey: b64uEncode(kp.publicKey),
      privateKey: b64uEncode(kp.privateKey),
      createdAt: new Date().toISOString(),
    }
    await saveHolder(holder)
  }
  return { holder, credentials: await loadCredentials() }
}

export async function store(
  credential: string,
  format: StoredCredential['format'],
  selectivelyDisclosable: string[] = [],
): Promise<StoredCredential> {
  const meta = format === 'vc+sd-jwt' ? extractSdJwtMeta(credential) : extractVcJwtMeta(credential)
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
  await addCredential(cred)
  return cred
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

export type { HolderIdentity, StoredCredential }
