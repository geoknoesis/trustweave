/**
 * QR payload for verifier → wallet presentation requests (OID4VP-style demo).
 *
 * The verifier displays a QR; the wallet scans it, fetches a fresh authorization
 * request (nonce + accepted types), then POSTs the presentation back.
 */
export interface PresentationRequestQrPayload {
  v: 1
  type: 'trustweave-presentation-request'
  /** Verifier base URL, e.g. http://192.168.1.252:3000 */
  verifierUrl: string
  /** Default: /api/demo-verifier/request */
  requestEndpoint?: string
  /** Default: /api/demo-verifier/verify */
  verifyEndpoint?: string
  /** When set, the wallet uses this request directly (no extra round-trip on scan). */
  presentationRequest?: PresentationRequestParams
}

export const DEFAULT_REQUEST_ENDPOINT = '/api/demo-verifier/request'
export const DEFAULT_VERIFY_ENDPOINT = '/api/demo-verifier/verify'

export interface PresentationRequestParams {
  verifier: string
  audience: string
  nonce: string
  acceptedTypes: string[]
}

export function buildPresentationRequestUrl(verifierUrl: string, path = '/verifier'): string {
  const base = verifierUrl.replace(/\/$/, '')
  return `${base}${path}`
}

export function buildPresentationRequestQrPayload(
  verifierUrl: string,
  presentationRequest?: PresentationRequestParams,
): string {
  const payload: PresentationRequestQrPayload = {
    v: 1,
    type: 'trustweave-presentation-request',
    verifierUrl: verifierUrl.replace(/\/$/, ''),
  }
  if (presentationRequest) {
    payload.presentationRequest = presentationRequest
  }
  return JSON.stringify(payload)
}

function isVerifierUrlPath(pathname: string): boolean {
  return pathname === '/verifier' || pathname.startsWith('/verifier/') || pathname === '/airspace/gate'
}

export function parsePresentationRequestQr(raw: string): PresentationRequestQrPayload | null {
  const trimmed = raw.trim()

  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    try {
      const url = new URL(trimmed)
      if (!isVerifierUrlPath(url.pathname)) return null
      return {
        v: 1,
        type: 'trustweave-presentation-request',
        verifierUrl: `${url.protocol}//${url.host}`,
      }
    } catch {
      return null
    }
  }

  try {
    const parsed = JSON.parse(trimmed) as Partial<PresentationRequestQrPayload> & {
      issuerUrl?: string
    }
    if (parsed.type !== 'trustweave-presentation-request') return null
    const verifierUrl = parsed.verifierUrl ?? parsed.issuerUrl
    if (typeof verifierUrl !== 'string') return null
    return {
      v: 1,
      type: 'trustweave-presentation-request',
      verifierUrl: verifierUrl.replace(/\/$/, ''),
      requestEndpoint: parsed.requestEndpoint,
      verifyEndpoint: parsed.verifyEndpoint,
      presentationRequest: parsed.presentationRequest,
    }
  } catch {
    return null
  }
}

export function requestUrl(qr: PresentationRequestQrPayload, verifierBase: string): string {
  const endpoint = qr.requestEndpoint ?? DEFAULT_REQUEST_ENDPOINT
  const base = verifierBase.replace(/\/$/, '')
  const path = endpoint.startsWith('/') ? endpoint : `/${endpoint}`
  return `${base}${path}`
}

export function verifyUrl(qr: PresentationRequestQrPayload, verifierBase: string): string {
  const endpoint = qr.verifyEndpoint ?? DEFAULT_VERIFY_ENDPOINT
  const base = verifierBase.replace(/\/$/, '')
  const path = endpoint.startsWith('/') ? endpoint : `/${endpoint}`
  return `${base}${path}`
}

/** True when credential type matches verifier accepted types. */
export function credentialMatchesRequest(
  credentialTypes: string[],
  acceptedTypes: string[],
): boolean {
  if (acceptedTypes.length === 0) return true
  return credentialTypes.some((t) => acceptedTypes.includes(t))
}
