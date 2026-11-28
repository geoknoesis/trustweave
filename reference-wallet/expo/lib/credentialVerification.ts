/**
 * Fail-closed credential import verification for the Expo mobile wallet.
 *
 * Parity with `reference-wallet/lib/credential-verification.ts` (web) and
 * `reference-wallet/android/.../lib/CredentialVerification.kt` (Android).
 *
 * Supported cryptographic import profile:
 *   - Ed25519 `did:key` issuer proofs (`alg=EdDSA`) for `vc+jwt` and `vc+sd-jwt`.
 *   - Fail-closed issuer trust policy (throws `UntrustedIssuerError` when issuer
 *     is valid cryptographically but not yet trusted).
 *   - Holder binding (`sub`, plus `cnf.kid` for SD-JWT VC and optional for VC-JWT).
 *   - Whole-second `exp`/`nbf`/`iat` checks.
 *   - SD-JWT VC disclosure digest checks against signed top-level `_sd` array.
 */
import { b64uDecodeString, didKeyToPublicKey, parseJwsHeader, verifyJws } from './crypto'
import { IssuerTrustPolicy, UntrustedIssuerError } from './issuerTrust'
import { decodeSdJwtVc } from './sdjwt'

const MAX_BYTES = 1_048_576
const RESERVED_DISCLOSURE_NAMES = new Set([
  'iss',
  'sub',
  'iat',
  'nbf',
  'exp',
  'vct',
  'cnf',
  '_sd',
  '_sd_alg',
  '...',
  '__proto__',
  'constructor',
  'prototype',
])

export interface VerifyImportOptions {
  /** Which issuers are trusted. Defaults to `IssuerTrustPolicy.NONE` (fail closed). */
  issuerPolicy?: IssuerTrustPolicy
  /** Plain VC-JWT only: require `cnf.kid == sub`. SD-JWT VC always requires it. */
  requireHolderKeyBinding?: boolean
  /** Expected holder DID bound to this wallet. */
  holderDid?: string
  /** Optional override for current epoch seconds (testing). */
  nowEpochSeconds?: number
  /** Optional display title forwarded into UntrustedIssuerError for the trust prompt. */
  credentialTitle?: string
}

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value)

export function verifyImportedCredential(
  compact: string,
  format: 'vc+jwt' | 'vc+sd-jwt' | 'vc-ld',
  options: VerifyImportOptions = {},
): void {
  const issuerPolicy = options.issuerPolicy ?? IssuerTrustPolicy.NONE
  if (new TextEncoder().encode(compact).byteLength > MAX_BYTES) {
    throw new Error('Credential exceeds the 1 MiB import limit')
  }

  if (format === 'vc-ld') {
    verifyVcLdImport(compact, issuerPolicy, options.holderDid, options.credentialTitle)
    return
  }

  if (format !== 'vc+jwt' && format !== 'vc+sd-jwt') {
    throw new Error('Unsupported credential format')
  }
  if (format === 'vc+jwt' && compact.includes('~')) {
    throw new Error('SD-JWT cannot be imported as a plain VC-JWT')
  }
  if (format === 'vc+sd-jwt' && !compact.includes('~')) {
    throw new Error('SD-JWT serialization requires a separator')
  }

  const jwt = compact.split('~')[0]
  const segments = jwt.split('.')
  if (segments.length !== 3) {
    throw new Error('Issuer credential is not a compact JWS')
  }

  let untrusted: unknown
  try {
    untrusted = JSON.parse(b64uDecodeString(segments[1]))
  } catch {
    throw new Error('Credential payload is not valid JSON')
  }
  if (!isRecord(untrusted) || typeof untrusted.iss !== 'string' || !untrusted.iss) {
    throw new Error('Credential issuer is missing')
  }

  const header = parseJwsHeader(jwt)
  if (!header || header.alg !== 'EdDSA' || header.crit !== undefined || header.b64 !== undefined) {
    throw new Error('Unsupported issuer signature profile')
  }
  if (header.typ !== undefined && !['JWT', format].includes(String(header.typ))) {
    throw new Error('Unsupported credential token type')
  }
  const canonicalKid = `${untrusted.iss}#${untrusted.iss.slice('did:key:'.length)}`
  if (header.kid !== undefined && header.kid !== untrusted.iss && header.kid !== canonicalKid) {
    throw new Error('Issuer signing key does not match the credential issuer')
  }

  const payload = verifyJws(jwt, didKeyToPublicKey(untrusted.iss))
  const titleHint =
    options.credentialTitle ??
    (typeof payload.vct === 'string'
      ? payload.vct
      : isRecord(payload.vc) && Array.isArray(payload.vc.type)
        ? String(payload.vc.type.find((t) => t !== 'VerifiableCredential') ?? 'Credential')
        : undefined)

  if (!issuerPolicy.isTrusted(untrusted.iss)) {
    throw new UntrustedIssuerError(untrusted.iss, titleHint)
  }

  if (typeof payload.sub !== 'string' || !payload.sub) {
    throw new Error('Credential holder is missing')
  }
  if (options.holderDid !== undefined && payload.sub !== options.holderDid) {
    throw new Error('This credential was issued to a different wallet identity.')
  }

  if (format === 'vc+jwt') {
    if (!isRecord(payload.vc) || payload.vct !== undefined || payload._sd !== undefined) {
      throw new Error('Unsupported VC-JWT payload profile')
    }
    const vc = payload.vc
    if (options.requireHolderKeyBinding) {
      const cnf = payload.cnf
      if (!isRecord(cnf) || cnf.kid !== payload.sub || Object.keys(cnf).some((k) => k !== 'kid')) {
        throw new Error('Credential has no holder key binding (cnf.kid) for its holder')
      }
    }
    if (
      !Array.isArray(vc.type) ||
      vc.type.length === 0 ||
      vc.type.some((type) => typeof type !== 'string' || !type)
    ) {
      throw new Error('Invalid credential types')
    }
    if (
      vc.issuer !== undefined &&
      vc.issuer !== payload.iss &&
      (!isRecord(vc.issuer) || vc.issuer.id !== payload.iss)
    ) {
      throw new Error('Conflicting credential issuer')
    }
    if (
      vc.credentialSubject !== undefined &&
      (!isRecord(vc.credentialSubject) ||
        (vc.credentialSubject.id !== undefined && vc.credentialSubject.id !== payload.sub))
    ) {
      throw new Error('Conflicting credential holder')
    }
  } else {
    if (typeof payload.vct !== 'string' || !payload.vct || payload.vc !== undefined) {
      throw new Error('Unsupported SD-JWT payload profile')
    }
    if (
      !isRecord(payload.cnf) ||
      payload.cnf.kid !== payload.sub ||
      Object.keys(payload.cnf).some((k) => k !== 'kid')
    ) {
      throw new Error('Unsupported or conflicting holder key binding')
    }
  }

  for (const claim of ['exp', 'nbf', 'iat'] as const) {
    if (
      payload[claim] !== undefined &&
      (typeof payload[claim] !== 'number' || !Number.isFinite(payload[claim]))
    ) {
      throw new Error('Invalid credential time claim')
    }
  }

  const now = options.nowEpochSeconds ?? Math.floor(Date.now() / 1000)
  const seconds = (claim: 'exp' | 'nbf' | 'iat'): number | undefined =>
    typeof payload[claim] === 'number' ? Math.trunc(payload[claim]) : undefined
  const exp = seconds('exp')
  const nbf = seconds('nbf')
  const iat = seconds('iat')

  if (exp !== undefined && exp <= now) throw new Error('This credential has expired.')
  if (nbf !== undefined && nbf > now) throw new Error('This credential is not valid yet.')
  if (iat !== undefined && iat > now) throw new Error('Credential issue time is in the future.')
  if (exp !== undefined && ((nbf !== undefined && nbf >= exp) || (iat !== undefined && iat >= exp))) {
    throw new Error('Inconsistent credential validity interval')
  }

  if (format === 'vc+sd-jwt') {
    verifySdJwtDisclosures(compact, payload)
  }
}

