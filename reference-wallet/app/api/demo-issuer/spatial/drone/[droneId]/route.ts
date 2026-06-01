/**
 * Full drone authorization record for one roster entry in demo-sf-airspace.
 */
import { NextRequest, NextResponse } from 'next/server'
import {
  droneRecordDisclosableNames,
  getDemoSfAirspaceTrustDomain,
  resolveDemoSfAirspaceDrone,
} from '@/lib/trust-domains/demo-sf-airspace'
import { getIssuer } from '@/lib/server-keys'

export async function GET(
  _req: NextRequest,
  context: { params: Promise<{ droneId: string }> },
) {
  const { droneId } = await context.params

  try {
    const record = resolveDemoSfAirspaceDrone(droneId)
    const domain = getDemoSfAirspaceTrustDomain()
    const issuer = getIssuer()

    return NextResponse.json({
      issuerDid: issuer.did,
      domain,
      drone: record,
      selectivelyDisclosable: droneRecordDisclosableNames(record),
    })
  } catch (e) {
    return NextResponse.json(
      { error: e instanceof Error ? e.message : String(e) },
      { status: 404 },
    )
  }
}
