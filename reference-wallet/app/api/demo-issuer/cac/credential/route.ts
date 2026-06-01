/**
 * Issue Common Access Card (CAC) credentials with registered personnel portraits.
 */
import { NextRequest, NextResponse } from 'next/server'
import { issueSdJwtVc } from '@/lib/sdjwt'
import { getCacIssuer } from '@/lib/server-keys'
import {
  cacRecordDisclosableNames,
  cacRecordToDisclosableClaims,
  getCacTrustDomain,
  resolveCacSubject,
} from '@/lib/trust-domains/demo-cac'

interface CredentialResponse {
  format: 'vc+sd-jwt'
  credential: string
  issuer: string
  trustDomainId: string
  personnelId: string
  dodId: string
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

  const personnelIdParam = req.nextUrl.searchParams.get('personnelId')

  let subjectRecord
  try {
    subjectRecord = resolveCacSubject(personnelIdParam)
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : String(e) },
      { status: 404 },
    )
  }

  const domain = getCacTrustDomain()
  const origin = req.nextUrl.origin
  const selectivelyDisclosable = cacRecordToDisclosableClaims(subjectRecord, domain, origin)
  const issuer = getCacIssuer()
  const now = Math.floor(Date.now() / 1000)

  const sdJwtVc = issueSdJwtVc({
    issuerDid: issuer.did,
    issuerPrivateKey: issuer.keyPair.privateKey,
    issuerKid: `${issuer.did}#${issuer.did.slice('did:key:'.length)}`,
    holderDid: subject,
    alwaysVisible: {
      jti: `urn:uuid:${crypto.randomUUID()}`,
      trustDomainId: domain.domainId,
      personnelId: subjectRecord.personnelId,
      dodId: subjectRecord.dodId,
      issuingAuthority: domain.authorityName,
    },
    selectivelyDisclosable,
    vct: subjectRecord.vct,
    now,
  })

  return NextResponse.json({
    format: 'vc+sd-jwt',
    credential: sdJwtVc,
    issuer: issuer.did,
    trustDomainId: domain.domainId,
    personnelId: subjectRecord.personnelId,
    dodId: subjectRecord.dodId,
    vct: subjectRecord.vct,
    selectivelyDisclosable: cacRecordDisclosableNames(subjectRecord),
  })
}
