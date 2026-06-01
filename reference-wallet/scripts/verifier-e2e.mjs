/**
 * End-to-end: issuer credential → holder presentation → verifier verify
 */
import {
  generateEd25519KeyPair,
  publicKeyToDidKey,
} from '../lib/crypto.ts'
import { issueSdJwtVc, presentSdJwtVc } from '../lib/sdjwt.ts'

const base = process.env.BASE_URL ?? 'http://127.0.0.1:3000'

const holderKp = generateEd25519KeyPair()
const holderDid = publicKeyToDidKey(holderKp.publicKey)

const credRes = await fetch(`${base}/api/demo-issuer/credential?subject=${encodeURIComponent(holderDid)}&studentId=STU-001`)
if (!credRes.ok) throw new Error(`credential HTTP ${credRes.status}: ${await credRes.text()}`)
const credBody = await credRes.json()
console.log('issued:', credBody.vct, 'format:', credBody.format)

const reqRes = await fetch(`${base}/api/demo-verifier/request`)
if (!reqRes.ok) throw new Error(`request HTTP ${reqRes.status}`)
const req = await reqRes.json()
console.log('verifier audience:', req.audience.slice(0, 28) + '…')
console.log('acceptedTypes:', req.acceptedTypes)

const typeMatch = [credBody.vct].some((t) => req.acceptedTypes.includes(t))
if (!typeMatch) {
  console.error('TYPE MISMATCH:', credBody.vct, 'not in', req.acceptedTypes)
  process.exit(1)
}

const now = Math.floor(Date.now() / 1000)
const presentation = presentSdJwtVc({
  sdJwtVc: credBody.credential,
  selectDisclose: credBody.selectivelyDisclosable ?? ['name', 'degree'],
  holderPrivateKey: holderKp.privateKey,
  holderDid,
  audience: req.audience,
  nonce: req.nonce,
  now,
})

const verifyRes = await fetch(`${base}/api/demo-verifier/verify`, {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({
    presentation,
    format: 'vc+sd-jwt',
    expectedNonce: req.nonce,
  }),
})
const result = await verifyRes.json()
console.log('verify HTTP:', verifyRes.status)
console.log('valid:', result.valid)

const recentRes = await fetch(`${base}/api/demo-verifier/recent?limit=1`)
const recent = await recentRes.json()
console.log('inbox latest valid:', recent.latest?.valid ?? 'none')

for (const c of result.checks ?? []) {
  console.log(`  ${c.passed ? '✓' : '✗'} ${c.step}${c.detail ? ` — ${c.detail}` : ''}`)
}
if (!result.valid) process.exit(1)
console.log('E2E OK')
