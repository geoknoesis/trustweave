import { signWithPasskey, verifyPasskeyProof, type PasskeyIdentity, type PasskeyProof } from './passkey'
import { signWithManagedKey, type ManagedIdentity } from './managed'

export type CustodyIdentity = PasskeyIdentity | ManagedIdentity
export type CustodyProof =
  | { profile: 'passkey'; payload: string; proof: PasskeyProof }
  | { profile: 'managed-kms'; jws: string }

/** Explicit dispatch. No browser-key fallback and no conversion of WebAuthn into JWT. */
export async function signCustodyPayload(identity: CustodyIdentity, payload: Record<string, unknown>, accessToken?: string): Promise<CustodyProof> {
  if (typeof payload.nonce !== 'string' || !payload.nonce || typeof payload.aud !== 'string' || !payload.aud) throw new Error('A verifier nonce and audience are required')
  if (identity.profile === 'managed-kms') return { profile: 'managed-kms', jws: await signWithManagedKey(identity, payload, accessToken ?? '') }
  if (identity.profile !== 'passkey') throw new Error('Unsupported custody profile')
  const serialized = JSON.stringify(payload)
  const bytes = new TextEncoder().encode(serialized)
  if (bytes.length > 1_048_576) throw new Error('Signing payload is too large')
  const challenge = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
  return { profile: 'passkey', payload: serialized, proof: await signWithPasskey(identity, challenge) }
}

/** Application verifier boundary. Trusted identity/challenge must come from server state.
 * consume must atomically check/consume the nonce and persist any counter policy.
 * Never implement consume with a separate read followed by write.
 */
export async function verifyPasskeyPayload(
  identity: PasskeyIdentity,
  envelope: Extract<CustodyProof, { profile: 'passkey' }>,
  expected: { nonce: string; audience: string; expiresAt: number },
  consume: (nonce: string, credentialId: string, counter: number) => Promise<boolean>,
): Promise<Record<string, unknown>> {
  identity = { ...identity }
  envelope = { ...envelope, proof: { ...envelope.proof } }
  expected = { ...expected }
  const serialized = envelope.payload
  if (envelope.profile !== 'passkey' || typeof serialized !== 'string' || new TextEncoder().encode(serialized).length > 1_048_576 ||
      !expected.nonce || !expected.audience || !Number.isFinite(expected.expiresAt) || expected.expiresAt <= Date.now()) throw new Error('Invalid or expired passkey challenge')
  const payload = JSON.parse(serialized)
  if (!payload || Array.isArray(payload) || payload.nonce !== expected.nonce || payload.aud !== expected.audience) throw new Error('Passkey audience or nonce mismatch')
  const challenge = new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(serialized)))
  const counter = await verifyPasskeyProof(identity, envelope.proof, challenge)
  if (expected.expiresAt <= Date.now() || !await consume(expected.nonce, identity.credentialId, counter)) throw new Error('Passkey challenge expired or was already consumed')
  return payload
}
