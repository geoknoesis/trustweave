import { test, expect } from '@playwright/test'

test('device passkey signs a challenge; verifier rejects tampering and stale challenge', async ({ page, context }) => {
  const cdp = await context.newCDPSession(page)
  await cdp.send('WebAuthn.enable')
  const { authenticatorId } = await cdp.send('WebAuthn.addVirtualAuthenticator', { options: {
    protocol: 'ctap2', transport: 'usb', hasResidentKey: true, hasUserVerification: true,
    isUserVerified: true, automaticPresenceSimulation: true,
  } })
  try {
    await page.goto('http://localhost:4174/tests/browser/')
    await page.waitForFunction(() => !!(window as any).walletTest)
    const result = await page.evaluate(async () => {
      const { passkey } = (window as any).walletTest
      const identity = await passkey.enrollPasskey('Test holder')
      const challenge = crypto.getRandomValues(new Uint8Array(32))
      const proof = await passkey.signWithPasskey(identity, challenge)
      const counter = await passkey.verifyPasskeyProof(identity, proof, challenge)
      const rejected: string[] = []
      for (const [name, modified, expected] of [
        ['challenge', proof, crypto.getRandomValues(new Uint8Array(32))],
        ['credential', { ...proof, credentialId: 'other' }, challenge],
        ['signature', { ...proof, signature: proof.signature.slice(0, -5) + 'AAAAA' }, challenge],
      ] as const) {
        try { await passkey.verifyPasskeyProof(identity, modified, expected) } catch { rejected.push(name) }
      }
      ;(window as any).passkeyIdentity = identity
      return { profile: identity.profile, counter, rejected }
    })
    expect(result.profile).toBe('passkey')
    expect(result.counter).toBeGreaterThan(0)
    expect(result.rejected).toEqual(['challenge', 'credential', 'signature'])
    await cdp.send('WebAuthn.setUserVerified', { authenticatorId, isUserVerified: false })
    // Exercise verifier enforcement without relying on a 60-second browser prompt timeout.
    expect(await page.evaluate(async () => {
      const { passkey, crypto: helpers } = (window as any).walletTest
      const identity = (window as any).passkeyIdentity
      const challenge = crypto.getRandomValues(new Uint8Array(32))
      const result = await navigator.credentials.get({ publicKey: { challenge, rpId: identity.rpId,
        allowCredentials: [{ type: 'public-key', id: helpers.b64uDecode(identity.credentialId) }], userVerification: 'discouraged' } }) as PublicKeyCredential
      const r = result.response as AuthenticatorAssertionResponse
      const proof = { credentialId: identity.credentialId, authenticatorData: helpers.b64uEncode(new Uint8Array(r.authenticatorData)),
        clientDataJSON: helpers.b64uEncode(new Uint8Array(r.clientDataJSON)), signature: helpers.b64uEncode(new Uint8Array(r.signature)) }
      try { await passkey.verifyPasskeyProof(identity, proof, challenge); return false } catch { return true }
    })).toBe(true)
  } finally { await cdp.send('WebAuthn.removeVirtualAuthenticator', { authenticatorId }) }
})
