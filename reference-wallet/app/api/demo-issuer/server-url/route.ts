import { NextRequest, NextResponse } from 'next/server'
import { guessMobileReachableUrl } from '@/lib/server-url'

/** Helps the issuer QR page pick a URL phones on the LAN can reach. */
export async function GET(req: NextRequest) {
  const host = req.headers.get('host') ?? 'localhost:3000'
  const proto = req.headers.get('x-forwarded-proto') ?? 'http'
  const origin = `${proto}://${host}`.replace(/\/$/, '')

  const port = Number(host.split(':')[1] ?? 3000)
  const mobileReachableUrl = guessMobileReachableUrl(Number.isFinite(port) ? port : 3000)

  const onLocalhost = origin.includes('127.0.0.1') || origin.includes('localhost')

  return NextResponse.json({
    origin,
    mobileReachableUrl,
    hint:
      mobileReachableUrl && onLocalhost
        ? `Phones cannot use ${origin}. QR uses ${mobileReachableUrl} instead.`
        : null,
  })
}
