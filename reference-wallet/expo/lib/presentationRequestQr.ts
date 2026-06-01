/** Mirrors reference-wallet/lib/presentation-request-qr.ts for Expo. */
export interface PresentationRequestQrPayload {
  v: 1
  type: 'trustweave-presentation-request'
  verifierUrl: string
  requestEndpoint?: string
  verifyEndpoint?: string
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

function isVerifierUrlPath(pathname: string): boolean {
  return pathname === '/verifier' || pathname.startsWith('/verifier/')
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
    const parsed = JSON.parse(trimmed) as Partial<PresentationRequestQrPayload>
    if (parsed.type !== 'trustweave-presentation-request' || typeof parsed.verifierUrl !== 'string') {
      return null
    }
    return {
      v: 1,
      type: 'trustweave-presentation-request',
      verifierUrl: parsed.verifierUrl.replace(/\/$/, ''),
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

export function credentialMatchesRequest(
  credentialTypes: string[],
  acceptedTypes: string[],
): boolean {
  if (acceptedTypes.length === 0) return true
  return credentialTypes.some((t) => acceptedTypes.includes(t))
}
