import { NextRequest, NextResponse } from 'next/server'
import {
  buildDronePhotoUrl,
  dronePhotoDigest,
  faaRecordDisclosableNames,
  getFaaTrustDomain,
  resolveFaaDrone,
} from '@/lib/trust-domains/demo-faa-drone-registry'
import { getFaaIssuer } from '@/lib/server-keys'

export async function GET(
  req: NextRequest,
  context: { params: Promise<{ droneId: string }> },
) {
  const { droneId } = await context.params

  try {
    const record = resolveFaaDrone(droneId)
    const domain = getFaaTrustDomain()
    const issuer = getFaaIssuer()
    const origin = req.nextUrl.origin

    return NextResponse.json({
      issuerDid: issuer.did,
      domain,
      drone: {
        ...record,
        photoUrl: buildDronePhotoUrl(origin, record.photoFile),
        photoDigest: dronePhotoDigest(record.photoFile),
      },
      selectivelyDisclosable: faaRecordDisclosableNames(record),
    })
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : String(e) },
      { status: 404 },
    )
  }
}
