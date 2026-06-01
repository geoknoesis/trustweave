/**
 * Demo verifier verification endpoint — handles both VC-JWT (legacy Phase 1) and
 * SD-JWT VC (Phase 2.5) presentations.
 *
 * For SD-JWT VC, the verifier:
 *  1. Splits the presentation into <issuer-jwt>~<disclosure>~...~<kb-jwt>.
 *  2. Resolves the issuer did:key, verifies the issuer JWT signature.
 *  3. For each presented disclosure, computes its SHA-256 hash and checks it
 *     appears in the issuer JWT's `_sd` array.
 *  4. Resolves the holder did:key (from the issuer JWT's `cnf.kid`), verifies the
 *     KB-JWT signature. KB-JWT MUST be signed by the holder.
 *  5. Validates audience + nonce in the KB-JWT match this verifier + the expected
 *     challenge.
 *  6. Validates `sd_hash` in the KB-JWT equals SHA-256 of the
 *     <issuer-jwt>~<disclosure>~...~ prefix (replay-binds the KB-JWT to exactly the
 *     disclosures shown).
 *  7. Reconstructs disclosed claims as {name: value} and reports them, plus the
 *     names of any claims the issuer marked disclosable but the holder withheld.
 */
import { NextRequest, NextResponse } from 'next/server'
import { verifyJws, didKeyToPublicKey, b64uDecodeString } from '@/lib/crypto'
import { decodeSdJwtVc } from '@/lib/sdjwt'
import { sha256 } from '@noble/hashes/sha256'
import { getVerifier } from '@/lib/server-keys'
import { recordVerification } from '@/lib/verification-inbox'

interface VerificationCheck { step: string; passed: boolean; detail?: string }

interface VerifiedCredential {
  type: string[]
  issuer: string
  subject: string
  disclosedClaims: Record<string, unknown>
  /** SD-JWT VC: names of claims the issuer marked disclosable that the holder withheld. */
  withheldClaimNames?: string[]
}

interface VerificationResponse {
  valid: boolean
  checks: VerificationCheck[]
  holder?: string
  credentials?: VerifiedCredential[]
}

interface VerifyRequestBody {
  /** Phase 2.5 unified field — SD-JWT VC presentation or VP-JWT. */
  presentation?: string
  /** Phase 1 legacy field name — falls back to `presentation` if missing. */
  vp?: string
  format?: 'vc+jwt' | 'vc+sd-jwt'
  expectedNonce?: string
}

export async function POST(req: NextRequest): Promise<NextResponse<VerificationResponse>> {
  const checks: VerificationCheck[] = []
  let valid = true
  const recordCheck = (step: string, passed: boolean, detail?: string) => {
    checks.push({ step, passed, detail })
    if (!passed) valid = false
  }

  let body: VerifyRequestBody = {}
  try {
    body = await req.json()
  } catch {
    recordCheck('Parse request body', false, 'request body is not valid JSON')
    return NextResponse.json({ valid: false, checks })
  }

  const presentation = body.presentation ?? body.vp
  if (!presentation || typeof presentation !== 'string') {
    recordCheck('Parse request body', false, 'missing `presentation` (or legacy `vp`) field')
    return NextResponse.json({ valid: false, checks })
  }

  // Format detection. Body MAY tell us; if not, sniff: SD-JWT VC contains `~`.
  const format: 'vc+jwt' | 'vc+sd-jwt' = body.format
    ?? (presentation.includes('~') ? 'vc+sd-jwt' : 'vc+jwt')

  if (format === 'vc+sd-jwt') {
    const result = await verifySdJwtVc(presentation, body.expectedNonce, checks, recordCheck)
    recordVerification({
      valid: result.valid,
      nonce: body.expectedNonce,
      holder: result.holder,
      checks: result.checks,
      credentials: result.credentials,
    })
    return NextResponse.json(result)
  }
  const result = await verifyVpJwt(presentation, body.expectedNonce, checks, recordCheck)
  recordVerification({
    valid: result.valid,
    nonce: body.expectedNonce,
    holder: result.holder,
    checks: result.checks,
    credentials: result.credentials,
  })
  return NextResponse.json(result)
}

