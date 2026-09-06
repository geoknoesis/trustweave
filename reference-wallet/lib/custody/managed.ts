/** Client boundary for an authenticated custody service. No private key enters the browser. */
import { b64uDecode, b64uEncodeString, parseJwsHeader } from '../crypto'

export interface ManagedIdentity {
  profile: 'managed-kms'
  /** Administrator-provisioned HTTPS endpoint; never take it from a credential/QR. */
  endpoint: string
  keyId: string
  algorithm: 'EdDSA' | 'ES256'
  /** Ed25519 raw public key or ES256 SPKI, provisioned through a trusted enrollment. */
  publicKey: string
}

/** The service must authorize key ownership and the exact payload under its session. */
export async function signWithManagedKey(identity: ManagedIdentity, payload: Record<string, unknown>, accessToken: string): Promise<string> {
  identity = { ...identity }
  const url = new URL(identity.endpoint)
  if (identity.profile !== 'managed-kms' || url.protocol !== 'https:' || url.username || url.password || url.hash ||
      !identity.keyId || !accessToken || !['EdDSA', 'ES256'].includes(identity.algorithm)) throw new Error('Invalid managed custody configuration')
  if (typeof payload.nonce !== 'string' || !payload.nonce || typeof payload.aud !== 'string' || !payload.aud) throw new Error('Signing requires a nonce and audience')
  // Snapshot before awaiting the network: caller mutation cannot change verification.
  const encodedPayload = b64uEncodeString(JSON.stringify(payload))
  if (encodedPayload.length > 1_400_000) throw new Error('Signing payload is too large')
  const response = await fetch(url, {
    method: 'POST', redirect: 'error', credentials: 'omit', cache: 'no-store', signal: AbortSignal.timeout(15_000),
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${accessToken}` },
    body: JSON.stringify({ keyId: identity.keyId, algorithm: identity.algorithm, encodedPayload }),
  })
  if (!response.ok) throw new Error(`Custody service refused signing (${response.status})`)
  if (!response.body) throw new Error('Custody response is empty')
  const reader = response.body.getReader(), decoder = new TextDecoder('utf-8', { fatal: true })
  let body = '', size = 0
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      size += value.byteLength
      if (size > 1_500_000) throw new Error('Custody response is too large')
      body += decoder.decode(value, { stream: true })
    }
    body += decoder.decode()
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock() }
  const { jws } = JSON.parse(body)
  if (typeof jws !== 'string') throw new Error('Custody response has no signature')
  const parts = jws.split('.')
  const header = parseJwsHeader(jws)
  if (parts.length !== 3 || parts[1] !== encodedPayload || header.alg !== identity.algorithm || header.kid !== identity.keyId || header.crit !== undefined || header.b64 !== undefined) throw new Error('Custody response changed the signing request')
  const key = identity.algorithm === 'EdDSA'
    ? await crypto.subtle.importKey('raw', b64uDecode(identity.publicKey), 'Ed25519', false, ['verify'])
    : await crypto.subtle.importKey('spki', b64uDecode(identity.publicKey), { name: 'ECDSA', namedCurve: 'P-256' }, false, ['verify'])
  const algorithm = identity.algorithm === 'EdDSA' ? 'Ed25519' : { name: 'ECDSA', hash: 'SHA-256' }
  if (!await crypto.subtle.verify(algorithm, key, b64uDecode(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`))) throw new Error('Invalid custody service signature')
  return jws
}
