/**
 * Demo verifier presentation-request endpoint.
 *
 * Conceptually a stand-in for an OID4VP Authorization Request: returns the parameters
 * the holder wallet needs to build a Verifiable Presentation (audience, nonce, accepted
 * credential types).
 *
 * Phase 1 simplification: returns the request as JSON directly, with no signed
 * request_uri or wallet redirect dance.
 */
import { NextResponse } from 'next/server'
import { cacAcceptedCredentialTypes } from '@/lib/trust-domains/demo-cac'
import { demoUniversityAcceptedTypes } from '@/lib/trust-domains/demo-university'
import { faaAcceptedCredentialTypes } from '@/lib/trust-domains/demo-faa-drone-registry'
import { getVerifier } from '@/lib/server-keys'

interface PresentationRequestResponse {
  verifier: string
  audience: string
  nonce: string
  acceptedTypes: string[]
}

export async function GET(): Promise<NextResponse<PresentationRequestResponse>> {
  const verifier = getVerifier()
  const acceptedTypes = [
    ...new Set([
      ...demoUniversityAcceptedTypes(),
      ...cacAcceptedCredentialTypes(),
      ...faaAcceptedCredentialTypes(),
    ]),
  ].sort()
  return NextResponse.json({
    verifier: verifier.did,
    audience: verifier.did,
    nonce: crypto.randomUUID(),
    acceptedTypes,
  })
}
