import { parseCredentialOfferQr } from './credentialOfferQr'
import { parseOid4VciCredentialOffer } from './oid4vciOffer'
import { parsePresentationRequestQr } from './presentationRequestQr'
import { explainPublicVerificationUrlMisscan, parsePublicVerificationUrl } from './publicVerificationUrl'

/** Explain why an Add-tab scan failed — guides users away from wrong QR types/tabs. */
export function explainOfferScanFailure(raw: string): string {
  const trimmed = raw.trim()

  if (parsePresentationRequestQr(trimmed)) {
    return 'This is a verifier QR. Open the Share tab and scan it there — not Add.'
  }

  if (parseOid4VciCredentialOffer(trimmed)) {
    return 'OpenID4VCI offer recognized. Claiming from the issuer in the QR…'
  }

  try {
    const parsed = JSON.parse(trimmed) as { type?: string }
    if (parsed.type === 'trustweave-holder') {
      return 'This is a wallet identity QR, not a credential offer.'
    }
    if (parsed.type === 'trustweave-presentation-request') {
      return 'This is a verifier QR. Open the Share tab and scan it there.'
    }
  } catch {
    // not JSON
  }

  if (parseCredentialOfferQr(trimmed)) {
    return 'Offer recognized but could not be read. Try paste instead of scan.'
  }

  return 'Unrecognized credential offer QR. Scan an issuer offer from any site that supports TrustWeave direct offers or OpenID4VCI wallet offers.'
}

export function explainVerifierScanFailure(raw: string): string {
  const trimmed = raw.trim()

  if (parsePublicVerificationUrl(trimmed)) {
    return explainPublicVerificationUrlMisscan()
  }

  if (parseCredentialOfferQr(trimmed) || parseOid4VciCredentialOffer(trimmed)) {
    return 'This is a credential offer QR. Open the Add tab to receive it first.'
  }

  try {
    const parsed = JSON.parse(trimmed) as { type?: string }
    if (parsed.type === 'trustweave-credential-offer') {
      return 'This is an issuer offer QR. Open the Add tab first.'
    }
    if (parsed.type === 'trustweave-holder') {
      return 'This is a wallet identity QR. Scan the verifier QR from the verifying organisation.'
    }
  } catch {
    // not JSON
  }

  return 'Not a verifier presentation request QR.'
}
