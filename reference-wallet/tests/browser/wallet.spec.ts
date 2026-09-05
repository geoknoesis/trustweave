import { test, expect, chromium } from '@playwright/test'

test('camera works under CSP and cancellation releases a late permission result', async () => {
  const browser = await chromium.launch({ args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] })
  try {
    const context = await browser.newContext({ permissions: ['camera'] })
    const page = await context.newPage()
    await page.goto('http://127.0.0.1:4175/receive')
    await page.getByRole('button', { name: 'Open camera scanner' }).click()
    await expect(page.locator('video')).toBeVisible()
    await expect.poll(() => page.locator('video').evaluate((video: HTMLVideoElement) => video.readyState)).toBeGreaterThanOrEqual(2)
    await page.getByRole('button', { name: 'Stop camera', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Open camera scanner' })).toBeVisible()
    await page.evaluate(() => {
      const original = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices)
      navigator.mediaDevices.getUserMedia = async constraints => {
        const stream = await original(constraints)
        ;(window as any).pendingCameraStream = stream
        await new Promise<void>(resolve => { (window as any).releaseCameraPermission = resolve })
        return stream
      }
    })
    await page.getByRole('button', { name: 'Open camera scanner' }).click()
    await page.waitForFunction(() => !!(window as any).releaseCameraPermission)
    await page.getByRole('button', { name: 'Stop camera', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Open camera scanner' })).toBeVisible()
    await page.evaluate(() => (window as any).releaseCameraPermission())
    await expect.poll(() => page.evaluate(() => (window as any).pendingCameraStream.getTracks().every((track: MediaStreamTrack) => track.readyState === 'ended'))).toBe(true)
  } finally { await browser.close() }
})

test('production nonce policy blocks injected scripts while wallet hydration works', async ({ page, request }) => {
  const first = await request.get('http://127.0.0.1:4175/present')
  const second = await request.get('http://127.0.0.1:4175/present')
  const policy = first.headers()['content-security-policy']
  expect(policy).toContain("'strict-dynamic'")
  expect(policy).not.toContain("'unsafe-eval'")
  expect(policy).not.toContain("'unsafe-inline'")
  expect(policy).not.toBe(second.headers()['content-security-policy'])
  await page.route('http://127.0.0.1:4175/present', async route => {
    const response = await route.fetch()
    const body = await response.text()
    expect(body).toContain('</head>')
    await route.fulfill({ response, body: body.replace('</head>', '<script>window.injectedWalletScriptExecuted = true</script></head>') })
  })
  await page.goto('http://127.0.0.1:4175/present')
  await expect(page.getByText('Loading wallet…', { exact: true })).not.toBeVisible()
  expect(await page.evaluate(() => (window as any).injectedWalletScriptExecuted)).toBeUndefined()
  expect(await page.locator('script[nonce]').count()).toBeGreaterThan(0)
})

test('production wallet prevents framing and invitation leakage through referrers', async ({ request }) => {
  const response = await request.get('http://127.0.0.1:4175/present')
  expect(response.headers()['x-frame-options']).toBe('DENY')
  expect(response.headers()['content-security-policy']).toContain("frame-ancestors 'none'")
  expect(response.headers()['referrer-policy']).toBe('no-referrer')
  expect(response.headers()['permissions-policy']).toContain('camera=(self)')
})

for (const profile of ['cac', 'faa', 'spatial']) {
  test(`${profile} issuer imports without leaking selective claims in the visible payload`, async ({ page, request }) => {
    await page.goto('/tests/browser/')
    await page.waitForFunction(() => !!(window as any).walletTest)
    const did = await page.evaluate(async () => (await (window as any).walletTest.wallet.bootstrap()).holder.did)
    const response = await request.get(`http://127.0.0.1:4175/api/demo-issuer/${profile}/credential`, { params: { subject: did } })
    expect(response.ok()).toBe(true)
    const issued = await response.json()
    const visible = JSON.parse(Buffer.from(issued.credential.split('.')[1], 'base64url').toString())
    for (const name of issued.selectivelyDisclosable) expect(visible).not.toHaveProperty(name)
    const hidden = await page.evaluate(async issued => {
      const { wallet, sdjwt } = (window as any).walletTest
      const record = (await wallet.store(issued.credential, issued.format)).credential
      return sdjwt.decodeSdJwtVc(await wallet.createPresentation([record.id], 'verifier', 'nonce', [])).disclosures.length
    }, issued)
    expect(hidden).toBe(0)
  })
}

test('real issuer and verifier agree on the holder-selected disclosure', async ({ page, request }) => {
  await page.goto('/tests/browser/')
  await page.waitForFunction(() => !!(window as any).walletTest)
  const did = await page.evaluate(async () => (await (window as any).walletTest.wallet.bootstrap()).holder.did)
  const issued = await (await request.get('http://127.0.0.1:4175/api/demo-issuer/credential', { params: { subject: did } })).json()
  const visible = JSON.parse(Buffer.from(issued.credential.split('.')[1], 'base64url').toString())
  for (const name of issued.selectivelyDisclosable) expect(visible).not.toHaveProperty(name)
  const challenge = await (await request.get('http://127.0.0.1:4175/api/demo-verifier/request')).json()
  const presentation = await page.evaluate(async ({ issued, challenge }) => {
    const { wallet } = (window as any).walletTest
    const record = (await wallet.store(issued.credential, issued.format)).credential
    return wallet.createPresentation([record.id], challenge.audience, challenge.nonce, [issued.selectivelyDisclosable[0]])
  }, { issued, challenge })
  const verdict = await (await request.post('http://127.0.0.1:4175/api/demo-verifier/verify', {
    data: { presentation, format: issued.format, expectedNonce: challenge.nonce },
  })).json()
  expect(verdict.valid).toBe(true)
  expect(verdict.credentials[0].disclosedClaims).toHaveProperty(issued.selectivelyDisclosable[0])
  for (const name of issued.selectivelyDisclosable.slice(1)) expect(verdict.credentials[0].disclosedClaims).not.toHaveProperty(name)
  const wrongNonce = await (await request.post('http://127.0.0.1:4175/api/demo-verifier/verify', {
    data: { presentation, format: issued.format, expectedNonce: 'different-challenge' },
  })).json()
  expect(wrongNonce.valid).toBe(false)
})

test('share page presents recovery instead of an endless loading state', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('trustweave-wallet-schema-version', '2')
    localStorage.setItem('trustweave-wallet-credentials', '[null]')
  })
  await page.goto('http://127.0.0.1:4175/present')
  await expect(page.getByRole('button', { name: /export/i })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('trustweave-wallet-credentials'))).toBe('[null]')
})

