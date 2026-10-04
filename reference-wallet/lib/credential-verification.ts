import { b64uDecodeString, didKeyToPublicKey, parseJwsHeader, verifyJws } from './crypto'
import { decodeSdJwtVc } from './sdjwt'

/**
 * Decides which issuers the wallet is willing to accept credentials from. A valid did:key
 * signature only proves that some key signed the credential (anyone can mint such a key), so
 * verification also needs an explicit trust decision about the issuer.
 */
export interface IssuerTrustPolicy {
  isTrusted(issuerDid: string): boolean
}

export const IssuerTrustPolicy = {
  /** Trusts nothing; the default of [verifyImportedCredential] (fail closed). */
  NONE: { isTrusted: () => false } as IssuerTrustPolicy,
  /** Trusts exactly the listed issuer DIDs/IDs (exact match, no prefix matching). */
  allowList(trusted: Iterable<string | null | undefined>): IssuerTrustPolicy {
    const copy = new Set([...trusted].filter((id): id is string => typeof id === 'string' && id.length > 0))
    return { isTrusted: issuerDid => copy.has(issuerDid) }
  },
}

/**
 * The credential signature is valid but its issuer is not trusted. Carries the issuer so a UI can
 * ask the user to confirm it explicitly; nothing is trusted or persisted by throwing this.
 */
export class UntrustedIssuerError extends Error {
  constructor(readonly issuerDid: string) {
    super(`Issuer ${issuerDid} is not trusted by this wallet`)
    this.name = 'UntrustedIssuerError'
  }
}

export interface VerifyImportOptions {
  /** Which issuers are trusted. Defaults to [IssuerTrustPolicy.NONE]: untrusted issuers are rejected. */
  issuerPolicy?: IssuerTrustPolicy
  /**
   * Plain VC-JWT only: require `cnf.kid == sub` (holder key binding). Default off; SD-JWT VC
   * always requires it.
   */
  requireHolderKeyBinding?: boolean
  /**
   * When set, the credential's `sub` must equal this holder DID (parity with the Android wallet).
   * Omit only where no wallet holder exists, e.g. a verifier checking a presentation.
   */
  holderDid?: string
}

/**
 * Supported import profile: Ed25519 did:key issuer proofs from issuers accepted by
 * `options.issuerPolicy`; unsupported or untrusted issuers fail closed.
 */
