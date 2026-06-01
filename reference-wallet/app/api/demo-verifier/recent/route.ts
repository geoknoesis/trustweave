import { NextResponse } from 'next/server'
import { latestVerification, listRecentVerifications, verificationForNonce } from '@/lib/verification-inbox'

/** Recent verification results for the demo verifier page (polled by the browser). */
export async function GET(req: Request) {
  const url = new URL(req.url)
  const limit = Math.min(Number(url.searchParams.get('limit') ?? 5), 20)
  const nonce = url.searchParams.get('nonce')
  if (nonce) {
    return NextResponse.json({
      latest: verificationForNonce(nonce),
      recent: listRecentVerifications(limit).filter((v) => v.nonce === nonce),
    })
  }
  return NextResponse.json({
    latest: latestVerification(),
    recent: listRecentVerifications(limit),
  })
}
