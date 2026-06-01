import { NextRequest, NextResponse } from 'next/server'
import {
  buildSubjectPortraitUrl,
  cacRecordDisclosableNames,
  getCacTrustDomain,
  resolveCacSubject,
  subjectPortraitDigest,
} from '@/lib/trust-domains/demo-cac'
import { getCacIssuer } from '@/lib/server-keys'

export async function GET(
  req: NextRequest,
  { params }: { params: { personnelId: string } },
) {
  const personnelId = params.personnelId
  try {
    const record = resolveCacSubject(personnelId)
    const domain = getCacTrustDomain()
    const issuer = getCacIssuer()
    const origin = req.nextUrl.origin

    return NextResponse.json({
      issuerDid: issuer.did,
      domain,
      subject: {
        personnelId: record.personnelId,
        dodId: record.dodId,
        name: record.name,
        rank: record.rank,
        branch: record.branch,
        portraitFile: record.portraitFile,
        portraitUrl: buildSubjectPortraitUrl(origin, record.portraitFile),
        portraitDigest: subjectPortraitDigest(record.portraitFile),
        vct: record.vct,
      },
      selectivelyDisclosable: cacRecordDisclosableNames(record),
    })
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : String(e) },
      { status: 404 },
    )
  }
}
