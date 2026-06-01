/**
 * Airspace gate verification — cryptographic proof + geographic / activity policy.
 */
import { NextRequest, NextResponse } from 'next/server'
import { getGateSession } from '@/lib/gate-sessions'
import {
  checkAirspaceAuthorization,
  getDemoSfAirspaceTrustDomain,
} from '@/lib/trust-domains/demo-sf-airspace'
import { verifyPresentation } from '@/lib/verify-presentation'
import { recordVerification } from '@/lib/verification-inbox'

interface VerifyRequestBody {
  presentation?: string
  vp?: string
  format?: 'vc+jwt' | 'vc+sd-jwt'
  expectedNonce?: string
}

export async function POST(req: NextRequest) {
  let body: VerifyRequestBody = {}
  try {
    body = await req.json()
  } catch {
    return NextResponse.json({
      valid: false,
      checks: [{ step: 'Parse request body', passed: false, detail: 'invalid JSON' }],
    })
  }

  const presentation = body.presentation ?? body.vp
  if (!presentation || typeof presentation !== 'string') {
    return NextResponse.json({
      valid: false,
      checks: [{ step: 'Parse request body', passed: false, detail: 'missing presentation' }],
    })
  }

  const session = body.expectedNonce ? getGateSession(body.expectedNonce) : null
  if (!session) {
    return NextResponse.json({
      valid: false,
      checks: [{
        step: 'Gate session',
        passed: false,
        detail: 'Unknown or expired gate nonce — refresh the airspace gate page',
      }],
    })
  }

  const cryptoResult = verifyPresentation({
    presentation,
    format: body.format,
    expectedNonce: body.expectedNonce,
  })

  const checks = [...cryptoResult.checks]
  const domain = getDemoSfAirspaceTrustDomain()
  const disclosed = cryptoResult.credentials?.[0]?.disclosedClaims ?? {}
  const policy = checkAirspaceAuthorization(
    domain,
    disclosed,
    session.activityType,
    session.lat,
    session.lon,
  )

  checks.push({
    step: 'Airspace domain policy',
    passed: policy.authorized,
    detail: policy.reason,
  })

  const valid = cryptoResult.valid && policy.authorized
  const response = {
    valid,
    checks,
    holder: cryptoResult.holder,
    credentials: cryptoResult.credentials,
    gate: {
      activityType: session.activityType,
      lat: session.lat,
      lon: session.lon,
      domainId: domain.domainId,
    },
  }

  recordVerification({
    valid,
    nonce: body.expectedNonce,
    holder: cryptoResult.holder,
    checks,
    credentials: cryptoResult.credentials,
  })

  return NextResponse.json(response)
}
