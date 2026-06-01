/**
 * In-memory store of recent demo verifications for the verifier UI.
 * Lets the /verifier page show live results when a holder wallet POSTs a presentation.
 */
import 'server-only'

export interface StoredVerification {
  id: string
  at: string
  valid: boolean
  nonce?: string
  holder?: string
  checks: Array<{ step: string; passed: boolean; detail?: string }>
  credentials?: Array<{
    type: string[]
    issuer: string
    subject: string
    disclosedClaims: Record<string, unknown>
  }>
}

const MAX = 20
const inbox: StoredVerification[] = []

export function recordVerification(entry: Omit<StoredVerification, 'id' | 'at'>): StoredVerification {
  const stored: StoredVerification = {
    id: crypto.randomUUID(),
    at: new Date().toISOString(),
    ...entry,
  }
  inbox.unshift(stored)
  if (inbox.length > MAX) inbox.length = MAX
  return stored
}

export function listRecentVerifications(limit = 5): StoredVerification[] {
  return inbox.slice(0, Math.min(limit, inbox.length))
}

export function latestVerification(): StoredVerification | null {
  return inbox[0] ?? null
}

export function verificationForNonce(nonce: string): StoredVerification | null {
  return inbox.find((v) => v.nonce === nonce) ?? null
}
