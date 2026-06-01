/**
 * Shared presentation verification for demo verifier and airspace gate endpoints.
 */
import 'server-only'
import { verifyJws, didKeyToPublicKey, b64uDecodeString } from '@/lib/crypto'
import { decodeSdJwtVc } from '@/lib/sdjwt'
import { sha256 } from '@noble/hashes/sha256'
import { getVerifier } from '@/lib/server-keys'

export interface VerificationCheck {
  step: string
  passed: boolean
  detail?: string
}

export interface VerifiedCredential {
  type: string[]
  issuer: string
  subject: string
  disclosedClaims: Record<string, unknown>
  withheldClaimNames?: string[]
}

export interface VerificationResponse {
  valid: boolean
  checks: VerificationCheck[]
  holder?: string
  credentials?: VerifiedCredential[]
}

export interface VerifyPresentationInput {
  presentation: string
  format?: 'vc+jwt' | 'vc+sd-jwt'
  expectedNonce?: string
}

export function verifyPresentation(input: VerifyPresentationInput): VerificationResponse {
  const checks: VerificationCheck[] = []
  const recordCheck = (step: string, passed: boolean, detail?: string) => {
    checks.push({ step, passed, detail })
  }

  const format: 'vc+jwt' | 'vc+sd-jwt' = input.format
    ?? (input.presentation.includes('~') ? 'vc+sd-jwt' : 'vc+jwt')

  if (format === 'vc+sd-jwt') {
    return verifySdJwtVc(input.presentation, input.expectedNonce, checks, recordCheck)
  }
  return verifyVpJwt(input.presentation, input.expectedNonce, checks, recordCheck)
}

