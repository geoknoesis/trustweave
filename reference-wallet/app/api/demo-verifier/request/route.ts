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
import { getVerifier } from '@/lib/server-keys'

interface PresentationRequestResponse {
  verifier: string
  audience: string
  nonce: string
  acceptedTypes: string[]
}

export async function GET(): Promise<NextResponse<PresentationRequestResponse>> {
  const verifier = getVerifier()
  return NextResponse.json({
    verifier: verifier.did,
    audience: verifier.did,
    nonce: crypto.randomUUID(),
    acceptedTypes: ['BachelorOfScienceDegree'],
  })
}
