/**
 * Issue activity authorization credentials from the demo-sf-airspace trust domain.
 *
 * Query params:
 *   - subject (required): drone agent holder did:key
 *   - droneId (optional): roster row, defaults to DRONE-001
 */
import { NextRequest, NextResponse } from 'next/server'
import { issueSdJwtVc } from '@/lib/sdjwt'
import { getIssuer } from '@/lib/server-keys'
import {
  droneRecordDisclosableNames,
  droneRecordToDisclosableClaims,
  getDemoSfAirspaceTrustDomain,
  resolveDemoSfAirspaceDrone,
} from '@/lib/trust-domains/demo-sf-airspace'

interface CredentialResponse {
  format: 'vc+sd-jwt'
  credential: string
  issuer: string
  trustDomainId: string
  droneId: string
  vct: string
  selectivelyDisclosable: string[]
}

export async function GET(req: NextRequest): Promise<NextResponse<CredentialResponse | { error: string }>> {
  const subject = req.nextUrl.searchParams.get('subject')
  if (!subject || !subject.startsWith('did:key:')) {
    return NextResponse.json(
      { error: 'subject query parameter must be a did:key' },
      { status: 400 },
    )
  }

  const droneIdParam = req.nextUrl.searchParams.get('droneId')

  let droneRecord
  try {
    droneRecord = resolveDemoSfAirspaceDrone(droneIdParam)
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : String(e) },
      { status: 404 },
    )
  }

  const domain = getDemoSfAirspaceTrustDomain()
  const selectivelyDisclosable = droneRecordToDisclosableClaims(droneRecord, domain)
  const issuer = getIssuer()
  const now = Math.floor(Date.now() / 1000)

  const sdJwtVc = issueSdJwtVc({
    issuerDid: issuer.did,
    issuerPrivateKey: issuer.keyPair.privateKey,
    issuerKid: `${issuer.did}#${issuer.did.slice('did:key:'.length)}`,
    holderDid: subject,
    alwaysVisible: {
      jti: `urn:uuid:${crypto.randomUUID()}`,
      trustDomainId: domain.domainId,
      droneId: droneRecord.droneId,
    },
    selectivelyDisclosable,
    vct: droneRecord.vct,
    now,
  })

  return NextResponse.json({
    format: 'vc+sd-jwt',
    credential: sdJwtVc,
    issuer: issuer.did,
    trustDomainId: domain.domainId,
    droneId: droneRecord.droneId,
    vct: droneRecord.vct,
    selectivelyDisclosable: droneRecordDisclosableNames(droneRecord),
  })
}
