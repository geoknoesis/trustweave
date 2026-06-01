import { buildCredentialClaimUrl, parseCredentialOfferQr, type CredentialOfferQrPayload } from './credentialOfferQr'
import { parseOid4VciCredentialOffer } from './oid4vciOffer'
import { networkErrorHint, resolveIssuerUrl } from './resolveIssuerUrl'

export type CredentialFormat = 'vc+jwt' | 'vc+sd-jwt' | 'vc-ld'

export interface WalletClaimResponse {
  format: CredentialFormat
  credential: string
  issuer: string
  subjectDid?: string
  type?: string[]
  selectivelyDisclosable?: string[]
  preview?: { title: string; subtitle?: string }
}

export type IncomingCredentialOffer =
  | { protocol: 'trustweave-direct'; offer: CredentialOfferQrPayload }
  | {
      protocol: 'oid4vci-pre-authorized'
      issuerBase: string
      preAuthorizedCode: string
      credentialConfigurationIds: string[]
    }

/** Parse any supported credential-offer QR (issuer URL comes from the QR, not hardcoded). */
export function parseIncomingCredentialQr(raw: string): IncomingCredentialOffer | null {
  const trustweave = parseCredentialOfferQr(raw)
  if (trustweave) return { protocol: 'trustweave-direct', offer: trustweave }

  const oid4vci = parseOid4VciCredentialOffer(raw)
  if (oid4vci) {
    return {
      protocol: 'oid4vci-pre-authorized',
      issuerBase: oid4vci.issuerBase,
      preAuthorizedCode: oid4vci.preAuthorizedCode,
      credentialConfigurationIds: oid4vci.credentialConfigurationIds,
    }
  }

  return null
}

async function claimTrustWeaveDirect(
  offer: CredentialOfferQrPayload,
  holderDid: string,
  fallbackBaseUrl?: string,
): Promise<WalletClaimResponse> {
  const issuerUrl = resolveIssuerUrl(offer.issuerUrl, fallbackBaseUrl)
  const url = buildCredentialClaimUrl(offer, holderDid, issuerUrl)
  const res = await fetch(url)
  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as { error?: string }
    throw new Error(body.error ?? `Issuer HTTP ${res.status}`)
  }
  return (await res.json()) as WalletClaimResponse
}

async function claimOid4VciPreAuthorized(
  offer: Extract<IncomingCredentialOffer, { protocol: 'oid4vci-pre-authorized' }>,
  holderDid: string,
  fallbackBaseUrl?: string,
): Promise<WalletClaimResponse> {
  const issuerUrl = resolveIssuerUrl(offer.issuerBase, fallbackBaseUrl)
  const res = await fetch(`${issuerUrl}/api/public/credential-offer/redeem`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      preAuthorizedCode: offer.preAuthorizedCode,
      subjectDid: holderDid,
    }),
  })
  const body = (await res.json().catch(() => ({}))) as WalletClaimResponse & {
    error?: string
    expectedSubjectDid?: string
    previewTitle?: string
    previewSubtitle?: string
  }
  if (!res.ok) {
    throw new Error(body.error ?? `Issuer HTTP ${res.status}`)
  }
  return {
    format: (body.format === 'vc-ld' ? 'vc-ld' : body.format === 'vc+sd-jwt' ? 'vc+sd-jwt' : 'vc+jwt') as CredentialFormat,
    credential: body.credential,
    issuer: body.issuer,
    subjectDid: body.subjectDid,
    type: body.type,
    selectivelyDisclosable: body.selectivelyDisclosable ?? [],
    preview: {
      title: body.previewTitle ?? body.preview?.title ?? 'Credential',
      subtitle: body.previewSubtitle ?? body.preview?.subtitle,
    },
  }
}

export async function claimIncomingCredentialOffer(
  parsed: IncomingCredentialOffer,
  holderDid: string,
  fallbackBaseUrl?: string,
): Promise<WalletClaimResponse> {
  try {
    if (parsed.protocol === 'trustweave-direct') {
      return await claimTrustWeaveDirect(parsed.offer, holderDid, fallbackBaseUrl)
    }
    return await claimOid4VciPreAuthorized(parsed, holderDid, fallbackBaseUrl)
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    if (msg.includes('Network request failed') || msg.includes('Failed to fetch')) {
      const target =
        parsed.protocol === 'trustweave-direct'
          ? resolveIssuerUrl(parsed.offer.issuerUrl, fallbackBaseUrl)
          : resolveIssuerUrl(parsed.issuerBase, fallbackBaseUrl)
      throw new Error(networkErrorHint(target, fallbackBaseUrl))
    }
    throw e
  }
}
