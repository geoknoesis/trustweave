import { NextRequest, NextResponse } from 'next/server'
import { latestVerification, listRecentVerifications, verificationForNonce } from '@/lib/verification-inbox'

export async function GET(req: NextRequest) {
  const nonce = req.nextUrl.searchParams.get('nonce')
  if (nonce) {
    return NextResponse.json({
      latest: verificationForNonce(nonce),
      recent: listRecentVerifications(5).filter((v) => v.nonce === nonce),
    })
  }
  return NextResponse.json({
    latest: latestVerification(),
    recent: listRecentVerifications(5),
  })
}
