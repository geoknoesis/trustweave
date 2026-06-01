/**
 * Airspace gate presentation request — creates a session bound to activity + location.
 */
import { NextRequest, NextResponse } from 'next/server'
import { createGateSession } from '@/lib/gate-sessions'
import { demoSfAirspaceAcceptedTypes } from '@/lib/trust-domains/demo-sf-airspace'
import { getVerifier } from '@/lib/server-keys'

interface GateRequestBody {
  activityType?: string
  lat?: number
  lon?: number
}

export async function POST(req: NextRequest) {
  let body: GateRequestBody = {}
  try {
    body = await req.json()
  } catch {
    body = {}
  }

  const activityType = body.activityType?.trim() || 'data-collection'
  const lat = typeof body.lat === 'number' ? body.lat : 37.7749
  const lon = typeof body.lon === 'number' ? body.lon : -122.4194

  const session = createGateSession(activityType, lat, lon)
  const verifier = getVerifier()

  return NextResponse.json({
    verifier: verifier.did,
    audience: verifier.did,
    nonce: session.nonce,
    acceptedTypes: demoSfAirspaceAcceptedTypes(),
    activityType: session.activityType,
    lat: session.lat,
    lon: session.lon,
  })
}
