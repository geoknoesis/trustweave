import { b64uDecodeString, didKeyToPublicKey, parseJwsHeader, verifyJws } from './crypto'
import { decodeSdJwtVc } from './sdjwt'

/** Supported import profile: Ed25519 did:key issuer proofs; unsupported issuers fail closed. */
export function verifyImportedCredential(compact: string, format: 'vc+jwt' | 'vc+sd-jwt'): void {
  if (compact.length > 1_048_576) throw new Error('Credential exceeds the 1 MiB import limit')
  const jwt = compact.split('~')[0]
  const untrusted = JSON.parse(b64uDecodeString(jwt.split('.')[1] ?? '')) as Record<string, unknown>
  if (typeof untrusted.iss !== 'string') throw new Error('Credential issuer is missing')
  const header = parseJwsHeader(jwt)
  if (header.alg !== 'EdDSA' || header.crit !== undefined) throw new Error('Unsupported issuer signature profile')
  if (header.kid !== undefined && (typeof header.kid !== 'string' || header.kid.split('#')[0] !== untrusted.iss))
    throw new Error('Issuer signing key does not match the credential issuer')
  const payload = verifyJws(jwt, didKeyToPublicKey(untrusted.iss))
  const now = Math.floor(Date.now() / 1000)
  for (const claim of ['exp', 'nbf', 'iat']) {
    if (payload[claim] !== undefined && (typeof payload[claim] !== 'number' || !Number.isFinite(payload[claim])))
      throw new Error('Invalid credential time claim')
  }
  if (typeof payload.exp === 'number' && payload.exp <= now) throw new Error('Credential has expired')
  if (typeof payload.nbf === 'number' && payload.nbf > now) throw new Error('Credential is not yet valid')
  if (format === 'vc+sd-jwt') {
    if (payload._sd_alg !== undefined && payload._sd_alg !== 'sha-256') throw new Error('Unsupported disclosure digest algorithm')
    const decoded = decodeSdJwtVc(compact)
    if (decoded.kbJwt) throw new Error('Import the issuer credential, not a holder presentation')
    const hashes = new Set<string>()
    const collect = (node: unknown, depth = 0) => {
      if (depth > 32) throw new Error('Credential nesting exceeds 32 levels')
      if (Array.isArray(node)) { node.forEach(value => collect(value, depth + 1)); return }
      if (node && typeof node === 'object') for (const [key,value] of Object.entries(node)) {
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
    for (const disclosure of decoded.disclosures) {
      if (!hashes.has(disclosure.hash) || names.has(disclosure.name) || Object.hasOwn(payload, disclosure.name)) throw new Error('Disclosure is not uniquely bound to the issuer signature')
      collect(disclosure.value, 1)
      names.add(disclosure.name)
    }
  }
}
