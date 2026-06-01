import { parseCredentialOfferQr } from '@/lib/credential-offer-qr'
import { parsePresentationRequestQr } from '@/lib/presentation-request-qr'
import { explainPublicVerificationUrlMisscan, parsePublicVerificationUrl } from '@/lib/public-verification-url'

export function explainOfferScanFailure(raw: string): string {
  const trimmed = raw.trim()

  if (parsePresentationRequestQr(trimmed)) {
    return 'This is a verifier QR. Open Share and scan it there — not Add.'
  }

  if (trimmed.startsWith('openid-credential-offer:') || trimmed.includes('credential_offer=')) {
    return 'This is a TrustWeave SaaS (OpenID4VCI) offer. The reference-wallet demo expects issuer QRs from /issuer/degree or /issuer/offer.'
  }

  try {
    const parsed = JSON.parse(trimmed) as { type?: string }
    if (parsed.type === 'trustweave-holder') {
      return 'This is a wallet identity QR, not a credential offer.'
    }
    if (parsed.type === 'trustweave-presentation-request') {
      return 'This is a verifier QR. Use the Share tab instead.'
    }
  } catch {
    // not JSON
  }

  return 'Not a TrustWeave credential offer QR. Scan the issuer offer from /issuer/degree or /issuer/offer — not the verifier QR from /verifier.'
}

export function explainVerifierScanFailure(raw: string): string {
  const trimmed = raw.trim()

  if (parsePublicVerificationUrl(trimmed)) {
    return explainPublicVerificationUrlMisscan()
  }

  if (parseCredentialOfferQr(trimmed)) {
    return 'This is an issuer offer QR. Receive it on the Add tab first.'
  }

  try {
    const parsed = JSON.parse(trimmed) as { type?: string }
    if (parsed.type === 'trustweave-credential-offer') {
      return 'This is an issuer offer QR. Add the credential before sharing with a verifier.'
    }
  } catch {
    // not JSON
  }

  return 'Not a verifier presentation request QR. Scan the QR from /verifier.'
}
