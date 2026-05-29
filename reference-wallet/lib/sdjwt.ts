/**
 * SD-JWT and SD-JWT VC helpers (IETF drafts).
 *
 * Spec references:
 *  - draft-ietf-oauth-selective-disclosure-jwt (SD-JWT core)
 *  - draft-ietf-oauth-sd-jwt-vc (SD-JWT VC profile)
 *
 * Format: `<issuer-jwt>~<disclosure1>~<disclosure2>~...~[<kb-jwt>]`
 *  - issuer-jwt: a JWS whose payload contains a `_sd` array of disclosure hashes
 *    in place of selectively-disclosable claims.
 *  - disclosure: base64url(JSON.stringify([salt, claim_name, claim_value])).
 *  - kb-jwt: optional key-binding JWT signed by the holder over the audience +
 *    nonce + sd_hash (SHA-256 of the SD-JWT presentation prefix). Required for
 *    presentation; absent on issuance.
 *
 * We use SHA-256 for `_sd_alg` (the spec default) and EdDSA (Ed25519) for both
 * the issuer JWT and the KB-JWT.
 */
import { sha256 } from '@noble/hashes/sha256'
import {
  b64uEncode,
  b64uEncodeString,
  b64uDecodeString,
  signEd25519,
  signJws,
} from './crypto'

/** SHA-256 of a UTF-8 string. */
function sha256OfString(s: string): Uint8Array {
  return sha256(new TextEncoder().encode(s))
}

/** SD-JWT spec hash function: `_sd_alg`="sha-256" → base64url(SHA-256(disclosure)). */
export function disclosureHash(disclosure: string): string {
  return b64uEncode(sha256OfString(disclosure))
}

/** Cryptographically-random salt for a disclosure. RFC recommends ≥128 bits. */
function randomSalt(): string {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return b64uEncode(bytes)
}

/**
 * Build one disclosure for an object-property claim: `base64url([salt, name, value])`.
 * Values can be any JSON.
 */
export function createObjectDisclosure(
  name: string,
  value: unknown,
): { disclosure: string; hash: string; salt: string } {
  const salt = randomSalt()
  const disclosureJson = JSON.stringify([salt, name, value])
  const disclosure = b64uEncodeString(disclosureJson)
  return { disclosure, hash: disclosureHash(disclosure), salt }
}

/** Parse a disclosure back to its [salt, name, value] tuple. */
export function parseDisclosure(d: string): [string, string, unknown] {
  const arr = JSON.parse(b64uDecodeString(d)) as unknown[]
  if (arr.length !== 3) {
    throw new Error(`Object disclosure must be [salt, name, value], got length ${arr.length}`)
  }
  return [String(arr[0]), String(arr[1]), arr[2]]
}

/** A claim the issuer is willing to selectively disclose. */
export interface SelectivelyDisclosableClaim {
  name: string
  value: unknown
}

/**
 * Issue an SD-JWT VC.
 *
 * Caller provides:
 *  - the always-visible claims (placed directly in the JWT payload)
 *  - the selectively-disclosable claims (replaced by `_sd` hashes in the payload,
 *    with the disclosures appended after the JWT)
 *
 * Returns the compact form `<jwt>~<d1>~<d2>~...`.
 */
export function issueSdJwtVc(args: {
  issuerDid: string
  issuerPrivateKey: Uint8Array
  issuerKid: string
  holderDid: string
  alwaysVisible: Record<string, unknown>
  selectivelyDisclosable: SelectivelyDisclosableClaim[]
  vct: string  // SD-JWT VC type identifier (the `vct` claim, replaces `vc.type`)
  now: number
  oneYear?: number
}): string {
  const oneYear = args.oneYear ?? 365 * 24 * 60 * 60
  const disclosures: string[] = []
  const sdHashes: string[] = []
  for (const c of args.selectivelyDisclosable) {
    const d = createObjectDisclosure(c.name, c.value)
    disclosures.push(d.disclosure)
    sdHashes.push(d.hash)
  }

  const payload = {
    iss: args.issuerDid,
    iat: args.now,
    nbf: args.now,
    exp: args.now + oneYear,
    vct: args.vct,
    sub: args.holderDid,
    // The holder key binding will be checked at presentation time against this `cnf`.
    // We bind to the holder's did:key directly; a richer impl would publish a JWK.
    cnf: { kid: args.holderDid },
    // Selectively-disclosable claims live here as hashes.
    _sd: sdHashes,
    _sd_alg: 'sha-256',
    ...args.alwaysVisible,
  }

  const issuerJwt = signJws(payload, args.issuerPrivateKey, args.issuerKid)
  return [issuerJwt, ...disclosures].join('~')
}

