/**
 * Trust domain discovery for the demo issuer.
 *
 * Exposes the predefined demo-university trust domain and its preloaded
 * degree roster (summaries only — full claims are issued on credential request).
 */
import { NextResponse } from 'next/server'
import { getIssuer } from '@/lib/server-keys'
import {
  demoUniversityAcceptedTypes,
  getDemoUniversityTrustDomain,
  listDemoUniversityDegreeSummaries,
} from '@/lib/trust-domains/demo-university'

export async function GET() {
  const issuer = getIssuer()
  const domain = getDemoUniversityTrustDomain()

  return NextResponse.json({
    issuerDid: issuer.did,
    domain,
    degrees: listDemoUniversityDegreeSummaries(),
    acceptedCredentialTypes: demoUniversityAcceptedTypes(),
  })
}