export function verifyImportedCredential(
  compact: string,
  format: 'vc+jwt' | 'vc+sd-jwt',
  options: VerifyImportOptions = {},
): void {
  const issuerPolicy = options.issuerPolicy ?? IssuerTrustPolicy.NONE
  if (!['vc+jwt', 'vc+sd-jwt'].includes(format)) throw new Error('Unsupported credential format')
  if (new TextEncoder().encode(compact).byteLength > 1_048_576) throw new Error('Credential exceeds the 1 MiB import limit')
  if (format === 'vc+jwt' && compact.includes('~')) throw new Error('SD-JWT cannot be imported as a plain VC-JWT')
  if (format === 'vc+sd-jwt' && !compact.includes('~')) throw new Error('SD-JWT serialization requires a separator')
  const jwt = compact.split('~')[0]
  const untrusted = JSON.parse(b64uDecodeString(jwt.split('.')[1] ?? '')) as Record<string, unknown>
  if (!untrusted || Array.isArray(untrusted) || typeof untrusted !== 'object' || typeof untrusted.iss !== 'string') throw new Error('Credential issuer is missing')
  const header = parseJwsHeader(jwt)
  if (!header || Array.isArray(header) || header.alg !== 'EdDSA' || header.crit !== undefined || header.b64 !== undefined)
    throw new Error('Unsupported issuer signature profile')
  if (header.typ !== undefined && !['JWT', format].includes(String(header.typ))) throw new Error('Unsupported credential token type')
  const canonicalKid = `${untrusted.iss}#${untrusted.iss.slice('did:key:'.length)}`
  if (header.kid !== undefined && header.kid !== untrusted.iss && header.kid !== canonicalKid)
    throw new Error('Issuer signing key does not match the credential issuer')
  const payload = verifyJws(jwt, didKeyToPublicKey(untrusted.iss))
  if (!issuerPolicy.isTrusted(untrusted.iss)) throw new UntrustedIssuerError(untrusted.iss)
  const now = Math.floor(Date.now() / 1000)
  const object = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object' && !Array.isArray(value)
  if (typeof payload.sub !== 'string' || !payload.sub) throw new Error('Credential holder is missing')
  if (options.holderDid !== undefined && payload.sub !== options.holderDid)
    throw new Error(`Credential is bound to ${payload.sub}, not to this wallet's holder ${options.holderDid}`)
  if (format === 'vc+jwt') {
    if (!object(payload.vc) || payload.vct !== undefined || payload._sd !== undefined) throw new Error('Unsupported VC-JWT payload profile')
    const vc = payload.vc
    if (options.requireHolderKeyBinding) {
      const cnf = payload.cnf
      if (!object(cnf) || cnf.kid !== payload.sub || Object.keys(cnf).some(key => key !== 'kid'))
        throw new Error('Credential has no holder key binding (cnf.kid) for its holder')
    }
    if (!Array.isArray(vc.type) || !vc.type.length || vc.type.some(type => typeof type !== 'string' || !type)) throw new Error('Invalid credential types')
    if (vc.issuer !== undefined && vc.issuer !== payload.iss && (!object(vc.issuer) || vc.issuer.id !== payload.iss)) throw new Error('Conflicting credential issuer')
    if (vc.credentialSubject !== undefined && (!object(vc.credentialSubject) || (vc.credentialSubject.id !== undefined && vc.credentialSubject.id !== payload.sub))) throw new Error('Conflicting credential holder')
  } else {
    if (typeof payload.vct !== 'string' || !payload.vct || payload.vc !== undefined) throw new Error('Unsupported SD-JWT payload profile')
    if (!object(payload.cnf) || payload.cnf.kid !== payload.sub || Object.keys(payload.cnf).some(key => key !== 'kid')) throw new Error('Unsupported or conflicting holder key binding')
  }
  for (const claim of ['exp', 'nbf', 'iat']) {
    if (payload[claim] !== undefined && (typeof payload[claim] !== 'number' || !Number.isFinite(payload[claim])))
      throw new Error('Invalid credential time claim')
  }
  // Whole seconds, truncated toward zero, exactly like the Android wallet (Double.toLong()).
  const seconds = (claim: string): number | undefined => typeof payload[claim] === 'number' ? Math.trunc(payload[claim] as number) : undefined
  const exp = seconds('exp'), nbf = seconds('nbf'), iat = seconds('iat')
  if (exp !== undefined && exp <= now) throw new Error('Credential has expired')
  if (nbf !== undefined && nbf > now) throw new Error('Credential is not yet valid')
  if (iat !== undefined && iat > now) throw new Error('Credential issue time is in the future')
  if (exp !== undefined && ((nbf !== undefined && nbf >= exp) || (iat !== undefined && iat >= exp))) throw new Error('Inconsistent credential validity interval')
  if (format === 'vc+sd-jwt') {
    if (payload._sd_alg !== undefined && payload._sd_alg !== 'sha-256') throw new Error('Unsupported disclosure digest algorithm')
    const decoded = decodeSdJwtVc(compact)
    if (decoded.kbJwt) throw new Error('Import the issuer credential, not a holder presentation')
    const hashes = new Set<string>()
    const collect = (node: unknown, depth = 0) => {
      if (depth > 32) throw new Error('Credential nesting exceeds 32 levels')
      if (Array.isArray(node)) { node.forEach(value => collect(value, depth + 1)); return }
      if (node && typeof node === 'object') for (const [key,value] of Object.entries(node)) {
        if (key === '...') throw new Error('Array selective disclosures are not supported by this wallet')
        if (key === '_sd') {
          if (!Array.isArray(value) || value.some(item => typeof item !== 'string')) throw new Error('Invalid disclosure digests')
          if (depth !== 0) throw new Error('Nested selective disclosures are not supported by this wallet')
          value.forEach(item => {
            if (hashes.has(item)) throw new Error('Duplicate disclosure digest')
            hashes.add(item)
          })
        } else collect(value, depth + 1)
      }
    }
    collect(payload)
    const names = new Set<string>()
    const reserved = new Set(['iss', 'sub', 'iat', 'nbf', 'exp', 'vct', 'cnf', '_sd', '_sd_alg', '...', '__proto__', 'constructor', 'prototype'])
    for (const disclosure of decoded.disclosures) {
      if (!hashes.has(disclosure.hash) || names.has(disclosure.name) || reserved.has(disclosure.name) || Object.hasOwn(payload, disclosure.name)) throw new Error('Disclosure is not uniquely bound to the issuer signature')
      collect(disclosure.value, 1)
      names.add(disclosure.name)
    }
  }
}
