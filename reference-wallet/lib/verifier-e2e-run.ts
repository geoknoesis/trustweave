/**
 * Headless issuer → holder → verifier round-trip for the demo-university trust domain.
 * Used by the verifier test page API and the CLI script.
 */
import 'server-only'
import {
  generateEd25519KeyPair,
  publicKeyToDidKey,
} from '@/lib/crypto'
import { presentSdJwtVc } from '@/lib/sdjwt'
import type { E2eTestResult, E2eTestStep } from '@/lib/verifier-e2e-types'

export type { E2eTestResult, E2eTestStep } from '@/lib/verifier-e2e-types'

interface CredentialResponse {
  format: 'vc+sd-jwt'
  credential: string
  vct: string
  selectivelyDisclosable: string[]
}

interface VerifierRequest {
  audience: string
  nonce: string
  acceptedTypes: string[]
}

function step(steps: E2eTestStep[], name: string, ok: boolean, detail?: string) {
  steps.push({ name, ok, detail })
  return ok
}

export async function runVerifierE2eTest(
  baseUrl: string,
  studentId = 'STU-001',
  disclose: string[] = ['name', 'degree', 'major'],
): Promise<E2eTestResult> {
  const steps: E2eTestStep[] = []
  const origin = baseUrl.replace(/\/$/, '')

  const holderKp = generateEd25519KeyPair()
  const holderDid = publicKeyToDidKey(holderKp.publicKey)

  const credRes = await fetch(
    `${origin}/api/demo-issuer/credential?subject=${encodeURIComponent(holderDid)}&studentId=${encodeURIComponent(studentId)}`,
  )
  if (!step(steps, 'Issue credential from trust domain', credRes.ok, `HTTP ${credRes.status}`)) {
    return { ok: false, studentId, holderDid, steps }
  }

  const credBody = (await credRes.json()) as CredentialResponse
  step(steps, 'Credential format', credBody.format === 'vc+sd-jwt', credBody.format)
  step(steps, 'Credential type (VCT)', Boolean(credBody.vct), credBody.vct)

  const reqRes = await fetch(`${origin}/api/demo-verifier/request`)
  if (!step(steps, 'Create verifier session', reqRes.ok, `HTTP ${reqRes.status}`)) {
    return { ok: false, studentId, holderDid, vct: credBody.vct, steps }
  }

  const req = (await reqRes.json()) as VerifierRequest
  const typeMatch = [credBody.vct].some((t) => req.acceptedTypes.includes(t))
  step(
    steps,
    'VCT accepted by verifier',
    typeMatch,
    typeMatch ? credBody.vct : `${credBody.vct} not in ${req.acceptedTypes.join(', ')}`,
  )
  if (!typeMatch) {
    return { ok: false, studentId, holderDid, vct: credBody.vct, steps }
  }

  const now = Math.floor(Date.now() / 1000)
  const presentation = presentSdJwtVc({
    sdJwtVc: credBody.credential,
    selectDisclose: disclose.filter((n) => credBody.selectivelyDisclosable.includes(n)),
    holderPrivateKey: holderKp.privateKey,
    holderDid,
    audience: req.audience,
    nonce: req.nonce,
    now,
  })
  step(steps, 'Build SD-JWT presentation + KB-JWT', presentation.includes('~'), `${disclose.join(', ')} disclosed`)

  const verifyRes = await fetch(`${origin}/api/demo-verifier/verify`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      presentation,
      format: 'vc+sd-jwt',
      expectedNonce: req.nonce,
    }),
  })
  const verify = (await verifyRes.json()) as E2eTestResult['verify']
  step(steps, 'POST /api/demo-verifier/verify', verifyRes.ok, `HTTP ${verifyRes.status}`)
  step(steps, 'Verification valid', Boolean(verify?.valid))

  const ok = steps.every((s) => s.ok)
  return {
    ok,
    studentId,
    holderDid,
    vct: credBody.vct,
    steps,
    verify,
  }
}
