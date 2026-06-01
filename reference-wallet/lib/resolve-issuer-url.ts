/**
 * Resolve issuer URL from a scanned QR for mobile wallets.
 * localhost / 127.0.0.1 in a QR points at the phone itself — rewrite to [fallbackBaseUrl].
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
    if (local && fallbackBaseUrl) {
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
    `Cannot reach issuer at ${issuerUrl}. ` +
    `Ensure the QR uses your PC's LAN IP (e.g. ${fallbackBaseUrl ?? 'http://192.168.x.x:3000'}), ` +
    'not localhost. Phone and PC must be on the same Wi‑Fi; allow port 3000 through the firewall.'
  )
}
