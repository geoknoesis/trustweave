/**
 * Best-effort extraction of a cardholder portrait from a stored credential.
 *
 * The portrait is always sourced from the received credential — nothing is pre-bundled or pre-stored
 * in the wallet. Two issuer conventions are supported, and an inline image is always preferred:
 *  - Inline data URI (`data:image/...;base64,...`): the photo bytes are carried by the credential.
 *  - Hosted URL (`https?://...` under a portrait/photo/image key, e.g. `portraitUrl`): a reference the
 *    credential points at, fetched on demand. Only used as a fallback when no inline image is present.
 *
 * The claim may live in a VC-LD JSON body, a VC-JWT payload, or an SD-JWT disclosure, and may be
 * nested, so we walk the decoded credential.
 */
import type { StoredCredential } from './storage'
import { decodeSdJwtVc } from './sdjwt'
import { b64uDecodeString } from './crypto'

const IMAGE_KEY = /portrait|photo|image|picture|avatar/i

function isHttpUrl(s: string): boolean {
  return /^https?:\/\//i.test(s)
}

/** Finds an inline `data:image/...` value anywhere in the decoded credential. */
function findInlineImage(node: unknown): string | null {
  if (typeof node === 'string') {
    return node.startsWith('data:image/') ? node : null
  }
  if (Array.isArray(node)) {
    for (const item of node) {
      const found = findInlineImage(item)
      if (found) return found
    }
    return null
  }
  if (node && typeof node === 'object') {
    for (const value of Object.values(node as Record<string, unknown>)) {
      const found = findInlineImage(value)
      if (found) return found
    }
  }
  return null
}

/** Finds a hosted image URL under an image-ish key (fallback when no inline image exists). */
function findImageUrl(node: unknown, keyHint = ''): string | null {
  if (typeof node === 'string') {
    return IMAGE_KEY.test(keyHint) && isHttpUrl(node) ? node : null
  }
  if (Array.isArray(node)) {
    for (const item of node) {
      const found = findImageUrl(item, keyHint)
      if (found) return found
    }
    return null
  }
  if (node && typeof node === 'object') {
    for (const [key, value] of Object.entries(node as Record<string, unknown>)) {
      const found = findImageUrl(value, key)
      if (found) return found
    }
  }
  return null
}

function decodeJwtPayload(jwt: string): unknown {
  const parts = jwt.split('.')
  if (parts.length < 2) return null
  try {
    return JSON.parse(b64uDecodeString(parts[1]))
  } catch {
    return null
  }
}

/** Returns the credential's claims as a single searchable object. */
function decodeClaims(cred: StoredCredential): unknown {
  if (cred.format === 'vc+sd-jwt') {
    const decoded = decodeSdJwtVc(cred.credential)
    const disclosed = Object.fromEntries(decoded.disclosures.map((d) => [d.name, d.value]))
    return { ...decoded.issuerPayload, ...disclosed }
  }
  if (cred.format === 'vc+jwt') {
    return decodeJwtPayload(cred.credential)
  }
  return JSON.parse(cred.credential)
}

/**
 * Returns a portrait from the credential — an inline data URI when present (preferred), otherwise a
 * hosted image URL the credential references. Null when the credential carries no portrait.
 */
export function extractPortrait(cred: StoredCredential): string | null {
  try {
    const claims = decodeClaims(cred)
    return findInlineImage(claims) ?? findImageUrl(claims)
  } catch {
    return null
  }
}
