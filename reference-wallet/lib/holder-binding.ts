import { decodeSdJwtVc } from './sdjwt'
import type { StoredCredential } from './storage'

export const HOLDER_BINDING_MISMATCH =
  'This credential belongs to a different wallet identity. Delete it and scan the issuer QR again to receive a fresh copy for this wallet.'

/** Holder DID bound in an SD-JWT VC issuer payload (`cnf.kid`, falling back to `sub`). */
export function boundHolderDidFromSdJwt(credential: string): string {
  const decoded = decodeSdJwtVc(credential)
  const cnf = decoded.issuerPayload.cnf as { kid?: string } | undefined
  return String(cnf?.kid ?? decoded.issuerPayload.sub ?? '')
}

/** Whether [credential] can be presented by [holderDid]'s current key. */
export function isCredentialBoundToHolder(
  cred: Pick<StoredCredential, 'format' | 'credential' | 'subjectDid'>,
  holderDid: string,
): boolean {
  if (cred.format === 'vc+sd-jwt') {
    try {
      return boundHolderDidFromSdJwt(cred.credential) === holderDid
    } catch {
      return false
    }
  }
  return cred.subjectDid === holderDid
}

/** Throws with a user-facing message when the holder cannot sign this credential. */
export function assertCredentialBoundToHolder(
  credential: string,
  format: StoredCredential['format'],
  holderDid: string,
): void {
  if (format !== 'vc+sd-jwt') {
    return
  }
  let bound: string
  try {
    bound = boundHolderDidFromSdJwt(credential)
  } catch {
    throw new Error(CREDENTIAL_UNREADABLE)
  }
  if (bound !== holderDid) {
    throw new Error(HOLDER_BINDING_MISMATCH)
  }
}

export const CREDENTIAL_UNREADABLE =
  'This credential could not be read from your wallet. Delete it and scan the issuer QR again.'
