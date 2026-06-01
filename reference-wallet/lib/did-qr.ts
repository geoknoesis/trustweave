/**
 * Parse a scanned QR code or pasted string into a W3C DID (any did method).
 * Mirrors trustweave-saas/frontend/src/lib/didQr.ts
 */

export interface HolderQrPayload {
  v: 1
  type: 'trustweave-holder'
  did: string
}

export function looksLikeDid(value: string): boolean {
  const s = value.trim()
  if (!s.startsWith('did:')) return false
  const rest = s.slice(4)
  const colon = rest.indexOf(':')
  if (colon <= 0) return false
  const method = rest.slice(0, colon)
  return /^[a-z0-9]+$/.test(method) && rest.length > colon + 1
}

function firstDidToken(text: string): string | null {
  const token = text.trim().split(/\s+/)[0] ?? ''
  return looksLikeDid(token) ? token : null
}

function didFromJson(parsed: Record<string, unknown>): string | null {
  if (
    parsed.type === 'trustweave-credential-offer'
    || parsed.type === 'trustweave-presentation-request'
    || String(parsed.type ?? '').includes('credential-offer')
  ) {
    return null
  }
  for (const c of [parsed.did, parsed.id]) {
    if (typeof c === 'string' && looksLikeDid(c)) return c.trim()
  }
  return null
}

export function parseDidFromQrPayload(raw: string): string | null {
  const trimmed = raw.trim()
  if (!trimmed) return null

  const plain = firstDidToken(trimmed)
  if (plain) return plain

  try {
    const url = new URL(trimmed)
    for (const key of ['did', 'subject', 'holder', 'issuer']) {
      const q = url.searchParams.get(key)
      if (q && looksLikeDid(q)) return q.trim()
    }
  } catch {
    // not a URL
  }

  try {
    return didFromJson(JSON.parse(trimmed) as Record<string, unknown>)
  } catch {
    return null
  }
}

export function holderQrPayload(did: string): string {
  const payload: HolderQrPayload = { v: 1, type: 'trustweave-holder', did }
  return JSON.stringify(payload)
}

/** @deprecated Use parseDidFromQrPayload */
export function parseHolderQrPayload(raw: string): string | null {
  return parseDidFromQrPayload(raw)
}
