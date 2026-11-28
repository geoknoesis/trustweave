/**
 * Fail-closed issuer trust policy for the Expo mobile wallet.
 *
 * Parity with `reference-wallet/lib/issuer-trust.ts` (web) and
 * `reference-wallet/android/.../lib/IssuerTrustPolicy.kt` (Android):
 *
 * An issuer named by an offer, QR code, or credential response is NEVER trusted
 * merely for being named. Trust comes only from:
 *   1. Build-time allow-list (`expo.extra.trustedIssuers`, comma-separated DIDs),
 *   2. The wallet's own configured backend identity (`/api/demo-issuer/identity`),
 *   3. Issuers explicitly confirmed by the user (persisted only after every
 *      cryptographic check passes).
 */
import Constants from 'expo-constants'

export const BACKEND_IDENTITY_PATH = '/api/demo-issuer/identity'
export const MAX_ACCEPTED_ISSUERS = 200
export const MAX_DID_LENGTH = 256

const DID_SHAPE = /^did:[a-z0-9]+:[A-Za-z0-9._:%#-]+$/

export function isPlausibleIssuerDid(value: unknown): value is string {
  return typeof value === 'string' && value.length <= MAX_DID_LENGTH && DID_SHAPE.test(value)
}

export function sanitizeAcceptedIssuers(raw: unknown): string[] {
  if (!Array.isArray(raw)) return []
  const seen = new Set<string>()
  const out: string[] = []
  for (const item of raw) {
    if (!isPlausibleIssuerDid(item) || seen.has(item)) continue
    seen.add(item)
    out.push(item)
    if (out.length >= MAX_ACCEPTED_ISSUERS) break
  }
  return out
}

export function withAcceptedIssuer(current: string[], did: string): string[] {
  if (!isPlausibleIssuerDid(did)) {
    throw new Error('Not a valid issuer identifier')
  }
  const clean = sanitizeAcceptedIssuers(current)
  if (clean.includes(did)) return clean
  if (clean.length >= MAX_ACCEPTED_ISSUERS) {
    throw new Error(`At most ${MAX_ACCEPTED_ISSUERS} accepted issuers are supported. Remove one first.`)
  }
  return [...clean, did]
}

export function parseConfiguredIssuers(raw?: string | string[] | null): string[] {
  if (!raw) return []
  const items = Array.isArray(raw) ? raw : raw.split(',')
  return sanitizeAcceptedIssuers(items.map((item) => String(item).trim()))
}

export function configuredTrustedIssuers(): string[] {
  const extra = Constants.expoConfig?.extra as { trustedIssuers?: string | string[] } | undefined
  return parseConfiguredIssuers(extra?.trustedIssuers)
}

export interface IssuerTrustPolicy {
  isTrusted(issuerDid: string): boolean
}

export const IssuerTrustPolicy = {
  /** Trusts nothing; fail-closed default. */
  NONE: { isTrusted: () => false } as IssuerTrustPolicy,
  /** Trusts exact matches only (no prefix matching). */
  allowList(trusted: Iterable<string | null | undefined>): IssuerTrustPolicy {
    const allowed = new Set(
      [...trusted].filter((id): id is string => typeof id === 'string' && id.length > 0),
    )
    return { isTrusted: (issuerDid: string) => allowed.has(issuerDid) }
  },
}

/**
 * Thrown when a credential passes signature verification (or is structurally valid VC-LD)
 * but its issuer is not trusted by the active policy. Carries the signed issuer DID so the
 * UI can show a human-readable confirmation sheet. Nothing is trusted or saved by throwing.
 */
export class UntrustedIssuerError extends Error {
  constructor(
    readonly issuerDid: string,
    readonly credentialTitle?: string,
  ) {
    super(`Issuer ${issuerDid} is not trusted by this wallet`)
    this.name = 'UntrustedIssuerError'
  }
}

export type IssuerTrustSource = 'configured' | 'backend' | 'confirmed' | 'unverified'

export function resolveIssuerTrustSource(
  issuerDid: string,
  sources: {
    configured: Iterable<string>
    backend: Iterable<string>
    accepted: Iterable<string>
    confirmedIssuer?: string | null
  },
): IssuerTrustSource {
  const configuredSet = new Set(sources.configured)
  if (configuredSet.has(issuerDid)) return 'configured'
  const backendSet = new Set(sources.backend)
  if (backendSet.has(issuerDid)) return 'backend'
  const acceptedSet = new Set(sources.accepted)
  if (acceptedSet.has(issuerDid) || sources.confirmedIssuer === issuerDid) return 'confirmed'
  return 'unverified'
}

export function buildIssuerTrustPolicy(sources: {
  configured: Iterable<string>
  backend: Iterable<string>
  accepted: Iterable<string>
  confirmedIssuer?: string | null
}): IssuerTrustPolicy {
  return IssuerTrustPolicy.allowList([
    ...sources.configured,
    ...sources.backend,
    ...sources.accepted,
    ...(sources.confirmedIssuer ? [sources.confirmedIssuer] : []),
  ])
}

/**
 * Fetch the issuer DIDs belonging to the wallet's own configured backend.
 * Fails closed: network, status, or shape errors yield an empty array.
 */
export async function loadBackendIssuers(
  baseUrl: string,
  fetchImpl: typeof fetch | undefined = typeof fetch === 'function' ? fetch : undefined,
): Promise<string[]> {
  if (!fetchImpl || !baseUrl) return []
  try {
    const cleanBase = baseUrl.replace(/\/$/, '')
    const response = await fetchImpl(`${cleanBase}${BACKEND_IDENTITY_PATH}`)
    if (!response.ok) return []
    const body = (await response.json()) as { issuerDids?: unknown } | null
    const dids = body?.issuerDids
    if (!Array.isArray(dids) || dids.length > 16 || !dids.every(isPlausibleIssuerDid)) {
      return []
    }
    return dids
  } catch {
    return []
  }
}

/** Short human-scannable fingerprint for an issuer DID (e.g. `z6Mk…9a4F`). */
export function shortDidFingerprint(did: string): string {
  if (!did) return 'Unknown'
  const lastSegment = did.split(':').pop() ?? did
  if (lastSegment.length <= 12) return lastSegment
  return `${lastSegment.slice(0, 6)}…${lastSegment.slice(-4)}`
}
