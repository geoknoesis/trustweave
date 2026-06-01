/** Mirrors reference-wallet/lib/credential-offer-qr.ts for the Expo wallet. */
export interface CredentialOfferQrPayload {
  v: 1
  type: 'trustweave-credential-offer'
  issuerUrl: string
  credentialEndpoint?: string
  params: Record<string, string>
}

export const DEFAULT_CREDENTIAL_ENDPOINT = '/api/demo-issuer/credential'

export function normalizeOfferParams(offer: CredentialOfferQrPayload): Record<string, string> {
  const params = { ...offer.params }
  delete params.subject
  return params
}

export function buildCredentialClaimUrl(
  offer: CredentialOfferQrPayload,
  holderDid: string,
  issuerBase: string,
): string {
  const endpoint = offer.credentialEndpoint ?? DEFAULT_CREDENTIAL_ENDPOINT
  const base = issuerBase.replace(/\/$/, '')
  const path = endpoint.startsWith('/') ? endpoint : `/${endpoint}`
  const params = new URLSearchParams({ ...normalizeOfferParams(offer), subject: holderDid })
  return `${base}${path}?${params}`
}

function parseParamsFromUrl(url: URL): Record<string, string> | null {
  const params: Record<string, string> = {}
  url.searchParams.forEach((value, key) => {
    params[key] = value
  })

  const degreeMatch = url.pathname.match(/\/issuer\/degree\/([^/]+)/)
  if (degreeMatch) {
    params.studentId = decodeURIComponent(degreeMatch[1])
  }

  return Object.keys(params).length > 0 ? params : null
}

function isOfferUrlPath(pathname: string): boolean {
  return (
    pathname.includes('/issuer/offer') ||
    pathname.includes('/issuer/degree/') ||
    pathname.includes('/credential-offer')
  )
}

export function parseCredentialOfferQr(raw: string): CredentialOfferQrPayload | null {
  const trimmed = raw.trim()

  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    try {
      const url = new URL(trimmed)
      if (!isOfferUrlPath(url.pathname)) return null
      const params = parseParamsFromUrl(url)
      if (!params) return null
      return {
        v: 1,
        type: 'trustweave-credential-offer',
        issuerUrl: `${url.protocol}//${url.host}`,
        params,
      }
    } catch {
      return null
    }
  }

  try {
    const parsed = JSON.parse(trimmed) as Partial<CredentialOfferQrPayload> & {
      studentId?: string
      trustDomainId?: string
    }
    if (parsed.type !== 'trustweave-credential-offer' || typeof parsed.issuerUrl !== 'string') {
      return null
    }

    const params: Record<string, string> = { ...(parsed.params ?? {}) }
    if (typeof parsed.studentId === 'string' && !params.studentId) {
      params.studentId = parsed.studentId
    }
    if (typeof parsed.trustDomainId === 'string' && !params.trustDomainId) {
      params.trustDomainId = parsed.trustDomainId
    }
    if (Object.keys(params).length === 0) return null

    return {
      v: 1,
      type: 'trustweave-credential-offer',
      issuerUrl: parsed.issuerUrl.replace(/\/$/, ''),
      credentialEndpoint: parsed.credentialEndpoint,
      params,
    }
  } catch {
    return null
  }
}
