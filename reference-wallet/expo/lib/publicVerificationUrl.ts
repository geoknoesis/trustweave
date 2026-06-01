/** Mirrors reference-wallet/lib/public-verification-url.ts for Expo. */
export function parsePublicVerificationUrl(raw: string): string | null {
  const trimmed = raw.trim()
  if (!trimmed.startsWith('http://') && !trimmed.startsWith('https://')) return null
  try {
    const url = new URL(trimmed)
    if (!/^\/verify\/[^/]+\/?$/.test(url.pathname)) return null
    return trimmed
  } catch {
    return null
  }
}

export function explainPublicVerificationUrlMisscan(): string {
  return (
    'This is a public verification link — open it in your phone browser (or scan with the camera app). ' +
    'The Share tab expects a verifier presentation-request QR from /verifier, not a verify link from your credential.'
  )
}
