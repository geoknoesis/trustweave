import { isPlausibleIssuerDid, loadAcceptedIssuers } from './storage'

/** Same-origin endpoint listing the issuer DIDs of the wallet's own configured backend. */
export const BACKEND_IDENTITY_PATH = '/api/demo-issuer/identity'

/**
 * Issuers named in the build-time allow-list (`NEXT_PUBLIC_TRUSTED_ISSUERS`, comma-separated DIDs).
 * `process` exists under Next (which inlines NEXT_PUBLIC_*) but not in a bare browser module graph.
 */
export function configuredTrustedIssuers(): string[] {
  const raw = (typeof process !== 'undefined' ? process.env.NEXT_PUBLIC_TRUSTED_ISSUERS : undefined) ?? ''
  return raw.split(',').map(id => id.trim()).filter(id => id.length > 0)
}

let backendIssuers: Promise<string[]> | undefined

/**
 * The issuer DIDs of the wallet's own configured backend, read from a same-origin configuration
 * endpoint. They are NEVER taken from an offer or credential response. Fails closed: any network,
 * status or shape problem yields an empty list (and is retried on the next call).
 */
export function loadBackendIssuers(fetchImpl: typeof fetch | undefined = typeof fetch === 'function' ? fetch : undefined): Promise<string[]> {
  if (!fetchImpl) return Promise.resolve([])
  if (!backendIssuers) {
    const attempt = (async () => {
      const response = await fetchImpl(BACKEND_IDENTITY_PATH, { cache: 'no-store', credentials: 'omit' })
      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const body: unknown = await response.json()
      const dids = (body as { issuerDids?: unknown } | null)?.issuerDids
      if (!Array.isArray(dids) || dids.length > 16 || !dids.every(isPlausibleIssuerDid)) throw new Error('Unexpected backend identity shape')
      return dids as string[]
    })()
    backendIssuers = attempt
    attempt.catch(() => { if (backendIssuers === attempt) backendIssuers = undefined })
  }
  return backendIssuers.catch(() => [])
}

/** Test hook: forget the cached backend identity. */
export function resetBackendIssuersCache(): void { backendIssuers = undefined }

/** Where an issuer's trust comes from, for display in the issuer review UI. */
export type IssuerTrustSource = 'configured' | 'accepted'

/** Everything the user can review: configured entries are read-only, accepted ones can be removed. */
export function reviewableIssuers(): Array<{ did: string; source: IssuerTrustSource }> {
  const configured = configuredTrustedIssuers()
  const accepted = loadAcceptedIssuers().filter(did => !configured.includes(did))
  return [...configured.map(did => ({ did, source: 'configured' as const })), ...accepted.map(did => ({ did, source: 'accepted' as const }))]
}
