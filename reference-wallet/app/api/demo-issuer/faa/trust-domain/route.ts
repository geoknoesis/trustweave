import { NextResponse } from 'next/server'
import { getFaaIssuer } from '@/lib/server-keys'
import {
  faaAcceptedCredentialTypes,
  getFaaTrustDomain,
  listFaaDroneSummaries,
} from '@/lib/trust-domains/demo-faa-drone-registry'

export async function GET() {
  const issuer = getFaaIssuer()
  const domain = getFaaTrustDomain()

  return NextResponse.json({
    issuerDid: issuer.did,
    domain,
    drones: listFaaDroneSummaries(),
    acceptedCredentialTypes: faaAcceptedCredentialTypes(),
  })
}
