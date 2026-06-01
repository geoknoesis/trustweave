/** Public SaaS verify-by-id page (holder shows this; verifier opens in browser). */
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
    'This is a public verification link (for anyone to confirm your credential in a browser). ' +
    'It is not a verifier presentation request. To share with a verifier, scan the QR from their /verifier page instead. ' +
    'To check verification yourself, open this link in your phone browser (or scan it with the camera app).'
  )
}
