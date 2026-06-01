/**
 * Full degree record for one student in the demo-university trust domain.
 * Used by graduate portal detail pages (institution, GPA, graduation date, etc.).
 */
import { NextRequest, NextResponse } from 'next/server'
import {
  getDemoUniversityTrustDomain,
  resolveDemoUniversityDegree,
  degreeRecordDisclosableNames,
} from '@/lib/trust-domains/demo-university'
import { getIssuer } from '@/lib/server-keys'

export async function GET(
  _req: NextRequest,
  context: { params: Promise<{ studentId: string }> },
) {
  const { studentId } = await context.params

  try {
    const record = resolveDemoUniversityDegree(studentId)
    const domain = getDemoUniversityTrustDomain()
    const issuer = getIssuer()

    return NextResponse.json({
      issuerDid: issuer.did,
      domain,
      degree: record,
      selectivelyDisclosable: degreeRecordDisclosableNames(record),
    })
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : String(e) },
      { status: 404 },
    )
  }
}
