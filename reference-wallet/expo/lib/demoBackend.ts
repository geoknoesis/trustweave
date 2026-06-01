/**
 * Demo backend client. Same Next.js endpoints as the web/Android/iOS wallets.
 *
 * Base URL comes from app.json's `expo.extra.demoBackendBaseUrl`. Default is
 * 10.0.2.2:3000 (Android emulator alias). For Expo Go on a phone, override
 * by editing app.json to your host machine's LAN IP (e.g., 192.168.1.42:3000)
 * and re-running `npx expo start`.
 */
import Constants from 'expo-constants'
import {
  claimIncomingCredentialOffer,
  type IncomingCredentialOffer,
  type WalletClaimResponse,
} from './credentialOfferIngress'

const baseUrl: string =
  (Constants.expoConfig?.extra?.demoBackendBaseUrl as string | undefined) ??
  'http://10.0.2.2:3000'

export interface CredentialOffer {
  format: 'vc+jwt' | 'vc+sd-jwt'
  credential: string
  issuer: string
  trustDomainId?: string
  studentId?: string
  vct?: string
  selectivelyDisclosable?: string[]
}

export interface TrustDomainInfo {
  domainId: string
  name: string
  description: string
  country: string
  registrarContact: string
  defaultStudentId: string
}

export interface DegreeSummary {
  studentId: string
  name: string
  degree: string
  major: string
  vct: string
}

export interface TrustDomainDiscovery {
  issuerDid: string
  domain: TrustDomainInfo
  degrees: DegreeSummary[]
  acceptedCredentialTypes: string[]
}

export interface PresentationRequestParams {
  verifier: string
  audience: string
  nonce: string
  acceptedTypes: string[]
}

export interface VerificationCheck {
  step: string
  passed: boolean
  detail?: string
}

export interface VerifiedCredentialView {
  type: string[]
  issuer: string
  subject: string
  disclosedClaims: Record<string, unknown>
  withheldClaimNames?: string[]
}

export interface VerificationResponse {
  valid: boolean
  checks: VerificationCheck[]
  holder?: string
  credentials?: VerifiedCredentialView[]
}

export async function fetchTrustDomain(): Promise<TrustDomainDiscovery> {
  const res = await fetch(`${baseUrl}/api/demo-issuer/trust-domain`)
  if (!res.ok) throw new Error(`Trust domain HTTP ${res.status}`)
  return (await res.json()) as TrustDomainDiscovery
}

export async function receiveCredential(subjectDid: string, studentId?: string): Promise<CredentialOffer> {
  const params = new URLSearchParams({ subject: subjectDid })
  if (studentId) params.set('studentId', studentId)
  const res = await fetch(`${baseUrl}/api/demo-issuer/credential?${params}`)
  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as { error?: string }
    throw new Error(body.error ?? `Issuer HTTP ${res.status}`)
  }
  return (await res.json()) as CredentialOffer
}

export async function claimCredentialOffer(
  parsed: IncomingCredentialOffer,
  holderDid: string,
): Promise<WalletClaimResponse> {
  return claimIncomingCredentialOffer(parsed, holderDid, baseUrl)
}

export { parseIncomingCredentialQr, type IncomingCredentialOffer } from './credentialOfferIngress'

export async function fetchPresentationRequest(): Promise<PresentationRequestParams> {
  const res = await fetch(`${baseUrl}/api/demo-verifier/request`)
  if (!res.ok) throw new Error(`Verifier request HTTP ${res.status}`)
  return (await res.json()) as PresentationRequestParams
}

export async function verify(
  presentation: string,
  format: CredentialOffer['format'],
  expectedNonce: string,
): Promise<VerificationResponse> {
  const res = await fetch(`${baseUrl}/api/demo-verifier/verify`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ presentation, format, expectedNonce }),
  })
  return (await res.json()) as VerificationResponse
}

/** Exposed so the Settings screen / debug view can show what backend is in use. */
export const demoBackendBaseUrl = baseUrl