test('real browser custody survives reload and concurrent tabs retain imports', async ({ context, page }) => {
  await page.goto('/tests/browser/')
  await page.waitForFunction(() => !!(window as any).walletTest)
  const did = await page.evaluate(async () => (await (window as any).walletTest.wallet.bootstrap()).holder.did)
  const second = await context.newPage()
  await second.goto('/tests/browser/')
  await second.waitForFunction(() => !!(window as any).walletTest)
  const add = (tab: typeof page, name: string) => tab.evaluate(async name => {
    const { wallet, crypto, sdjwt } = (window as any).walletTest
    const holder = (await wallet.bootstrap()).holder
    const issuer = crypto.generateEd25519KeyPair(), issuerDid = crypto.publicKeyToDidKey(issuer.publicKey)
    const compact = sdjwt.issueSdJwtVc({ issuerDid, issuerPrivateKey: issuer.privateKey, issuerKid: issuerDid,
      holderDid: holder.did, vct: name, alwaysVisible: {}, selectivelyDisclosable: [{ name: 'privateClaim', value: name }], now: Math.floor(Date.now()/1000) })
    return (await wallet.store(compact, 'vc+sd-jwt')).credential.id
  }, name)
  const ids = await Promise.all([add(page, 'Employee'), add(second, 'Degree')])
  await page.reload(); await page.waitForFunction(() => !!(window as any).walletTest)
  const result = await page.evaluate(async id => {
    const { wallet, crypto, sdjwt, keys } = (window as any).walletTest
    const holder = (await wallet.bootstrap()).holder
    const key = (await keys.loadHolderKeys(holder.did)).signing
    let exportRejected = false
    try { await window.crypto.subtle.exportKey('pkcs8', key) } catch { exportRejected = true }
    const compact = await wallet.createPresentation([id], 'verifier', 'nonce', [])
    const decoded = sdjwt.decodeSdJwtVc(compact)
    const proof = crypto.verifyJws(decoded.kbJwt, crypto.b64uDecode(holder.publicKey))
    return { did: holder.did, count: JSON.parse(localStorage.getItem('trustweave-wallet-credentials')!).length,
      exportRejected, disclosures: decoded.disclosures.length, nonce: proof.nonce }
  }, ids[0])
  expect(result).toEqual({ did, count: 2, exportRejected: true, disclosures: 0, nonce: 'nonce' })
})
