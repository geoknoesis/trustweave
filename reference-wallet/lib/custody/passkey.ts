/** WebAuthn assertion custody primitive. Assertions are not JWT signatures. */
import { p256 } from '@noble/curves/p256'
import { b64uDecode, b64uEncode } from '../crypto'

export interface PasskeyIdentity {
  profile: 'passkey'
  credentialId: string
  publicKeySpki: string
  rpId: string
  origin: string
}
export interface PasskeyProof {
  credentialId: string
  authenticatorData: string
  clientDataJSON: string
  signature: string
}
const hash = async (bytes: Uint8Array) => new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
const equal = (a: Uint8Array, b: Uint8Array) => a.length === b.length && a.every((value, i) => value === b[i])

function checkOrigin(origin: string, rpId: string) {
  const url = new URL(origin)
  if (url.origin !== origin || (url.protocol !== 'https:' && !(url.protocol === 'http:' && url.hostname === 'localhost')) ||
      url.hostname !== rpId) throw new Error('Passkey profile requires an exact secure RP origin')
}

async function validateAuthenticator(data: Uint8Array, rpId: string) {
  if (data.length < 37 || !equal(data.slice(0, 32), await hash(new TextEncoder().encode(rpId)))) throw new Error('Invalid authenticator RP binding')
  const flags = data[32]
  if ((flags & 0x05) !== 0x05) throw new Error('User presence and verification are required')
  if (flags & 0x18) throw new Error('This profile requires a non-backup-eligible device credential')
}

/** Verifiers must supply an issued, unexpired one-use challenge and atomically consume it. */
export async function verifyPasskeyProof(identity: PasskeyIdentity, proof: PasskeyProof, challenge: Uint8Array): Promise<number> {
  identity = { ...identity }; proof = { ...proof }; challenge = challenge.slice()
  checkOrigin(identity.origin, identity.rpId)
  if (identity.profile !== 'passkey' || challenge.length !== 32 || proof.credentialId !== identity.credentialId) throw new Error('Passkey identity or challenge mismatch')
  if (proof.clientDataJSON.length > 8192 || proof.authenticatorData.length > 8192 || proof.signature.length > 128) throw new Error('Oversized passkey proof')
  const clientBytes = b64uDecode(proof.clientDataJSON)
  const client = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(clientBytes))
  if (client.type !== 'webauthn.get' || client.origin !== identity.origin || (client.crossOrigin !== undefined && client.crossOrigin !== false) || client.topOrigin !== undefined ||
      client.challenge !== b64uEncode(challenge)) throw new Error('Invalid passkey client binding')
  const auth = b64uDecode(proof.authenticatorData)
  await validateAuthenticator(auth, identity.rpId)
  // This bounded assertion profile does not negotiate authenticator extensions.
  if (auth.length !== 37 || (auth[32] & 0xc0)) throw new Error('Unsupported authenticator assertion extensions')
  const input = new Uint8Array(auth.length + 32)
  input.set(auth); input.set(await hash(clientBytes), auth.length)
  const publicKey = await crypto.subtle.importKey('spki', b64uDecode(identity.publicKeySpki), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify'])
  const rawSignature = p256.Signature.fromDER(b64uDecode(proof.signature)).toCompactRawBytes()
  if (!await crypto.subtle.verify({ name: 'ECDSA', hash: 'SHA-256' }, publicKey, rawSignature, input)) throw new Error('Invalid passkey signature')
  return new DataView(auth.buffer, auth.byteOffset, auth.byteLength).getUint32(33)
}

export async function signWithPasskey(identity: PasskeyIdentity, challenge: Uint8Array): Promise<PasskeyProof> {
  identity = { ...identity }; challenge = challenge.slice()
  checkOrigin(identity.origin, identity.rpId)
  if (location.origin !== identity.origin || challenge.length !== 32) throw new Error('Passkey origin or challenge mismatch')
  const result = await navigator.credentials.get({ publicKey: {
    challenge, rpId: identity.rpId, userVerification: 'required', timeout: 60_000,
    allowCredentials: [{ type: 'public-key', id: b64uDecode(identity.credentialId) }],
  } }) as PublicKeyCredential | null
  if (!result) throw new Error('Passkey signing was cancelled')
  const response = result.response as AuthenticatorAssertionResponse
  const proof = {
    credentialId: b64uEncode(new Uint8Array(result.rawId)),
    authenticatorData: b64uEncode(new Uint8Array(response.authenticatorData)),
    clientDataJSON: b64uEncode(new Uint8Array(response.clientDataJSON)),
    signature: b64uEncode(new Uint8Array(response.signature)),
  }
  await verifyPasskeyProof(identity, proof, challenge)
  return proof
}

/** Enrolls a device credential and proves possession. Does not attest vendor hardware. */
export async function enrollPasskey(displayName: string): Promise<PasskeyIdentity> {
  const origin = location.origin, rpId = location.hostname
  checkOrigin(origin, rpId)
  const challenge = crypto.getRandomValues(new Uint8Array(32))
  const result = await navigator.credentials.create({ publicKey: {
    challenge, rp: { id: rpId, name: 'TrustWeave' },
    user: { id: crypto.getRandomValues(new Uint8Array(32)), name: displayName, displayName },
    pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
    authenticatorSelection: { userVerification: 'required', residentKey: 'required' },
    attestation: 'none', timeout: 60_000,
  } }) as PublicKeyCredential | null
  if (!result) throw new Error('Passkey enrollment was cancelled')
  const response = result.response as AuthenticatorAttestationResponse
  const spki = response.getPublicKey()
  if (!spki || response.getPublicKeyAlgorithm() !== -7) throw new Error('Only ES256 passkeys are supported')
  const client = JSON.parse(new TextDecoder().decode(response.clientDataJSON))
  if (client.type !== 'webauthn.create' || client.challenge !== b64uEncode(challenge) || client.origin !== origin || (client.crossOrigin !== undefined && client.crossOrigin !== false) || client.topOrigin !== undefined) throw new Error('Invalid registration binding')
  await validateAuthenticator(new Uint8Array(response.getAuthenticatorData()), rpId)
  const identity: PasskeyIdentity = { profile: 'passkey', credentialId: b64uEncode(new Uint8Array(result.rawId)), publicKeySpki: b64uEncode(new Uint8Array(spki)), rpId, origin }
  await signWithPasskey(identity, crypto.getRandomValues(new Uint8Array(32)))
  return identity
}
