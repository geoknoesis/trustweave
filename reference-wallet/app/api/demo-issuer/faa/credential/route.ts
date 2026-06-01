/**
 * Issue FAA DroneIdentificationCredentials with registered aircraft photo metadata.
 */
import { NextRequest, NextResponse } from 'next/server'
import { issueSdJwtVc } from '@/lib/sdjwt'
import { getFaaIssuer } from '@/lib/server-keys'
import {
  faaRecordDisclosableNames,
  faaRecordToDisclosableClaims,
  getFaaTrustDomain,
  resolveFaaDrone,
} from '@/lib/trust-domains/demo-faa-drone-registry'

interface CredentialResponse {
  format: 'vc+sd-jwt'
  credential: string
  issuer: string
  trustDomainId: string
  droneId: string
  registrationNumber: string
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
    droneRecord = resolveFaaDrone(droneIdParam)
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : String(e) },
      { status: 404 },
    )
  }

  const domain = getFaaTrustDomain()
  const origin = req.nextUrl.origin
  const selectivelyDisclosable = faaRecordToDisclosableClaims(droneRecord, domain, origin)
  const issuer = getFaaIssuer()
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
      registrationNumber: droneRecord.registrationNumber,
      issuingAuthority: domain.authorityName,
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
    registrationNumber: droneRecord.registrationNumber,
    vct: droneRecord.vct,
    selectivelyDisclosable: faaRecordDisclosableNames(droneRecord),
  })
}
