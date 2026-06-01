import {
  type PresentationRequestParams,
  type PresentationRequestQrPayload,
  requestUrl,
  verifyUrl,
} from './presentationRequestQr'
import { networkErrorHint, resolveIssuerUrl } from './resolveIssuerUrl'
import type { VerificationResponse } from './demoBackend'

function resolveVerifierUrl(verifierUrl: string, fallbackBaseUrl: string): string {
  return resolveIssuerUrl(verifierUrl, fallbackBaseUrl)
}

export async function fetchPresentationRequestFromQr(
  qr: PresentationRequestQrPayload,
  fallbackBaseUrl: string,
): Promise<PresentationRequestParams> {
  if (qr.presentationRequest) {
    return qr.presentationRequest
  }
  const base = resolveVerifierUrl(qr.verifierUrl, fallbackBaseUrl)
  const url = requestUrl(qr, base)
  try {
    const res = await fetch(url)
    if (!res.ok) throw new Error(`Verifier request HTTP ${res.status}`)
    return (await res.json()) as PresentationRequestParams
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    if (msg.includes('Network request failed') || msg.includes('Failed to fetch')) {
      throw new Error(networkErrorHint(base, fallbackBaseUrl))
    }
    throw e
  }
}

export async function submitPresentationToVerifier(
  qr: PresentationRequestQrPayload,
  presentation: string,
  format: 'vc+jwt' | 'vc+sd-jwt',
  expectedNonce: string,
  fallbackBaseUrl: string,
): Promise<VerificationResponse> {
  const base = resolveVerifierUrl(qr.verifierUrl, fallbackBaseUrl)
  const url = verifyUrl(qr, base)
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ presentation, format, expectedNonce }),
    })
    if (!res.ok) {
      const body = (await res.json().catch(() => ({}))) as { error?: string }
      throw new Error(body.error ?? `Verify HTTP ${res.status}`)
    }
    return (await res.json()) as VerificationResponse
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    if (msg.includes('Network request failed') || msg.includes('Failed to fetch')) {
      throw new Error(networkErrorHint(base, fallbackBaseUrl))
    }
    throw e
  }
}
