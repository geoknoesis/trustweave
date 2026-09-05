import { NextRequest, NextResponse } from 'next/server'

/** A fresh nonce authorizes framework scripts without authorizing injected inline JavaScript. */
export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString('base64')
  const development = process.env.NODE_ENV === 'development'
  const policy = [
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${development ? " 'unsafe-eval'" : ''}`,
    "script-src-attr 'none'",
    "object-src 'none'",
    "base-uri 'self'",
    "frame-ancestors 'none'",
  ].join('; ')
  const headers = new Headers(request.headers)
  headers.set('Content-Security-Policy', policy)
  headers.set('x-nonce', nonce)
  const response = NextResponse.next({ request: { headers } })
  response.headers.set('Content-Security-Policy', policy)
  response.headers.set('Cache-Control', 'private, no-store')
  return response
}

export const config = {
  matcher: ['/((?!api|_next/static|_next/image|favicon.ico).*)'],
}
