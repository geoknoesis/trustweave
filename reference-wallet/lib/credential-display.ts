import type { StoredCredential } from './storage'

/** Europass-inspired palette accents per credential type hash. */
const ACCENTS = ['#003399', '#1e3a8a', '#0d9488', '#7c3aed', '#0369a1', '#b45309']

export function credentialAccent(title: string): string {
  let hash = 0
  for (let i = 0; i < title.length; i++) hash = (hash + title.charCodeAt(i) * (i + 1)) % ACCENTS.length
  return ACCENTS[hash]!
}

export function formatReceivedDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    })
  } catch {
    return iso
  }
}

/** Short, human-readable issuer label (hide raw DID where possible). */
export function formatIssuerLabel(issuerDid: string): string {
  if (issuerDid.startsWith('did:key:')) return 'Verified issuer'
  if (issuerDid.startsWith('did:')) {
    const method = issuerDid.split(':')[1] ?? 'issuer'
    return method.charAt(0).toUpperCase() + method.slice(1) + ' issuer'
  }
  return issuerDid.length > 28 ? `${issuerDid.slice(0, 28)}…` : issuerDid
}

export function humanClaimName(name: string): string {
  return name
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

export function credentialSummary(c: StoredCredential): {
  title: string
  subtitle: string | undefined
  issuer: string
  added: string
  accent: string
} {
  return {
    title: c.preview.title,
    subtitle: c.preview.subtitle,
    issuer: formatIssuerLabel(c.issuerDid),
    added: formatReceivedDate(c.receivedAt),
    accent: credentialAccent(c.preview.title),
  }
}
