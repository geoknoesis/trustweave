import { buildCredentialClaimUrl, type CredentialOfferQrPayload } from './credential-offer-qr'
import { networkErrorHint, resolveIssuerUrl } from './resolve-issuer-url'
import type { StoredCredential } from './storage'

export interface IssuedCredentialResponse {
  format: StoredCredential['format']
  credential: string
  issuer: string
  selectivelyDisclosable?: string[]
}

/** Fetch a signed credential from a scanned issuer offer, bound to [holderDid]. */
export async function fetchCredentialFromOffer(
  offer: CredentialOfferQrPayload,
  holderDid: string,
  fallbackBaseUrl?: string,
): Promise<IssuedCredentialResponse> {
  const issuerUrl = resolveIssuerUrl(offer.issuerUrl, fallbackBaseUrl)
  const url = buildCredentialClaimUrl(offer, holderDid, issuerUrl)
  try {
    const res = await fetch(url)
    if (!res.ok) {
      const body = (await res.json().catch(() => ({}))) as { error?: string }
      throw new Error(body.error ?? `Issuer HTTP ${res.status}`)
    }
    return (await res.json()) as IssuedCredentialResponse
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    if (msg.includes('Network request failed') || msg.includes('Failed to fetch')) {
      throw new Error(networkErrorHint(issuerUrl, fallbackBaseUrl))
    }
    throw e
  }
}