/**
 * Build a holder presentation from an issuer-issued SD-JWT VC.
 *
 * `selectDisclose` is the set of claim names the holder is willing to reveal.
 * Disclosures whose name is NOT in the set are dropped (withheld).
 *
 * Appends a key-binding JWT signed by the holder, binding to the verifier's
 * `aud` and `nonce` plus the SHA-256 of the SD-JWT presentation prefix
 * (jwt~selectedDisclosure1~...~). This is the spec's `sd_hash` claim.
 */
export function presentSdJwtVc(args: {
  sdJwtVc: string
  selectDisclose: string[]
  holderPrivateKey: Uint8Array
  holderDid: string
  audience: string
  nonce: string
  now: number
}): string {
  const parts = args.sdJwtVc.split('~').filter((p) => p.length > 0)
  if (parts.length < 1) throw new Error('Empty SD-JWT VC')
  const issuerJwt = parts[0]
  const allDisclosures = parts.slice(1)

  const selected: string[] = []
  for (const d of allDisclosures) {
    const [, name] = parseDisclosure(d)
    if (args.selectDisclose.includes(name)) {
      selected.push(d)
    }
  }

  // Build the prefix the KB-JWT signs over.
  const prefix = [issuerJwt, ...selected, ''].join('~')
  const sdHash = b64uEncode(sha256OfString(prefix))

  const kbPayload = {
    iat: args.now,
    aud: args.audience,
    nonce: args.nonce,
    sd_hash: sdHash,
  }
  // KB-JWT is a compact JWS with typ=kb+jwt per the spec.
  const kbJwt = signJwsWithTyp(
    kbPayload,
    args.holderPrivateKey,
    args.holderDid,
    'kb+jwt',
  )

  return prefix + kbJwt
}

/**
 * Like signJws() but allows a custom `typ` header (e.g. "kb+jwt").
 * Duplicates a small amount of code from crypto.ts to keep that file
 * focused on plain VC-JWT.
 */
function signJwsWithTyp(
  payload: Record<string, unknown>,
  privateKey: Uint8Array,
  kid: string,
  typ: string,
): string {
  const header = { alg: 'EdDSA', typ, kid }
  const encodedHeader = b64uEncodeString(JSON.stringify(header))
  const encodedPayload = b64uEncodeString(JSON.stringify(payload))
  const signingInput = `${encodedHeader}.${encodedPayload}`
  const signature = signEd25519(signingInput, privateKey)
  return `${signingInput}.${b64uEncode(signature)}`
}

/**
 * Decode an SD-JWT VC into its constituent parts (without verifying anything).
 *
 * Useful both to the holder (to know what claims it CAN disclose) and to the
 * verifier (as an intermediate step before signature/hash checks).
 */
export interface DecodedSdJwtVc {
  issuerJwt: string
  issuerPayload: Record<string, unknown>
  disclosures: Array<{ raw: string; name: string; value: unknown; hash: string }>
  kbJwt?: string
}

export function decodeSdJwtVc(sdJwtVc: string): DecodedSdJwtVc {
  const parts = sdJwtVc.split('~')
  if (parts.length < 1) throw new Error('Empty SD-JWT VC')
  const issuerJwt = parts[0]
  // The last segment is empty if there's no KB-JWT; non-empty if there is one.
  // disclosures are everything in between, non-empty strings only.
  let kbJwt: string | undefined
  let lastIdx = parts.length - 1
  if (parts[lastIdx] !== '') {
    // It's a KB-JWT.
    kbJwt = parts[lastIdx]
    lastIdx -= 1
  }
  const disclosureSegments = parts.slice(1, lastIdx + 1).filter((p) => p.length > 0)
  const disclosures = disclosureSegments.map((raw) => {
    const [, name, value] = parseDisclosure(raw)
    return { raw, name, value, hash: disclosureHash(raw) }
  })

  // Decode the issuer JWT payload (without verifying signature).
  const jwtParts = issuerJwt.split('.')
  if (jwtParts.length !== 3) throw new Error('Issuer JWT must have three parts')
  const issuerPayload = JSON.parse(b64uDecodeString(jwtParts[1])) as Record<string, unknown>

  return { issuerJwt, issuerPayload, disclosures, kbJwt }
}
