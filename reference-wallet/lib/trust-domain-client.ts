/** Client-safe types for the demo-university trust domain discovery API. */

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

/** Full registrar row — available from the per-student degree API. */
export interface DegreeRecord extends DegreeSummary {
  institution: string
  graduationDate: string
  gpa: string
}

export interface DegreeDetailResponse {
  issuerDid: string
  domain: TrustDomainInfo
  degree: DegreeRecord
  selectivelyDisclosable: string[]
}

export interface TrustDomainDiscovery {
  issuerDid: string
  domain: TrustDomainInfo
  degrees: DegreeSummary[]
  acceptedCredentialTypes: string[]
}

export async function fetchTrustDomain(baseUrl = ''): Promise<TrustDomainDiscovery> {
  const res = await fetch(`${baseUrl}/api/demo-issuer/trust-domain`)
  if (!res.ok) throw new Error(`Trust domain HTTP ${res.status}`)
  return (await res.json()) as TrustDomainDiscovery
}

export function formatDegreeOption(d: DegreeSummary): string {
  return `${d.studentId} — ${d.name}, ${d.degree} (${d.major})`
}

export async function fetchDegreeDetail(
  studentId: string,
  baseUrl = '',
): Promise<DegreeDetailResponse> {
  const res = await fetch(`${baseUrl}/api/demo-issuer/degree/${encodeURIComponent(studentId)}`)
  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as { error?: string }
    throw new Error(body.error ?? `Degree HTTP ${res.status}`)
  }
  return (await res.json()) as DegreeDetailResponse
}
