/**
 * Pending scan payload handoff between the Unified Scanner / OS Deep Link listener
 * and the Add (Receive) or Share (Present) flows.
 *
 * Kept in memory so large JSON offers or presentation requests don't need to be
 * mangled through URL query params.
 */
import type { PresentationRequestQrPayload } from './presentationRequestQr'

let pendingOfferRaw: string | null = null
let pendingPresentationQr: PresentationRequestQrPayload | null = null
let pendingCredentialIdToShare: string | null = null

export function setPendingOfferRaw(raw: string): void {
  pendingOfferRaw = raw
}

export function consumePendingOfferRaw(): string | null {
  const value = pendingOfferRaw
  pendingOfferRaw = null
  return value
}

export function setPendingPresentationQr(qr: PresentationRequestQrPayload): void {
  pendingPresentationQr = qr
}

export function consumePendingPresentationQr(): PresentationRequestQrPayload | null {
  const value = pendingPresentationQr
  pendingPresentationQr = null
  return value
}

export function setPendingCredentialIdToShare(id: string): void {
  pendingCredentialIdToShare = id
}

export function consumePendingCredentialIdToShare(): string | null {
  const value = pendingCredentialIdToShare
  pendingCredentialIdToShare = null
  return value
}
