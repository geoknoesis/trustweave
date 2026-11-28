import { Platform, useColorScheme } from 'react-native'
import { b64uDecodeString } from './crypto'
import { extractClaimsForDisplay } from './credentialClaims'
import { shortDidFingerprint } from './issuerTrust'
import { decodeSdJwtVc } from './sdjwt'
import type { StoredCredential } from './storage'

const ACCENTS = ['#003399', '#1e3a8a', '#0d9488', '#6d28d9', '#0369a1', '#b45309']

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

export function formatRelativeTime(iso: string): string {
  try {
    const date = new Date(iso)
    const diffMs = Date.now() - date.getTime()
    const mins = Math.floor(diffMs / 60_000)
    if (mins < 1) return 'Just now'
    if (mins < 60) return `${mins}m ago`
    const hours = Math.floor(mins / 60)
    if (hours < 24) return `${hours}h ago`
    return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
  } catch {
    return iso
  }
}

/**
 * Honest issuer label: never claims an arbitrary `did:key:` is a "Verified issuer".
 * Shows a human organization name when available, otherwise a short scannable fingerprint.
 */
export function formatIssuerLabel(issuerDid: string, issuerName?: string): string {
  if (issuerName && issuerName.trim().length > 0) return issuerName.trim()
  if (issuerDid.startsWith('did:web:')) {
    const host = issuerDid.slice('did:web:'.length).split(':')[0]
    return host ? decodeURIComponent(host) : 'Web issuer'
  }
  if (issuerDid.startsWith('did:')) {
    return `Issuer · ${shortDidFingerprint(issuerDid)}`
  }
  return issuerDid.length > 28 ? `${issuerDid.slice(0, 28)}…` : issuerDid
}

export function humanClaimName(name: string): string {
  return name
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase())
}

export type TrustBadgeTone = 'success' | 'info' | 'warning' | 'danger' | 'neutral'

export interface StatusBadge {
  label: string
  tone: TrustBadgeTone
  icon: 'checkmark-circle' | 'shield-checkmark' | 'alert-circle' | 'time' | 'document-outline'
}

export function extractCredentialExpiryEpoch(cred: Pick<StoredCredential, 'format' | 'credential'>): number | null {
  try {
    if (cred.format === 'vc+sd-jwt') {
      const decoded = decodeSdJwtVc(cred.credential)
      return typeof decoded.issuerPayload.exp === 'number' ? Math.trunc(decoded.issuerPayload.exp) : null
    }
    if (cred.format === 'vc+jwt') {
      const parts = cred.credential.split('.')
      if (parts.length !== 3) return null
      const payload = JSON.parse(b64uDecodeString(parts[1])) as { exp?: unknown }
      return typeof payload.exp === 'number' ? Math.trunc(payload.exp) : null
    }
    const parsed = JSON.parse(cred.credential) as { validUntil?: unknown; expirationDate?: unknown }
    const iso = typeof parsed.validUntil === 'string' ? parsed.validUntil : typeof parsed.expirationDate === 'string' ? parsed.expirationDate : null
    if (!iso) return null
    const ms = Date.parse(iso)
    return Number.isFinite(ms) ? Math.floor(ms / 1000) : null
  } catch {
    return null
  }
}

export function credentialTrustBadge(
  cred: Pick<StoredCredential, 'format' | 'credential' | 'trustSource'>,
  nowEpochSeconds: number = Math.floor(Date.now() / 1000),
): StatusBadge {
  const exp = extractCredentialExpiryEpoch(cred)
  if (exp !== null && exp <= nowEpochSeconds) {
    return { label: 'Expired', tone: 'danger', icon: 'alert-circle' }
  }
  if (exp !== null && exp - nowEpochSeconds < 30 * 24 * 3600) {
    return { label: 'Expiring soon', tone: 'warning', icon: 'time' }
  }
  if (cred.trustSource === 'configured' || cred.trustSource === 'backend') {
    return { label: 'Trusted issuer', tone: 'success', icon: 'shield-checkmark' }
  }
  if (cred.trustSource === 'confirmed') {
    return { label: 'Confirmed by you', tone: 'info', icon: 'checkmark-circle' }
  }
  return { label: 'Saved on device', tone: 'neutral', icon: 'document-outline' }
}

export type CredentialIconName =
  | 'school-outline'
  | 'airplane-outline'
  | 'id-card-outline'
  | 'shield-checkmark-outline'
  | 'ribbon-outline'
  | 'card-outline'

export function credentialIconName(types: string[], title: string): CredentialIconName {
  const haystack = `${types.join(' ')} ${title}`.toLowerCase()
  if (/degree|diploma|university|student|education|bachelor|master/.test(haystack)) return 'school-outline'
  if (/faa|drone|airspace|pilot|aviation|uas|flight/.test(haystack)) return 'airplane-outline'
  if (/identity|passport|driver|license|licence|pid|cac|employee|member/.test(haystack)) return 'id-card-outline'
  if (/clearance|security|auth|access|permit|gate/.test(haystack)) return 'shield-checkmark-outline'
  if (/cert|award|badge|accomplishment/.test(haystack)) return 'ribbon-outline'
  return 'card-outline'
}

