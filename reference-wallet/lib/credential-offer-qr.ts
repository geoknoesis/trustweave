/**
 * QR payload for issuer → wallet credential offers.
 *
 * The wallet scans a QR, reads issuerUrl + opaque params, then calls the issuer's
 * credential endpoint with its holder DID as `subject`. Issuer-specific fields
 * (studentId, offerId, credential type, etc.) live in `params` — the wallet does
 * not interpret them.
 */
export interface CredentialOfferQrPayload {
  v: 1
  type: 'trustweave-credential-offer'
  /** Issuer base URL, e.g. http://192.168.1.252:3000 */
  issuerUrl: string
  /** Path on issuerUrl. Default: /api/demo-issuer/credential */
  credentialEndpoint?: string
  /** Query params forwarded to the credential endpoint (wallet adds `subject`). */
  params: Record<string, string>
}

export const DEFAULT_CREDENTIAL_ENDPOINT = '/api/demo-issuer/credential'

export function normalizeOfferParams(offer: CredentialOfferQrPayload): Record<string, string> {
  const params = { ...offer.params }
  // Holder DID is always supplied by the wallet at claim time — never from the QR.
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

export function buildCredentialOfferQrPayload(
  issuerUrl: string,
  params: Record<string, string>,
  credentialEndpoint = DEFAULT_CREDENTIAL_ENDPOINT,
): string {
  const payload: CredentialOfferQrPayload = {
    v: 1,
    type: 'trustweave-credential-offer',
    issuerUrl: issuerUrl.replace(/\/$/, ''),
    credentialEndpoint,
    params,
  }
  return JSON.stringify(payload)
}

/** Build a scannable HTTPS URL alternative (works in some QR readers as a link). */
export function buildCredentialOfferUrl(
  issuerUrl: string,
  params: Record<string, string>,
  path = '/issuer/offer',
): string {
  const base = issuerUrl.replace(/\/$/, '')
  return `${base}${path}?${new URLSearchParams(params)}`
}

/** Per-graduate portal page — deep link for email / printed handouts. */
export function buildDegreePortalUrl(
  issuerUrl: string,
  studentId: string,
  trustDomainId: string,
): string {
  const base = issuerUrl.replace(/\/$/, '')
  const params = new URLSearchParams({ trustDomainId })
  return `${base}/issuer/degree/${encodeURIComponent(studentId)}?${params}`
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

  const droneMatch = url.pathname.match(/\/issuer\/airspace\/([^/]+)/)
  if (droneMatch) {
    params.droneId = decodeURIComponent(droneMatch[1])
  }

  const faaMatch = url.pathname.match(/\/issuer\/faa\/([^/]+)/)
  if (faaMatch) {
    params.droneId = decodeURIComponent(faaMatch[1])
  }

  const cacMatch = url.pathname.match(/\/issuer\/cac\/([^/]+)/)
  if (cacMatch) {
    params.personnelId = decodeURIComponent(cacMatch[1])
  }

  return Object.keys(params).length > 0 ? params : null
}

function isOfferUrlPath(pathname: string): boolean {
  return (
    pathname.includes('/issuer/offer') ||
    pathname.includes('/issuer/degree/') ||
    pathname.includes('/issuer/airspace/') ||
    pathname.includes('/issuer/faa/') ||
    pathname.includes('/issuer/cac/') ||
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
      const isAirspace = url.pathname.includes('/issuer/airspace/')
      const isFaa = url.pathname.includes('/issuer/faa/')
      const isCac = url.pathname.includes('/issuer/cac/')
      return {
        v: 1,
        type: 'trustweave-credential-offer',
        issuerUrl: `${url.protocol}//${url.host}`,
        credentialEndpoint: isCac
          ? '/api/demo-issuer/cac/credential'
          : isFaa
            ? '/api/demo-issuer/faa/credential'
            : isAirspace
              ? '/api/demo-issuer/spatial/credential'
              : undefined,
        params,
      }
    } catch {
      return null
    }
  }

  try {
    const parsed = JSON.parse(trimmed) as Partial<CredentialOfferQrPayload> & {
      studentId?: string
      droneId?: string
      personnelId?: string
      trustDomainId?: string
    }
    if (parsed.type !== 'trustweave-credential-offer' || typeof parsed.issuerUrl !== 'string') {
      return null
    }

    const params: Record<string, string> = { ...(parsed.params ?? {}) }
    // Legacy demo QRs used top-level studentId / trustDomainId.
    if (typeof parsed.studentId === 'string' && !params.studentId) {
      params.studentId = parsed.studentId
    }
    if (typeof parsed.droneId === 'string' && !params.droneId) {
      params.droneId = parsed.droneId
    }
    if (typeof parsed.personnelId === 'string' && !params.personnelId) {
      params.personnelId = parsed.personnelId
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