function verifySdJwtVc(
  sdJwtVc: string,
  expectedNonce: string | undefined,
  checks: VerificationCheck[],
  recordCheck: (s: string, p: boolean, d?: string) => void,
): VerificationResponse {
  let decoded: ReturnType<typeof decodeSdJwtVc>
  try {
    decoded = decodeSdJwtVc(sdJwtVc)
    recordCheck(
      'Parse SD-JWT VC structure',
      true,
      `${decoded.disclosures.length} disclosure(s), kb-jwt: ${decoded.kbJwt ? 'present' : 'missing'}`,
    )
  } catch (e) {
    recordCheck('Parse SD-JWT VC structure', false, errorMessage(e))
    return { valid: false, checks }
  }

  if (!decoded.kbJwt) {
    recordCheck('KB-JWT present', false, 'SD-JWT VC presentation requires a key-binding JWT')
    return { valid: false, checks }
  }

  const issuerDid = String(decoded.issuerPayload.iss ?? '')
  if (!issuerDid.startsWith('did:key:')) {
    recordCheck('Resolve issuer DID', false, `issuer is not a did:key: ${issuerDid}`)
    return { valid: false, checks }
  }
  try {
    verifyJws(decoded.issuerJwt, didKeyToPublicKey(issuerDid))
    recordCheck('Verify issuer JWT signature', true, `issuer: ${issuerDid}`)
  } catch (e) {
    recordCheck('Verify issuer JWT signature', false, errorMessage(e))
    return { valid: false, checks }
  }

  const issuerSdHashes = (decoded.issuerPayload._sd ?? []) as string[]
  const disclosedClaims: Record<string, unknown> = {}
  for (const d of decoded.disclosures) {
    if (!issuerSdHashes.includes(d.hash)) {
      recordCheck(`Disclosure '${d.name}' hash in _sd`, false, `expected hash ${d.hash} not found`)
    } else {
      disclosedClaims[d.name] = d.value
    }
  }
  if (decoded.disclosures.every((d) => issuerSdHashes.includes(d.hash))) {
    recordCheck(
      'All disclosure hashes bound to issuer _sd',
      true,
      `${decoded.disclosures.length} disclosure(s) verified`,
    )
  }

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

  const cnf = decoded.issuerPayload.cnf as { kid?: string } | undefined
  const holderDid = String(cnf?.kid ?? '')
  if (!holderDid.startsWith('did:key:')) {
    recordCheck('Resolve holder DID (cnf.kid)', false, `cnf.kid is not a did:key: ${holderDid}`)
    return { valid: false, checks }
  }
  let kbPayload: Record<string, unknown>
  try {
    kbPayload = verifyJws(decoded.kbJwt, didKeyToPublicKey(holderDid))
    recordCheck('Verify KB-JWT signature (holder)', true, `holder: ${holderDid}`)
  } catch (e) {
    recordCheck('Verify KB-JWT signature (holder)', false, errorMessage(e))
    return { valid: false, checks }
  }

  const verifier = getVerifier()
  recordCheck(
    'Audience binding (KB-JWT aud)',
    kbPayload.aud === verifier.did,
    kbPayload.aud === verifier.did ? undefined : `aud=${String(kbPayload.aud)} does not match verifier ${verifier.did}`,
  )
  recordCheck(
    'Nonce binding (replay protection)',
    !expectedNonce || kbPayload.nonce === expectedNonce,
    expectedNonce && kbPayload.nonce !== expectedNonce ? 'KB-JWT nonce does not match expected challenge' : undefined,
  )

  const prefix = [decoded.issuerJwt, ...decoded.disclosures.map((d) => d.raw), ''].join('~')
  const expectedSdHash = b64uEncodeBytes(sha256(new TextEncoder().encode(prefix)))
  recordCheck(
    'sd_hash binding (disclosure-set integrity)',
    kbPayload.sd_hash === expectedSdHash,
    kbPayload.sd_hash === expectedSdHash ? undefined : 'KB-JWT sd_hash does not match the presented disclosure set',
  )

  const totalDisclosable = issuerSdHashes.length
  const presented = decoded.disclosures.length
  const withheldCount = totalDisclosable - presented
  const withheldClaimNames = withheldCount > 0
    ? [`${withheldCount} additional claim(s) withheld`]
    : undefined

  return {
    valid: checks.every((c) => c.passed),
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

function verifyVpJwt(
  vp: string,
  expectedNonce: string | undefined,
  checks: VerificationCheck[],
  recordCheck: (s: string, p: boolean, d?: string) => void,
): VerificationResponse {
  let vpPayload: Record<string, unknown>
  try {
    const parts = vp.split('.')
    if (parts.length !== 3) throw new Error('VP JWS must have three parts')
    const unverifiedPayload = JSON.parse(b64uDecodeString(parts[1])) as Record<string, unknown>
    const holderDid = String(unverifiedPayload.iss ?? '')
    if (!holderDid.startsWith('did:key:')) throw new Error(`VP issuer is not a did:key: ${holderDid}`)
    vpPayload = verifyJws(vp, didKeyToPublicKey(holderDid))
    recordCheck('Verify VP signature (holder)', true, `holder: ${holderDid}`)
  } catch (e) {
    recordCheck('Verify VP signature (holder)', false, errorMessage(e))
    return { valid: false, checks }
  }
  const holderDid = String(vpPayload.iss ?? '')

  const verifier = getVerifier()
  recordCheck(
    'Audience binding',
    vpPayload.aud === verifier.did,
    vpPayload.aud === verifier.did ? undefined : `aud=${String(vpPayload.aud)} != verifier ${verifier.did}`,
  )
  recordCheck(
    'Nonce binding (replay protection)',
    !expectedNonce || vpPayload.nonce === expectedNonce,
    expectedNonce && vpPayload.nonce !== expectedNonce ? 'VP nonce does not match expected challenge' : undefined,
  )

  const now = Math.floor(Date.now() / 1000)
  const vpExp = Number(vpPayload.exp ?? 0)
  recordCheck(
    'VP temporal validity',
    !(vpExp && now > vpExp),
    vpExp && now > vpExp ? `VP expired at ${new Date(vpExp * 1000).toISOString()}` : undefined,
  )

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
      const vcPayload = verifyJws(vcJwt, didKeyToPublicKey(issuerDid))
      const vc = vcPayload.vc as Record<string, unknown> | undefined
      const types = ((vc?.type ?? []) as string[]).map(String)
      const subject = String(vcPayload.sub ?? '')
      recordCheck(
        `VC #${i + 1} holder binding`,
        subject === holderDid,
        subject === holderDid ? undefined : `VC subject ${subject} != VP holder ${holderDid}`,
      )
      const exp = Number(vcPayload.exp ?? 0)
      const nbf = Number(vcPayload.nbf ?? vcPayload.iat ?? 0)
      const tempOk = !((exp && now > exp) || (nbf && now < nbf))
      recordCheck(
        `VC #${i + 1} temporal validity`,
        tempOk,
        !tempOk ? (exp && now > exp ? 'expired' : 'not yet valid') : undefined,
      )
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

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e)
}

function b64uEncodeBytes(bytes: Uint8Array): string {
  let binary = ''
  for (const b of bytes) binary += String.fromCharCode(b)
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}
