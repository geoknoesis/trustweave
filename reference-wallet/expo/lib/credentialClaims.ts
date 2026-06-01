/**
 * Decode a stored credential into human-displayable claim rows for the holder's
 * own detail view.
 *
 * Nested objects/arrays are flattened into an indented tree of rows (a group
 * header with indented sub-rows) rather than dumped as raw JSON, so the display
 * stays readable. Portrait/image claims and JWT/SD-JWT envelope fields are
 * omitted (the portrait is shown separately as a photo). Sync — the Expo demo
 * carries no JWE claims.
 */
import { decodeSdJwtVc } from './sdjwt'
import { b64uDecodeString } from './crypto'
import { humanClaimName } from './credentialDisplay'
import type { StoredCredential } from './storage'

export interface ClaimRow {
  /** Stable React key. */
  key: string
  /** Humanised label. */
  label: string
  /** Primitive value as text, or `null` for a group header (object/array). */
  value: string | null
  /** Indentation level (0 = top-level claim). */
  depth: number
  /** True when this top-level claim is selectively disclosable. */
  shareable: boolean
}

const ENVELOPE_KEYS = new Set([
  'iss', 'sub', 'aud', 'iat', 'exp', 'nbf', 'jti', 'cnf',
  'vct', 'vc', 'type', '@context', 'id', '_sd', '_sd_alg', 'status', 'proof',
])

const IMAGE_KEY = /portrait|photo|image|picture|avatar/i
const MAX_DEPTH = 4

function isImageish(name: string, value: unknown): boolean {
  if (IMAGE_KEY.test(name)) return true
  if (typeof value === 'string' && value.startsWith('data:image/')) return true
  return false
}

function isPrimitive(v: unknown): v is string | number | boolean {
  return typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean'
}

/** Recursively turn one claim into one or more rows. */
function flatten(rawName: string, value: unknown, depth: number, shareable: boolean, keyPath: string, out: ClaimRow[]): void {
  if (value === null || value === undefined) return
  if (isImageish(rawName, value)) return

  const label = humanClaimName(rawName)

  if (isPrimitive(value)) {
    const text = String(value)
    if (text === '') return
    out.push({ key: keyPath, label, value: text, depth, shareable })
    return
  }

  if (Array.isArray(value)) {
    if (value.length === 0) return
    if (value.every(isPrimitive)) {
      out.push({ key: keyPath, label, value: value.map(String).join(', '), depth, shareable })
      return
    }
    out.push({ key: keyPath, label, value: null, depth, shareable })
    if (depth >= MAX_DEPTH) return
    value.forEach((item, i) => flatten(`${rawName} ${i + 1}`, item, depth + 1, false, `${keyPath}.${i}`, out))
    return
  }

  const entries = Object.entries(value as Record<string, unknown>)
  const shown = entries.filter(([k, v]) => !isImageish(k, v) && v !== null && v !== undefined)
  if (shown.length === 0) return
  out.push({ key: keyPath, label, value: null, depth, shareable })
  if (depth >= MAX_DEPTH) return
  for (const [k, v] of shown) flatten(k, v, depth + 1, false, `${keyPath}.${k}`, out)
}

function rawClaimsFor(cred: StoredCredential): Record<string, unknown> {
  const claims: Record<string, unknown> = {}
  if (cred.format === 'vc+sd-jwt') {
    const decoded = decodeSdJwtVc(cred.credential)
    for (const [k, v] of Object.entries(decoded.issuerPayload)) claims[k] = v
    for (const d of decoded.disclosures) claims[d.name] = d.value
  } else if (cred.format === 'vc-ld') {
    const parsed = JSON.parse(cred.credential) as Record<string, unknown>
    const subject = (parsed.credentialSubject ?? {}) as Record<string, unknown>
    for (const [k, v] of Object.entries(subject)) claims[k] = v
  } else {
    const parts = cred.credential.split('.')
    if (parts.length === 3) {
      const payload = JSON.parse(b64uDecodeString(parts[1])) as Record<string, unknown>
      const vc = (payload.vc ?? {}) as Record<string, unknown>
      const subject = (vc.credentialSubject ?? {}) as Record<string, unknown>
      for (const [k, v] of Object.entries(subject)) claims[k] = v
    }
  }
  return claims
}

/** Decode the credential into a flat, indentation-aware list of display rows.
 *  Never throws — returns `[]` on a malformed credential. */
export function extractClaimsForDisplay(cred: StoredCredential): ClaimRow[] {
  let raw: Record<string, unknown>
  try {
    raw = rawClaimsFor(cred)
  } catch {
    return []
  }

  const shareable = new Set(cred.selectivelyDisclosable)
  const rows: ClaimRow[] = []
  for (const [name, value] of Object.entries(raw)) {
    if (ENVELOPE_KEYS.has(name)) continue
    if (isImageish(name, value)) continue
    flatten(name, value, 0, shareable.has(name), name, rows)
  }
  return rows
}
