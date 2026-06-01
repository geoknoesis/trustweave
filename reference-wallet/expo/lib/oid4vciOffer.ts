/**
 * Parse OpenID4VCI credential offers (by-value in QR) for wallet redemption.
 * Supports the SaaS demo offer shape and standard pre-authorized_code grants.
 */
export interface Oid4VciPreAuthorizedOffer {
  issuerBase: string
  preAuthorizedCode: string
  credentialConfigurationIds: string[]
}

function parseQueryString(query: string): Map<string, string> {
  const params = new Map<string, string>()
  if (!query) return params
  for (const part of query.split('&')) {
    const [rawKey, rawVal = ''] = part.split('=')
    const key = decodeURIComponent(rawKey.replace(/\+/g, ' '))
    const val = decodeURIComponent(rawVal.replace(/\+/g, ' '))
    params.set(key, val)
  }
  return params
}

function extractPreAuthorizedCode(offer: Record<string, unknown>): string | null {
  const grants = offer.grants as Record<string, unknown> | undefined
  if (!grants) return null
  const preAuth = grants['urn:ietf:params:oauth:grant-type:pre-authorized_code'] as
    | { 'pre-authorized_code'?: string }
    | undefined
  const code = preAuth?.['pre-authorized_code']
  return typeof code === 'string' && code.trim() ? code.trim() : null
}

function parseOfferObject(raw: unknown): Oid4VciPreAuthorizedOffer | null {
  if (!raw || typeof raw !== 'object') return null
  const offer = raw as Record<string, unknown>
  const issuerBase = typeof offer.credential_issuer === 'string' ? offer.credential_issuer.trim() : ''
  const preAuthorizedCode = extractPreAuthorizedCode(offer)
  if (!issuerBase || !preAuthorizedCode) return null
  const configIds = Array.isArray(offer.credential_configuration_ids)
    ? offer.credential_configuration_ids.filter((v): v is string => typeof v === 'string')
    : []
  return { issuerBase, preAuthorizedCode, credentialConfigurationIds: configIds }
}

export function parseOid4VciCredentialOffer(raw: string): Oid4VciPreAuthorizedOffer | null {
  const trimmed = raw.trim()

  if (trimmed.startsWith('openid-credential-offer://')) {
    const query = trimmed.includes('?') ? trimmed.slice(trimmed.indexOf('?') + 1) : ''
    const params = parseQueryString(query)
    const encoded = params.get('credential_offer')
    if (encoded) {
      try {
        return parseOfferObject(JSON.parse(encoded) as Record<string, unknown>)
      } catch {
        return null
      }
    }
  }

  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    try {
      const url = new URL(trimmed)
      const encoded = url.searchParams.get('credential_offer')
      if (encoded) {
        return parseOfferObject(JSON.parse(encoded) as Record<string, unknown>)
      }
    } catch {
      return null
    }
  }

  try {
    const parsed = JSON.parse(trimmed) as Record<string, unknown>
    if (parsed.credential_issuer || parsed.grants) {
      return parseOfferObject(parsed)
    }
  } catch {
    return null
  }

  return null
}