// ============================================================
// SD-JWT VC verification (Phase 2.5)
// ============================================================
async function verifySdJwtVc(
  sdJwtVc: string,
  expectedNonce: string | undefined,
  checks: VerificationCheck[],
  recordCheck: (s: string, p: boolean, d?: string) => void,
): Promise<VerificationResponse> {
  // Step 1: structural decode.
  let decoded: ReturnType<typeof decodeSdJwtVc>
  try {
    decoded = decodeSdJwtVc(sdJwtVc)
    recordCheck('Parse SD-JWT VC structure', true, `${decoded.disclosures.length} disclosure(s), kb-jwt: ${decoded.kbJwt ? 'present' : 'missing'}`)
  } catch (e) {
    recordCheck('Parse SD-JWT VC structure', false, errorMessage(e))
    return { valid: false, checks }
  }

  if (!decoded.kbJwt) {
    recordCheck('KB-JWT present', false, 'SD-JWT VC presentation requires a key-binding JWT')
    return { valid: false, checks }
  }

  // Step 2: issuer JWT signature.
  const issuerDid = String(decoded.issuerPayload.iss ?? '')
  if (!issuerDid.startsWith('did:key:')) {
    recordCheck('Resolve issuer DID', false, `issuer is not a did:key: ${issuerDid}`)
    return { valid: false, checks }
  }
  try {
    const issuerPub = didKeyToPublicKey(issuerDid)
    verifyJws(decoded.issuerJwt, issuerPub)
    recordCheck('Verify issuer JWT signature', true, `issuer: ${issuerDid}`)
  } catch (e) {
    recordCheck('Verify issuer JWT signature', false, errorMessage(e))
    return { valid: false, checks }
  }

  // Step 3: disclosure hashes must appear in issuer's _sd array.
  const issuerSdHashes = (decoded.issuerPayload._sd ?? []) as string[]
  if (!Array.isArray(issuerSdHashes)) {
    recordCheck('Disclosure hash binding', false, 'issuer JWT has no _sd array')
    return { valid: false, checks }
  }
  const disclosedClaims: Record<string, unknown> = {}
  for (const d of decoded.disclosures) {
    if (!issuerSdHashes.includes(d.hash)) {
      recordCheck(`Disclosure '${d.name}' hash in _sd`, false, `expected hash ${d.hash} not found`)
    } else {
      disclosedClaims[d.name] = d.value
    }
  }
  if (decoded.disclosures.every((d) => issuerSdHashes.includes(d.hash))) {
    recordCheck('All disclosure hashes bound to issuer _sd', true, `${decoded.disclosures.length} disclosure(s) verified`)
  }

  // Step 4: temporal validity of issuer JWT.
  const now = Math.floor(Date.now() / 1000)
  const exp = Number(decoded.issuerPayload.exp ?? 0)
  const nbf = Number(decoded.issuerPayload.nbf ?? decoded.issuerPayload.iat ?? 0)
  if (exp && now > exp) {
    recordCheck('Issuer JWT temporal validity', false, 'expired')
  } else if (nbf && now < nbf) {
    recordCheck('Issuer JWT temporal validity', false, 'not yet valid')
  } else {
    recordCheck('Issuer JWT temporal validity', true)
  }

  // Step 5: KB-JWT signature using the holder key bound in `cnf`.
  const cnf = decoded.issuerPayload.cnf as { kid?: string } | undefined
  const holderDid = String(cnf?.kid ?? '')
  if (!holderDid.startsWith('did:key:')) {
    recordCheck('Resolve holder DID (cnf.kid)', false, `cnf.kid is not a did:key: ${holderDid}`)
    return { valid: false, checks, credentials: undefined }
  }
  let kbPayload: Record<string, unknown>
  try {
    const holderPub = didKeyToPublicKey(holderDid)
    kbPayload = verifyJws(decoded.kbJwt, holderPub)
    recordCheck('Verify KB-JWT signature (holder)', true, `holder: ${holderDid}`)
  } catch (e) {
    recordCheck('Verify KB-JWT signature (holder)', false, errorMessage(e))
    return { valid: false, checks }
  }

  // Step 6: audience + nonce binding in KB-JWT.
  const verifier = getVerifier()
  if (kbPayload.aud !== verifier.did) {
    recordCheck('Audience binding (KB-JWT aud)', false, `aud=${String(kbPayload.aud)} does not match verifier ${verifier.did}`)
  } else {
    recordCheck('Audience binding (KB-JWT aud)', true)
  }
  if (expectedNonce && kbPayload.nonce !== expectedNonce) {
    recordCheck('Nonce binding (replay protection)', false, 'KB-JWT nonce does not match expected challenge')
  } else {
    recordCheck('Nonce binding (replay protection)', true)
  }

  // Step 7: sd_hash binds the KB-JWT to exactly these disclosures.
  const prefix = [decoded.issuerJwt, ...decoded.disclosures.map((d) => d.raw), ''].join('~')
  const expectedSdHash = b64uEncodeBytes(sha256(new TextEncoder().encode(prefix)))
  if (kbPayload.sd_hash !== expectedSdHash) {
    recordCheck('sd_hash binding (disclosure-set integrity)', false, 'KB-JWT sd_hash does not match the presented disclosure set')
  } else {
    recordCheck('sd_hash binding (disclosure-set integrity)', true)
  }

  // Step 8: derive withheld claims (issuer-disclosable minus presented).
  // We can't know the full set of disclosable claim names from the issuer JWT alone
  // (only their hashes), so we report a count rather than names. The wallet UI knows
  // the names because it stored them at receipt time.
  const totalDisclosable = issuerSdHashes.length
  const presented = decoded.disclosures.length
  const withheldCount = totalDisclosable - presented
  const withheldClaimNames = withheldCount > 0
    ? [`${withheldCount} additional claim(s) withheld (hashes only — names unknown to verifier)`]
    : undefined

  const valid = checks.every((c) => c.passed)
  return {
    valid,
    checks,
    holder: holderDid,
    credentials: [{
      type: [String(decoded.issuerPayload.vct ?? 'Credential')],
      issuer: issuerDid,
      subject: holderDid,
      disclosedClaims,
      withheldClaimNames,
    }],
  }
}

