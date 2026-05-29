/**
 * Demo verifier verification endpoint.
 *
 * Validates a Verifiable Presentation:
 *  1. Parses the outer VP-JWT, resolves holder did:key, verifies the VP signature.
 *  2. For each inner VC-JWT, resolves issuer did:key, verifies the VC signature.
 *  3. Checks the VP audience matches this verifier, and the nonce is the expected
 *     challenge.
 *  4. Checks each VC is currently valid (nbf/exp window).
 *
 * Returns a structured verification result with per-step pass/fail breakdown — this
 * is the "killer demo moment" screen, per the design doc §6.4.
 */
import { NextRequest, NextResponse } from 'next/server'
import { verifyJws, didKeyToPublicKey, b64uDecode } from '@/lib/crypto'
import { getVerifier } from '@/lib/server-keys'

interface VerificationCheck {
  step: string
  passed: boolean
  detail?: string
}

interface VerificationResponse {
  valid: boolean
  checks: VerificationCheck[]
  holder?: string
  credentials?: Array<{
    type: string[]
    issuer: string
    subject: string
    disclosedClaims: Record<string, unknown>
  }>
}

export async function POST(req: NextRequest): Promise<NextResponse<VerificationResponse>> {
  const checks: VerificationCheck[] = []
  let valid = true
  const recordCheck = (step: string, passed: boolean, detail?: string) => {
    checks.push({ step, passed, detail })
    if (!passed) valid = false
  }

  let body: { vp?: string; expectedNonce?: string } = {}
  try {
    body = await req.json()
  } catch {
    recordCheck('Parse request body', false, 'request body is not valid JSON')
    return NextResponse.json({ valid: false, checks })
  }

  const { vp, expectedNonce } = body
  if (!vp || typeof vp !== 'string') {
    recordCheck('Parse request body', false, 'missing `vp` field')
    return NextResponse.json({ valid: false, checks })
  }

  // Step 1: parse the outer VP-JWT and verify signature against holder did:key.
  let vpPayload: Record<string, unknown>
  try {
    const parts = vp.split('.')
    if (parts.length !== 3) throw new Error('VP JWS must have three parts')
    const unverifiedPayload = JSON.parse(
      new TextDecoder().decode(b64uDecode(parts[1])),
    ) as Record<string, unknown>
    const holderDid = String(unverifiedPayload.iss ?? '')
    if (!holderDid.startsWith('did:key:')) {
      throw new Error(`VP issuer is not a did:key: ${holderDid}`)
    }
    const holderPublicKey = didKeyToPublicKey(holderDid)
    vpPayload = verifyJws(vp, holderPublicKey)
    recordCheck('Verify VP signature (holder)', true, `holder: ${holderDid}`)
  } catch (e) {
    recordCheck('Verify VP signature (holder)', false, errorMessage(e))
    return NextResponse.json({ valid: false, checks })
  }

  const holderDid = String(vpPayload.iss ?? '')

  // Step 2: audience binding — VP must be addressed to this verifier.
  const verifier = getVerifier()
  if (vpPayload.aud !== verifier.did) {
    recordCheck(
      'Audience binding',
      false,
      `VP aud=${String(vpPayload.aud)} does not match verifier ${verifier.did}`,
    )
  } else {
    recordCheck('Audience binding', true)
  }

  // Step 3: nonce binding — replay protection.
  if (expectedNonce && vpPayload.nonce !== expectedNonce) {
    recordCheck(
      'Nonce binding (replay protection)',
      false,
      `VP nonce does not match expected challenge`,
    )
  } else {
    recordCheck('Nonce binding (replay protection)', true)
  }

  // Step 4: VP temporal validity.
  const now = Math.floor(Date.now() / 1000)
  const vpExp = Number(vpPayload.exp ?? 0)
  if (vpExp && now > vpExp) {
    recordCheck('VP temporal validity', false, `VP expired at ${new Date(vpExp * 1000).toISOString()}`)
  } else {
    recordCheck('VP temporal validity', true)
  }

  // Step 5: inner credentials — verify each VC's signature against its issuer did:key.
  const vpInner = vpPayload.vp as Record<string, unknown> | undefined
  const vcJwts = (vpInner?.verifiableCredential ?? []) as string[]
  if (!Array.isArray(vcJwts) || vcJwts.length === 0) {
    recordCheck('Inner credentials present', false, 'VP contains no credentials')
    return NextResponse.json({ valid: false, checks, holder: holderDid })
  }
  recordCheck('Inner credentials present', true, `count: ${vcJwts.length}`)

  const credentials: NonNullable<VerificationResponse['credentials']> = []
  for (let i = 0; i < vcJwts.length; i++) {
    const vcJwt = vcJwts[i]
    try {
      const parts = vcJwt.split('.')
      const unverified = JSON.parse(
        new TextDecoder().decode(b64uDecode(parts[1])),
      ) as Record<string, unknown>
      const issuerDid = String(unverified.iss ?? '')
      if (!issuerDid.startsWith('did:key:')) {
        throw new Error(`VC issuer is not a did:key: ${issuerDid}`)
      }
      const issuerPublicKey = didKeyToPublicKey(issuerDid)
      const vcPayload = verifyJws(vcJwt, issuerPublicKey)
      const vc = vcPayload.vc as Record<string, unknown> | undefined
      const types = ((vc?.type ?? []) as string[]).map(String)

      // Subject binding — VC subject must be the VP holder (holder binding).
      const subject = String(vcPayload.sub ?? '')
      if (subject !== holderDid) {
        recordCheck(
          `VC #${i + 1} holder binding`,
          false,
          `VC subject ${subject} does not match VP holder ${holderDid}`,
        )
      } else {
        recordCheck(`VC #${i + 1} holder binding`, true)
      }

      // Temporal validity.
      const exp = Number(vcPayload.exp ?? 0)
      const nbf = Number(vcPayload.nbf ?? vcPayload.iat ?? 0)
      if (exp && now > exp) {
        recordCheck(`VC #${i + 1} temporal validity`, false, 'expired')
      } else if (nbf && now < nbf) {
        recordCheck(`VC #${i + 1} temporal validity`, false, 'not yet valid')
      } else {
        recordCheck(`VC #${i + 1} temporal validity`, true)
      }

      recordCheck(`VC #${i + 1} signature (issuer ${issuerDid.slice(0, 22)}…)`, true)
      credentials.push({
        type: types,
        issuer: issuerDid,
        subject,
        disclosedClaims: (vc?.credentialSubject ?? {}) as Record<string, unknown>,
      })
    } catch (e) {
      recordCheck(`VC #${i + 1} signature`, false, errorMessage(e))
    }
  }

  return NextResponse.json({
    valid,
    checks,
    holder: holderDid,
    credentials,
  })
}

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}
