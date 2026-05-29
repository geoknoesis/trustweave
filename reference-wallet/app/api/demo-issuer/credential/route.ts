/**
 * Demo issuer credential endpoint — SD-JWT VC profile.
 *
 * Phase 2.5 upgrade from plain VC-JWT to SD-JWT VC (IETF draft-ietf-oauth-sd-jwt-vc).
 * The credential is now returned as `<issuer-jwt>~<disclosure1>~<disclosure2>~...`,
 * with personal claims (name, degree, major, etc.) marked selectively disclosable.
 * The always-visible claims are `issuer` and `vct` (credential type identifier).
 *
 * Format identifier: `vc+sd-jwt` (per draft).
 */
import { NextRequest, NextResponse } from 'next/server'
import { issueSdJwtVc } from '@/lib/sdjwt'
import { getIssuer } from '@/lib/server-keys'

interface CredentialResponse {
  format: 'vc+sd-jwt'
  credential: string
  issuer: string
  /** Names of claims the issuer marked as selectively disclosable. */
  selectivelyDisclosable: string[]
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

  // What's selectively disclosable vs always visible:
  // - The holder MUST decide which personal claims to reveal at presentation time.
  // - `iss`, `iat`, `nbf`, `exp`, `vct`, `sub`, `cnf` always travel with the credential —
  //   those are credential metadata, not personal data.
  const selectivelyDisclosable = [
    { name: 'name', value: 'Demo Holder' },
    { name: 'degree', value: 'Bachelor of Science' },
    { name: 'major', value: 'Computer Science' },
    { name: 'institution', value: 'TrustWeave Demo University' },
    { name: 'graduationDate', value: '2026-05-29' },
    { name: 'gpa', value: '3.8' },
  ]

  const sdJwtVc = issueSdJwtVc({
    issuerDid: issuer.did,
    issuerPrivateKey: issuer.keyPair.privateKey,
    issuerKid: `${issuer.did}#${issuer.did.slice('did:key:'.length)}`,
    holderDid: subject,
    alwaysVisible: {
      jti: `urn:uuid:${crypto.randomUUID()}`,
    },
    selectivelyDisclosable,
    vct: 'BachelorOfScienceDegree',
    now,
  })

  return NextResponse.json({
    format: 'vc+sd-jwt',
    credential: sdJwtVc,
    issuer: issuer.did,
    selectivelyDisclosable: selectivelyDisclosable.map((c) => c.name),
  })
}