// ============================================================
// Legacy VC-JWT VP verification (Phase 1)
// ============================================================
async function verifyVpJwt(
  vp: string,
  expectedNonce: string | undefined,
  checks: VerificationCheck[],
  recordCheck: (s: string, p: boolean, d?: string) => void,
): Promise<VerificationResponse> {
  let vpPayload: Record<string, unknown>
  try {
    const parts = vp.split('.')
    if (parts.length !== 3) throw new Error('VP JWS must have three parts')
    const unverifiedPayload = JSON.parse(b64uDecodeString(parts[1])) as Record<string, unknown>
    const holderDid = String(unverifiedPayload.iss ?? '')
    if (!holderDid.startsWith('did:key:')) throw new Error(`VP issuer is not a did:key: ${holderDid}`)
    const holderPub = didKeyToPublicKey(holderDid)
    vpPayload = verifyJws(vp, holderPub)
    recordCheck('Verify VP signature (holder)', true, `holder: ${holderDid}`)
  } catch (e) {
    recordCheck('Verify VP signature (holder)', false, errorMessage(e))
    return { valid: false, checks }
  }
  const holderDid = String(vpPayload.iss ?? '')

  const verifier = getVerifier()
  recordCheck('Audience binding', vpPayload.aud === verifier.did,
    vpPayload.aud === verifier.did ? undefined : `aud=${String(vpPayload.aud)} != verifier ${verifier.did}`)

  if (expectedNonce) {
    recordCheck('Nonce binding (replay protection)', vpPayload.nonce === expectedNonce,
      vpPayload.nonce === expectedNonce ? undefined : 'VP nonce does not match expected challenge')
  } else {
    recordCheck('Nonce binding (replay protection)', true)
  }

  const now = Math.floor(Date.now() / 1000)
  const vpExp = Number(vpPayload.exp ?? 0)
  recordCheck('VP temporal validity', !(vpExp && now > vpExp),
    vpExp && now > vpExp ? `VP expired at ${new Date(vpExp * 1000).toISOString()}` : undefined)

  const vpInner = vpPayload.vp as Record<string, unknown> | undefined
  const vcJwts = (vpInner?.verifiableCredential ?? []) as string[]
  if (!Array.isArray(vcJwts) || vcJwts.length === 0) {
    recordCheck('Inner credentials present', false, 'VP contains no credentials')
    return { valid: false, checks, holder: holderDid }
  }
  recordCheck('Inner credentials present', true, `count: ${vcJwts.length}`)

  const credentials: VerifiedCredential[] = []
  for (let i = 0; i < vcJwts.length; i++) {
    const vcJwt = vcJwts[i]
    try {
      const parts = vcJwt.split('.')
      const unverified = JSON.parse(b64uDecodeString(parts[1])) as Record<string, unknown>
      const issuerDid = String(unverified.iss ?? '')
      if (!issuerDid.startsWith('did:key:')) throw new Error(`VC issuer is not a did:key: ${issuerDid}`)
      const issuerPub = didKeyToPublicKey(issuerDid)
      const vcPayload = verifyJws(vcJwt, issuerPub)
      const vc = vcPayload.vc as Record<string, unknown> | undefined
      const types = ((vc?.type ?? []) as string[]).map(String)
      const subject = String(vcPayload.sub ?? '')

      recordCheck(`VC #${i + 1} holder binding`, subject === holderDid,
        subject === holderDid ? undefined : `VC subject ${subject} != VP holder ${holderDid}`)

      const exp = Number(vcPayload.exp ?? 0)
      const nbf = Number(vcPayload.nbf ?? vcPayload.iat ?? 0)
      const tempOk = !((exp && now > exp) || (nbf && now < nbf))
      recordCheck(`VC #${i + 1} temporal validity`, tempOk,
        !tempOk ? (exp && now > exp ? 'expired' : 'not yet valid') : undefined)

      recordCheck(`VC #${i + 1} signature (issuer ${issuerDid.slice(0, 22)}…)`, true)
      credentials.push({
        type: types,
        issuer: issuerDid,
        subject,
        disclosedClaims: (vc?.credentialSubject ?? {}) as Record<string, unknown>,
      })
    } catch (e) {
      recordCheck(`VC #${i + 1} signature`, false, errorMessage(e))
    }
  }

  return { valid: checks.every((c) => c.passed), checks, holder: holderDid, credentials }
}

function errorMessage(e: unknown): string { return e instanceof Error ? e.message : String(e) }

/** Local helper — same as crypto.b64uEncode but for sha256 output without importing crypto.ts twice. */
function b64uEncodeBytes(bytes: Uint8Array): string {
  let binary = ''
  for (const b of bytes) binary += String.fromCharCode(b)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

