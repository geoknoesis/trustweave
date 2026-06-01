import { b64uDecodeString } from './crypto'
import { decodeSdJwtVc } from './sdjwt'
import type { StoredCredential } from './storage'

/** JWT / SD-JWT VC envelope fields — excluded from business-claim fingerprinting. */
const JWT_METADATA = new Set([
  'iss',
  'sub',
  'aud',
  'exp',
  'nbf',
  'iat',
  'jti',
  'cnf',
  '_sd',
  '_sd_alg',
  'vct',
  'vc',
  '@context',
  'type',
])

/** Well-known claim names that identify a credential instance across re-issuance. */
const IDENTIFIER_CLAIMS = [
  'id',
  'credentialId',
  'credential_id',
  'identifier',
  'studentId',
  'licenseNumber',
  'employeeId',
  'memberId',
  'accountId',
  'serialNumber',
  'number',
  'uuid',
]

function pickIdentifier(claims: Record<string, unknown>): string | null {
  for (const name of IDENTIFIER_CLAIMS) {
    const v = claims[name]
    if (v == null || v === '') continue
    const s = typeof v === 'string' ? v : JSON.stringify(v)
    if (s) return `${name}:${s}`
  }
  return null
}

function businessClaimsFingerprint(claims: Record<string, unknown>): string {
  const entries = Object.entries(claims)
    .filter(([k]) => !JWT_METADATA.has(k))
    .sort(([a], [b]) => a.localeCompare(b))
  return JSON.stringify(entries)
}

function typeKeyFromVcTypes(types: string[]): string {
  return types.find((x) => x !== 'VerifiableCredential') ?? types[0] ?? 'Credential'
}

function dedupKeyFromVcJwt(
  credential: string,
  fallback: Pick<StoredCredential, 'issuerDid' | 'subjectDid' | 'type'>,
): string {
  try {
    const parts = credential.split('.')
    if (parts.length !== 3) throw new Error('invalid VC-JWT')
    const payload = JSON.parse(b64uDecodeString(parts[1])) as Record<string, unknown>
    const vc = payload.vc as Record<string, unknown> | undefined
    const subject = (vc?.credentialSubject ?? {}) as Record<string, unknown>
    const issuerDid = String(payload.iss ?? vc?.issuer ?? fallback.issuerDid)
    const subjectDid = String(payload.sub ?? subject.id ?? fallback.subjectDid)
    const t = vc?.type
    const types = Array.isArray(t) ? t.map(String) : t ? [String(t)] : fallback.type
    const typeKey = typeKeyFromVcTypes(types)

    const stableId =
      (vc?.id ? `id:${String(vc.id)}` : null) ??
      pickIdentifier(subject) ??
      pickIdentifier(payload) ??
      `fp:${businessClaimsFingerprint(subject)}`

    return `${issuerDid}|${subjectDid}|${typeKey}|${stableId}`
  } catch {
    return `${fallback.issuerDid}|${fallback.subjectDid}|${typeKeyFromVcTypes(fallback.type)}|`
  }
}

function dedupKeyFromSdJwt(
  credential: string,
  fallback: Pick<StoredCredential, 'issuerDid' | 'subjectDid' | 'type'>,
): string {
  try {
    const decoded = decodeSdJwtVc(credential)
    const issuerDid = String(decoded.issuerPayload.iss ?? fallback.issuerDid)
    const subjectDid = String(decoded.issuerPayload.sub ?? fallback.subjectDid)
    const typeKey = String(decoded.issuerPayload.vct ?? typeKeyFromVcTypes(fallback.type))

    const disclosureClaims = Object.fromEntries(
      decoded.disclosures.map((d) => [d.name, d.value]),
    )
    const fingerprintClaims: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(decoded.issuerPayload)) {
      if (!JWT_METADATA.has(k)) fingerprintClaims[k] = v
    }
    for (const [k, v] of Object.entries(disclosureClaims)) {
      fingerprintClaims[k] = v
    }

    const stableId =
      pickIdentifier(decoded.issuerPayload) ??
      pickIdentifier(disclosureClaims) ??
      `fp:${businessClaimsFingerprint(fingerprintClaims)}`

    return `${issuerDid}|${subjectDid}|${typeKey}|${stableId}`
  } catch {
    return `${fallback.issuerDid}|${fallback.subjectDid}|${typeKeyFromVcTypes(fallback.type)}|`
  }
}

/**
 * Logical identity for deduplication — same credential for the same holder from the same issuer.
 * Works for any VC-JWT or SD-JWT VC: prefers explicit id claims, otherwise fingerprints business claims.
 */
export function credentialDedupKey(
  c: Pick<StoredCredential, 'format' | 'credential' | 'issuerDid' | 'subjectDid' | 'type'>,
): string {
  if (c.format === 'vc+sd-jwt') {
    return dedupKeyFromSdJwt(c.credential, c)
  }
  return dedupKeyFromVcJwt(c.credential, c)
}

export function findCredentialByDedupKey(
  credentials: StoredCredential[],
  key: string,
): StoredCredential | undefined {
  return credentials.find((entry) => credentialDedupKey(entry) === key)
}

/**
 * Business identity without holder subject — same issuer, type, and stable id claims.
 * Used to drop stale credentials after a wallet reset + re-receive (new holder DID).
 */
export function credentialBusinessKey(
  c: Pick<StoredCredential, 'format' | 'credential' | 'issuerDid' | 'subjectDid' | 'type'>,
): string {
  const full = credentialDedupKey(c)
  const parts = full.split('|')
  if (parts.length < 4) return full
  return `${parts[0]}|${parts[2]}|${parts.slice(3).join('|')}`
}
