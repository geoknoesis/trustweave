import type { StoredCredential } from './storage'

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

export function credentialSummary(c: StoredCredential) {
  return {
    title: c.preview.title,
    subtitle: c.preview.subtitle,
    issuer: formatIssuerLabel(c.issuerDid),
    added: formatReceivedDate(c.receivedAt),
    accent: credentialAccent(c.preview.title),
  }
}

export const theme = {
  primary: '#003399',
  primaryLight: '#1e40af',
  bg: '#f0f4f8',
  surface: '#ffffff',
  text: '#1a202c',
  textMuted: '#64748b',
  success: '#00875a',
  successBg: '#e6f4ed',
  border: '#e2e8f0',
  radius: 12,
  shadow: {
    shadowColor: '#003399',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 3,
  },
} as const
