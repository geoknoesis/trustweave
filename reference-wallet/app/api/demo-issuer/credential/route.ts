/**
 * Demo issuer credential endpoint — SD-JWT VC profile.
 *
 * Issues credentials from the predefined **demo-university** trust domain.
 * Degree claims are loaded from the preloaded registrar CSV
 * (`data/trust-domains/demo-university/degrees.csv`).
 *
 * Query params:
 *   - subject (required): holder did:key
 *   - studentId (optional): registrar row, defaults to STU-001
 *
 * Format identifier: `vc+sd-jwt` (per draft).
 */
import { NextRequest, NextResponse } from 'next/server'
import { issueSdJwtVc } from '@/lib/sdjwt'
import { getIssuer } from '@/lib/server-keys'
import {
  degreeRecordDisclosableNames,
  degreeRecordToDisclosableClaims,
  getDemoUniversityTrustDomain,
  resolveDemoUniversityDegree,
} from '@/lib/trust-domains/demo-university'

interface CredentialResponse {
  format: 'vc+sd-jwt'
  credential: string
  issuer: string
  trustDomainId: string
  studentId: string
  vct: string
  /** Names of claims the issuer marked as selectively disclosable. */
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

  const studentIdParam = req.nextUrl.searchParams.get('studentId')

  let degreeRecord
  try {
    degreeRecord = resolveDemoUniversityDegree(studentIdParam)
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : String(e) },
      { status: 404 },
    )
  }

  const selectivelyDisclosable = degreeRecordToDisclosableClaims(degreeRecord)
  const trustDomain = getDemoUniversityTrustDomain()
  const issuer = getIssuer()
  const now = Math.floor(Date.now() / 1000)

  const sdJwtVc = issueSdJwtVc({
    issuerDid: issuer.did,
    issuerPrivateKey: issuer.keyPair.privateKey,
    issuerKid: `${issuer.did}#${issuer.did.slice('did:key:'.length)}`,
    holderDid: subject,
    alwaysVisible: {
      jti: `urn:uuid:${crypto.randomUUID()}`,
      trustDomainId: trustDomain.domainId,
      studentId: degreeRecord.studentId,
    },
    selectivelyDisclosable,
    vct: degreeRecord.vct,
    now,
  })

  return NextResponse.json({
    format: 'vc+sd-jwt',
    credential: sdJwtVc,
    issuer: issuer.did,
    trustDomainId: trustDomain.domainId,
    studentId: degreeRecord.studentId,
    vct: degreeRecord.vct,
    selectivelyDisclosable: degreeRecordDisclosableNames(degreeRecord),
  })
}
