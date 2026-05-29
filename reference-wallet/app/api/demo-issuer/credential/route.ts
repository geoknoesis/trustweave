/**
 * Demo issuer credential endpoint.
 *
 * Conceptually a stand-in for an OID4VCI Credential Endpoint: receives a holder DID
 * and returns a freshly-signed Verifiable Credential bound to that subject.
 *
 * Phase 1 simplification: direct GET with `?subject=did:key:...` query param. A real
 * OID4VCI flow would involve a credential offer URI, pre-authorized code or auth code,
 * and a token endpoint — deferred to Phase 1.1.
 */
import { NextRequest, NextResponse } from 'next/server'
import { signJws } from '@/lib/crypto'
import { getIssuer } from '@/lib/server-keys'

interface CredentialResponse {
  format: 'vc+jwt'
  credential: string
  issuer: string
}

export async function GET(req: NextRequest): Promise<NextResponse<CredentialResponse | { error: string }>> {
  const subject = req.nextUrl.searchParams.get('subject')
  if (!subject || !subject.startsWith('did:key:')) {
    return NextResponse.json(
      { error: 'subject query parameter must be a did:key' },
      { status: 400 },
    )
  }

  const issuer = getIssuer()
  const now = Math.floor(Date.now() / 1000)
  const oneYear = 365 * 24 * 60 * 60

  const payload = {
    iss: issuer.did,
    sub: subject,
    iat: now,
    nbf: now,
    exp: now + oneYear,
    jti: `urn:uuid:${crypto.randomUUID()}`,
    vc: {
      '@context': [
        'https://www.w3.org/ns/credentials/v2',
        'https://www.w3.org/ns/credentials/examples/v2',
      ],
      type: ['VerifiableCredential', 'BachelorOfScienceDegree'],
      issuer: issuer.did,
      validFrom: new Date(now * 1000).toISOString(),
      validUntil: new Date((now + oneYear) * 1000).toISOString(),
      credentialSubject: {
        id: subject,
        name: 'Demo Holder',
        degree: 'Bachelor of Science',
        major: 'Computer Science',
        institution: 'TrustWeave Demo University',
        graduationDate: '2026-05-29',
        gpa: '3.8',
      },
    },
  }

  const vcJwt = signJws(
    payload,
    issuer.keyPair.privateKey,
    `${issuer.did}#${issuer.did.slice('did:key:'.length)}`,
  )

  return NextResponse.json({
    format: 'vc+jwt',
    credential: vcJwt,
    issuer: issuer.did,
  })
}
