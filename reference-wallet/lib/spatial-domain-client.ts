/** Client-safe types for the demo-sf-airspace spatial trust domain API. */

export interface AirspaceBoundary {
  minLat: number
  maxLat: number
  minLon: number
  maxLon: number
}

export interface SpatialTrustDomainInfo {
  domainId: string
  name: string
  description: string
  authorityContact: string
  defaultDroneId: string
  boundary: AirspaceBoundary
  allowedActivities: string[]
}

export interface DroneAuthorizationSummary {
  droneId: string
  callsign: string
  operatorName: string
  activityType: string
  vct: string
}

export interface DroneAuthorizationRecord extends DroneAuthorizationSummary {
  maxAltitudeFt: string
  maxDuration: string
}

export interface DroneDetailResponse {
  issuerDid: string
  domain: SpatialTrustDomainInfo
  drone: DroneAuthorizationRecord
  selectivelyDisclosable: string[]
}

export interface SpatialTrustDomainDiscovery {
  issuerDid: string
  domain: SpatialTrustDomainInfo
  drones: DroneAuthorizationSummary[]
  acceptedCredentialTypes: string[]
}

export async function fetchSpatialTrustDomain(baseUrl = ''): Promise<SpatialTrustDomainDiscovery> {
  const res = await fetch(`${baseUrl}/api/demo-issuer/spatial/trust-domain`)
  if (!res.ok) throw new Error(`Spatial trust domain HTTP ${res.status}`)
  return (await res.json()) as SpatialTrustDomainDiscovery
}

export function formatDroneOption(d: DroneAuthorizationSummary): string {
  return `${d.droneId} — ${d.callsign} (${d.activityType})`
}

export async function fetchDroneDetail(
  droneId: string,
  baseUrl = '',
): Promise<DroneDetailResponse> {
  const res = await fetch(`${baseUrl}/api/demo-issuer/spatial/drone/${encodeURIComponent(droneId)}`)
  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as { error?: string }
    throw new Error(body.error ?? `Drone HTTP ${res.status}`)
  }
  return (await res.json()) as DroneDetailResponse
}

export const SPATIAL_CREDENTIAL_ENDPOINT = '/api/demo-issuer/spatial/credential'

export function buildAirspacePortalUrl(
  issuerUrl: string,
  droneId: string,
  trustDomainId: string,
): string {
  const base = issuerUrl.replace(/\/$/, '')
  const params = new URLSearchParams({ trustDomainId })
  return `${base}/issuer/airspace/${encodeURIComponent(droneId)}?${params}`
}
