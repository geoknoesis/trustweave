/**
 * Resolve backend URL from a scanned QR for mobile wallets.
 *
 * Uses the issuer URL embedded in the QR. Only rewrites localhost/127.0.0.1 to
 * [fallbackBaseUrl] so phone wallets can reach a dev machine on the LAN.
 */
export function resolveIssuerUrl(offerUrl: string, fallbackBaseUrl?: string): string {
  const trimmed = offerUrl.replace(/\/$/, '')
  try {
    const u = new URL(trimmed)
    const local =
      u.hostname === 'localhost' ||
      u.hostname === '127.0.0.1' ||
      u.hostname === '[::1]' ||
      u.hostname === '0.0.0.0'
    if (local && fallbackBaseUrl?.trim()) {
      const fb = new URL(fallbackBaseUrl.replace(/\/$/, '') + '/')
      u.hostname = fb.hostname
      return u.toString().replace(/\/$/, '')
    }
  } catch {
    // keep trimmed
  }
  return trimmed
}

export function networkErrorHint(issuerUrl: string, fallbackBaseUrl?: string): string {
  return (
    `Could not connect to ${issuerUrl}. ` +
    'Check your Wi‑Fi or internet connection and try again.' +
    (fallbackBaseUrl
      ? ` If you are testing locally, make sure your phone and server are on the same network (${fallbackBaseUrl}).`
      : '')
  )
}
