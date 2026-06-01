import type { CacSubjectRecord, CacSubjectSummary, CacTrustDomainInfo } from './trust-domains/demo-cac'

export type { CacSubjectRecord, CacSubjectSummary, CacTrustDomainInfo }

export interface CacSubjectDetail {
  personnelId: string
  dodId: string
  name: string
  rank: string
  branch: string
  portraitFile: string
  portraitUrl: string
  portraitDigest: string
  vct: string
}

export interface CacSubjectDetailResponse {
  issuerDid: string
  domain: CacTrustDomainInfo
  subject: CacSubjectDetail
  selectivelyDisclosable: string[]
}

export interface CacTrustDomainResponse {
  issuerDid: string
  domain: CacTrustDomainInfo
  subjects: CacSubjectSummary[]
  acceptedCredentialTypes: string[]
}

export const CAC_CREDENTIAL_ENDPOINT = '/api/demo-issuer/cac/credential'

export function formatCacSubjectOption(s: CacSubjectSummary): string {
  return `${s.personnelId} — ${s.rank} ${s.name} (${s.branch})`
}

export async function fetchCacTrustDomain(baseUrl = ''): Promise<CacTrustDomainResponse> {
  const res = await fetch(`${baseUrl}/api/demo-issuer/cac/trust-domain`)
  if (!res.ok) throw new Error(`CAC trust domain HTTP ${res.status}`)
  return res.json() as Promise<CacTrustDomainResponse>
}

export async function fetchCacSubjectDetail(
  personnelId: string,
  baseUrl = '',
): Promise<CacSubjectDetailResponse> {
  const res = await fetch(
    `${baseUrl}/api/demo-issuer/cac/subject/${encodeURIComponent(personnelId)}`,
  )
  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as { error?: string }
    throw new Error(body.error ?? `CAC subject HTTP ${res.status}`)
  }
  return res.json() as Promise<CacSubjectDetailResponse>
}

export function buildCacPortalUrl(
  issuerUrl: string,
  personnelId: string,
  trustDomainId: string,
): string {
  const base = issuerUrl.replace(/\/$/, '')
  const params = new URLSearchParams({ trustDomainId })
  return `${base}/issuer/cac/${encodeURIComponent(personnelId)}?${params}`
}
