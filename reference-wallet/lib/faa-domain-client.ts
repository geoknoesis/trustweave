/** Client-safe types for the demo FAA drone registry API. */

export interface FaaTrustDomainInfo {
  domainId: string
  name: string
  description: string
  authorityName: string
  authorityContact: string
  defaultDroneId: string
}

export interface FaaDroneSummary {
  droneId: string
  registrationNumber: string
  make: string
  model: string
  callsign: string
  vct: string
}

export interface FaaDroneRecord extends FaaDroneSummary {
  serialNumber: string
  weightClass: string
  photoFile: string
  photoUrl: string
  photoDigest: string
}

export interface FaaDroneDetailResponse {
  issuerDid: string
  domain: FaaTrustDomainInfo
  drone: FaaDroneRecord
  selectivelyDisclosable: string[]
}

export interface FaaTrustDomainDiscovery {
  issuerDid: string
  domain: FaaTrustDomainInfo
  drones: FaaDroneSummary[]
  acceptedCredentialTypes: string[]
}

export const FAA_CREDENTIAL_ENDPOINT = '/api/demo-issuer/faa/credential'

export async function fetchFaaTrustDomain(baseUrl = ''): Promise<FaaTrustDomainDiscovery> {
  const res = await fetch(`${baseUrl}/api/demo-issuer/faa/trust-domain`)
  if (!res.ok) throw new Error(`FAA trust domain HTTP ${res.status}`)
  return (await res.json()) as FaaTrustDomainDiscovery
}

export function formatFaaDroneOption(d: FaaDroneSummary): string {
  return `${d.registrationNumber} — ${d.make} ${d.model} (${d.callsign})`
}

export async function fetchFaaDroneDetail(
  droneId: string,
  baseUrl = '',
): Promise<FaaDroneDetailResponse> {
  const res = await fetch(`${baseUrl}/api/demo-issuer/faa/drone/${encodeURIComponent(droneId)}`)
  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as { error?: string }
    throw new Error(body.error ?? `FAA drone HTTP ${res.status}`)
  }
  return (await res.json()) as FaaDroneDetailResponse
}

export function buildFaaPortalUrl(
  issuerUrl: string,
  droneId: string,
  trustDomainId: string,
): string {
  const base = issuerUrl.replace(/\/$/, '')
  const params = new URLSearchParams({ trustDomainId })
  return `${base}/issuer/faa/${encodeURIComponent(droneId)}?${params}`
}