export interface ShareableClaimOption {
  name: string
  label: string
  value: string | null
}

/**
 * Map every selectively disclosable claim name to its human label AND its actual
 * personal value so the user sees exactly what they are about to share.
 */
export function extractShareableClaimDetails(cred: StoredCredential): ShareableClaimOption[] {
  const displayRows = extractClaimsForDisplay(cred)
  const byKey = new Map<string, { label: string; value: string | null }>()
  for (const row of displayRows) {
    if (row.depth === 0) {
      byKey.set(row.key, { label: row.label, value: row.value })
    }
  }

  let sdValues: Record<string, unknown> = {}
  if (cred.format === 'vc+sd-jwt') {
    try {
      const decoded = decodeSdJwtVc(cred.credential)
      for (const d of decoded.disclosures) sdValues[d.name] = d.value
    } catch {
      sdValues = {}
    }
  }

  return cred.selectivelyDisclosable.map((name) => {
    const matched = byKey.get(name)
    let value = matched?.value ?? null
    if (value === null && Object.prototype.hasOwnProperty.call(sdValues, name)) {
      const raw = sdValues[name]
      if (typeof raw === 'string' || typeof raw === 'number' || typeof raw === 'boolean') {
        value = String(raw)
      } else if (Array.isArray(raw)) {
        value = raw.map(String).join(', ')
      } else if (raw && typeof raw === 'object') {
        value = Object.values(raw as Record<string, unknown>)
          .filter((v) => typeof v === 'string' || typeof v === 'number')
          .join(' · ') || null
      }
    }
    return {
      name,
      label: matched?.label ?? humanClaimName(name),
      value,
    }
  })
}

export function credentialSummary(c: StoredCredential) {
  return {
    title: c.preview.title,
    subtitle: c.preview.subtitle,
    issuer: formatIssuerLabel(c.issuerDid, c.issuerName),
    added: formatReceivedDate(c.receivedAt),
    accent: credentialAccent(c.preview.title),
    icon: credentialIconName(c.type, c.preview.title),
    badge: credentialTrustBadge(c),
  }
}

export const monoFont = Platform.select({
  ios: 'Menlo',
  android: 'monospace',
  default: 'monospace',
})

export interface WalletTheme {
  isDark: boolean
  primary: string
  primaryLight: string
  onPrimary: string
  bg: string
  surface: string
  surfaceElevated: string
  text: string
  textMuted: string
  success: string
  successBg: string
  info: string
  infoBg: string
  warning: string
  warningBg: string
  danger: string
  dangerBg: string
  border: string
  radius: number
  shadow: {
    shadowColor: string
    shadowOffset: { width: number; height: number }
    shadowOpacity: number
    shadowRadius: number
    elevation: number
  }
}

export const lightTheme: WalletTheme = {
  isDark: false,
  primary: '#1E3A8A',
  primaryLight: '#2563EB',
  onPrimary: '#FFFFFF',
  bg: '#F1F5F9',
  surface: '#FFFFFF',
  surfaceElevated: '#F8FAFC',
  text: '#0F172A',
  textMuted: '#475569',
  success: '#047857',
  successBg: '#ECFDF5',
  info: '#1D4ED8',
  infoBg: '#EFF6FF',
  warning: '#B45309',
  warningBg: '#FFFBEB',
  danger: '#DC2626',
  dangerBg: '#FEF2F2',
  border: '#E2E8F0',
  radius: 14,
  shadow: {
    shadowColor: '#0F172A',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.06,
    shadowRadius: 8,
    elevation: 2,
  },
}

export const darkTheme: WalletTheme = {
  isDark: true,
  primary: '#60A5FA',
  primaryLight: '#93C5FD',
  onPrimary: '#0B1120',
  bg: '#0B1120',
  surface: '#162033',
  surfaceElevated: '#1E293B',
  text: '#F8FAFC',
  textMuted: '#94A3B8',
  success: '#34D399',
  successBg: '#064E3B',
  info: '#93C5FD',
  infoBg: '#1E3A5F',
  warning: '#FBBF24',
  warningBg: '#451A03',
  danger: '#F87171',
  dangerBg: '#450A0A',
  border: '#273549',
  radius: 14,
  shadow: {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 8,
    elevation: 3,
  },
}

/** Backwards-compatible default static theme for non-hook callers. */
export const theme = lightTheme

export function useWalletTheme(): WalletTheme {
  const scheme = useColorScheme()
  return scheme === 'dark' ? darkTheme : lightTheme
}
