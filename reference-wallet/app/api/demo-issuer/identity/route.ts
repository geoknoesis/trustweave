import { NextResponse } from 'next/server'
import { getCacIssuer, getFaaIssuer, getIssuer } from '@/lib/server-keys'

/**
 * The issuer identities of THIS backend. The web wallet reads this same-origin endpoint (never the
 * credential offer) to learn which issuer DIDs belong to the backend it is configured to use, so an
 * offer cannot nominate its own issuer for trust.
 */
export async function GET() {
  return NextResponse.json(
    { issuerDids: [getIssuer().did, getFaaIssuer().did, getCacIssuer().did] },
    { headers: { 'cache-control': 'no-store' } },
  )
}
