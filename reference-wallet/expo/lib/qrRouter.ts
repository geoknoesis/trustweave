/**
 * Unified QR / NFC / Deep-Link classifier for the Expo mobile wallet.
 *
 * Instead of forcing users to guess whether to open "Add" or "Share" before
 * scanning, this router inspects any incoming QR payload, NFC URI, pasted text,
 * or OS deep link (`trustweavewallet://`, `openid-credential-offer://`,
 * `openid4vp://`, HTTPS links) and routes it automatically.
 */
import { parseIncomingCredentialQr, type IncomingCredentialOffer } from './credentialOfferIngress'
import { parsePresentationRequestQr, type PresentationRequestQrPayload } from './presentationRequestQr'
import { parsePublicVerificationUrl } from './publicVerificationUrl'

export type RoutedScanPayload =
  | { kind: 'offer'; offer: IncomingCredentialOffer; raw: string }
  | { kind: 'presentation'; request: PresentationRequestQrPayload; raw: string }
  | { kind: 'public-verify'; url: string; raw: string }
  | { kind: 'holder-did'; did: string; raw: string }
  | { kind: 'unknown'; reason: string; raw: string }

/**
 * Unwrap custom app deep links like `trustweavewallet://scan?data=...` or
 * `trustweavewallet://open?url=...` before standard payload classification.
 */
export function unwrapDeepLinkPayload(raw: string): string {
  const trimmed = raw.trim()
  if (trimmed.startsWith('trustweavewallet://')) {
    try {
      const url = new URL(trimmed)
      const embedded =
        url.searchParams.get('data') ??
        url.searchParams.get('url') ??
        url.searchParams.get('offer') ??
        url.searchParams.get('request')
      if (embedded && embedded.trim()) return embedded.trim()
    } catch {
      return trimmed
    }
  }
  return trimmed
}

/**
 * Parse an `openid4vp://` authorization request URI into a presentation request payload
 * when it carries a `request_uri` or verifier URL.
 */
function parseOpenId4VpUri(raw: string): PresentationRequestQrPayload | null {
  if (!raw.toLowerCase().startsWith('openid4vp://')) return null
  try {
    const normalized = raw.replace(/^openid4vp:\/\//i, 'https://openid4vp.local/')
    const url = new URL(normalized)
    const requestUri = url.searchParams.get('request_uri')
    if (requestUri && (requestUri.startsWith('http://') || requestUri.startsWith('https://'))) {
      const reqUrl = new URL(requestUri)
      return {
        v: 1,
        type: 'trustweave-presentation-request',
        verifierUrl: `${reqUrl.protocol}//${reqUrl.host}`,
        requestEndpoint: `${reqUrl.pathname}${reqUrl.search}`,
      }
    }
    const responseUri = url.searchParams.get('response_uri')
    if (responseUri && (responseUri.startsWith('http://') || responseUri.startsWith('https://'))) {
      const resUrl = new URL(responseUri)
      return {
        v: 1,
        type: 'trustweave-presentation-request',
        verifierUrl: `${resUrl.protocol}//${resUrl.host}`,
        verifyEndpoint: `${resUrl.pathname}${resUrl.search}`,
      }
    }
  } catch {
    return null
  }
  return null
}

/** Classify any scanned QR, NFC record, manual paste, or incoming OS link. */
export function classifyIncomingPayload(input: string): RoutedScanPayload {
  const raw = unwrapDeepLinkPayload(input)
  if (!raw) {
    return { kind: 'unknown', reason: 'No code or link was detected. Try scanning again.', raw }
  }

  // 1. Credential offer (TrustWeave direct or OpenID4VCI pre-authorized)
  const offer = parseIncomingCredentialQr(raw)
  if (offer) {
    return { kind: 'offer', offer, raw }
  }

  // 2. Public verification link (intended for a browser, not holder presentation)
  const publicVerify = parsePublicVerificationUrl(raw)
  if (publicVerify) {
    return { kind: 'public-verify', url: publicVerify, raw }
  }

  // 3. Presentation request (TrustWeave JSON/URL or openid4vp://)
  const presentation = parsePresentationRequestQr(raw) ?? parseOpenId4VpUri(raw)
  if (presentation) {
    return { kind: 'presentation', request: presentation, raw }
  }

  // 4. Holder identity QR (inform user calmly instead of failing cryptically)
  try {
    const parsed = JSON.parse(raw) as { type?: string; did?: string }
    if (parsed.type === 'trustweave-holder' && typeof parsed.did === 'string') {
      return { kind: 'holder-did', did: parsed.did, raw }
    }
  } catch {
    // not JSON
  }

  if (raw.startsWith('did:')) {
    return { kind: 'holder-did', did: raw, raw }
  }

  return {
    kind: 'unknown',
    reason:
      'This QR code is not a supported credential offer or verification request. Check that you are scanning a credential QR from an issuer or verifier.',
    raw,
  }
}
