/**
 * Trust domain discovery for the demo SF Bay airspace spatial domain.
 */
import { NextResponse } from 'next/server'
import { getIssuer } from '@/lib/server-keys'
import {
  demoSfAirspaceAcceptedTypes,
  getDemoSfAirspaceTrustDomain,
  listDemoSfAirspaceDroneSummaries,
} from '@/lib/trust-domains/demo-sf-airspace'

export async function GET() {
  const issuer = getIssuer()
  const domain = getDemoSfAirspaceTrustDomain()

  return NextResponse.json({
    issuerDid: issuer.did,
    domain,
    drones: listDemoSfAirspaceDroneSummaries(),
    acceptedCredentialTypes: demoSfAirspaceAcceptedTypes(),
  })
}