function verifySdJwtDisclosures(compact: string, payload: Record<string, unknown>): void {
  if (payload._sd_alg !== undefined && payload._sd_alg !== 'sha-256') {
    throw new Error('Unsupported disclosure digest algorithm')
  }
  const decoded = decodeSdJwtVc(compact)
  if (decoded.kbJwt) {
    throw new Error('Import the issuer credential, not a holder presentation')
  }

  const hashes = new Set<string>()
  const collect = (node: unknown, depth = 0): void => {
    if (depth > 32) throw new Error('Credential nesting exceeds 32 levels')
    if (Array.isArray(node)) {
      node.forEach((value) => collect(value, depth + 1))
      return
    }
    if (node && typeof node === 'object') {
      for (const [key, value] of Object.entries(node)) {
        if (key === '...') throw new Error('Array selective disclosures are not supported by this wallet')
        if (key === '_sd') {
          if (!Array.isArray(value) || value.some((item) => typeof item !== 'string')) {
            throw new Error('Invalid disclosure digests')
          }
          if (depth !== 0) throw new Error('Nested selective disclosures are not supported by this wallet')
          for (const item of value) {
            if (hashes.has(item)) throw new Error('Duplicate disclosure digest')
            hashes.add(item)
          }
        } else {
          collect(value, depth + 1)
        }
      }
    }
  }
  collect(payload)

  const names = new Set<string>()
  for (const disclosure of decoded.disclosures) {
    if (
      !hashes.has(disclosure.hash) ||
      names.has(disclosure.name) ||
      RESERVED_DISCLOSURE_NAMES.has(disclosure.name) ||
      Object.prototype.hasOwnProperty.call(payload, disclosure.name)
    ) {
      throw new Error('Disclosure is not uniquely bound to the issuer signature')
    }
    collect(disclosure.value, 1)
    names.add(disclosure.name)
  }
}

/**
 * VC-LD credentials cannot be cryptographically signature-verified locally in this
 * lightweight mobile profile, so we still fail closed on issuer trust and holder binding.
 */
function verifyVcLdImport(
  rawJson: string,
  issuerPolicy: IssuerTrustPolicy,
  holderDid?: string,
  titleHint?: string,
): void {
  let parsed: unknown
  try {
    parsed = JSON.parse(rawJson)
  } catch {
    throw new Error('Credential JSON is malformed')
  }
  if (!isRecord(parsed)) throw new Error('Credential payload is not a JSON object')
  const issuer = parsed.issuer
  const issuerDid =
    typeof issuer === 'string' ? issuer : isRecord(issuer) && typeof issuer.id === 'string' ? issuer.id : ''
  if (!issuerDid) throw new Error('Credential issuer is missing')
  const types = Array.isArray(parsed.type) ? parsed.type.map(String) : []
  const fallbackTitle = titleHint ?? types.find((t) => t !== 'VerifiableCredential') ?? 'Credential'
  if (!issuerPolicy.isTrusted(issuerDid)) {
    throw new UntrustedIssuerError(issuerDid, fallbackTitle)
  }
  const subject = isRecord(parsed.credentialSubject) ? parsed.credentialSubject : undefined
  const subjectId = typeof subject?.id === 'string' ? subject.id : undefined
  if (holderDid && subjectId && subjectId !== holderDid) {
    throw new Error('This credential was issued to a different wallet identity.')
  }
}
