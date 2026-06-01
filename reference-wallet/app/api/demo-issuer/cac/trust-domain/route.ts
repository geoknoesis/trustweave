import { NextResponse } from 'next/server'
import { getCacIssuer } from '@/lib/server-keys'
import {
  cacAcceptedCredentialTypes,
  getCacTrustDomain,
  listCacSubjectSummaries,
} from '@/lib/trust-domains/demo-cac'

export async function GET() {
  const issuer = getCacIssuer()
  const domain = getCacTrustDomain()

  return NextResponse.json({
    issuerDid: issuer.did,
    domain,
    subjects: listCacSubjectSummaries(),
    acceptedCredentialTypes: cacAcceptedCredentialTypes(),
  })
}
