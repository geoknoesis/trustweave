import { parseCredentialOfferQr } from './credentialOfferQr'
import { parseOid4VciCredentialOffer } from './oid4vciOffer'
import { parsePresentationRequestQr } from './presentationRequestQr'
import { explainPublicVerificationUrlMisscan, parsePublicVerificationUrl } from './publicVerificationUrl'

/** Explain why an Add-only scan failed — guides users calmly. */
export function explainOfferScanFailure(raw: string): string {
  const trimmed = raw.trim()

  if (parsePresentationRequestQr(trimmed)) {
    return 'This is a verification request QR. Use the Scan tab to share a credential.'
  }

  if (parseOid4VciCredentialOffer(trimmed)) {
    return 'Credential offer recognized. Receiving your credential…'
  }

  try {
    const parsed = JSON.parse(trimmed) as { type?: string }
    if (parsed.type === 'trustweave-holder') {
      return 'This is a wallet ID QR, not a credential offer.'
    }
    if (parsed.type === 'trustweave-presentation-request') {
      return 'This is a verification request QR. Use the Scan tab to share a credential.'
    }
  } catch {
    // not JSON
  }

  if (parseCredentialOfferQr(trimmed)) {
    return 'This offer could not be read automatically. Try entering the link manually.'
  }

  return 'Unrecognized QR code. Point your camera at a credential offer from your issuer.'
}

export function explainVerifierScanFailure(raw: string): string {
  const trimmed = raw.trim()

  if (parsePublicVerificationUrl(trimmed)) {
    return explainPublicVerificationUrlMisscan()
  }

  if (parseCredentialOfferQr(trimmed) || parseOid4VciCredentialOffer(trimmed)) {
    return 'This is a credential offer QR. Use the Scan tab to add it to your wallet first.'
  }

  try {
    const parsed = JSON.parse(trimmed) as { type?: string }
    if (parsed.type === 'trustweave-credential-offer') {
      return 'This is a credential offer QR. Use the Scan tab to add it first.'
    }
    if (parsed.type === 'trustweave-holder') {
      return 'This is a wallet ID QR. Scan the verification QR shown by the requester.'
    }
  } catch {
    // not JSON
  }

  return 'This QR code is not a verification request. Try scanning the requester’s QR code again.'
}
