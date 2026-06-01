import { decodeSdJwtVc } from './sdjwt'
import type { StoredCredential } from './storage'

/** Resolve a displayable portrait src from disclosed or stored claims. */
export function portraitSrcFromClaims(claims: Record<string, unknown>): string | null {
  const embedded = claims.portrait ?? claims.picture
  if (typeof embedded === 'string' && embedded.startsWith('data:image/')) {
    return embedded
  }
  const url = claims.portraitUrl ?? claims.dronePhotoUrl
  if (typeof url === 'string' && url.length > 0) {
    return url
  }
  return null
}

/** Extract portrait URL from a stored SD-JWT VC (all disclosures visible to holder). */
export function portraitSrcFromStoredCredential(cred: StoredCredential): string | null {
  if (cred.format !== 'vc+sd-jwt') return null
  try {
    const decoded = decodeSdJwtVc(cred.credential)
    const claims: Record<string, unknown> = { ...decoded.issuerPayload }
    for (const d of decoded.disclosures) claims[d.name] = d.value
    return portraitSrcFromClaims(claims)
  } catch {
    return null
  }
}
