import {
  b64uEncodeString,
  generateEd25519KeyPair,
  publicKeyToDidKey,
  signJws,
} from '../crypto'
import { createObjectDisclosure } from '../sdjwt'
import {
  buildIssuerTrustPolicy,
  isPlausibleIssuerDid,
  IssuerTrustPolicy,
  shortDidFingerprint,
  UntrustedIssuerError,
  withAcceptedIssuer,
} from '../issuerTrust'
import { verifyImportedCredential } from '../credentialVerification'
import { classifyIncomingPayload } from '../qrRouter'
import {
  credentialIconName,
  credentialTrustBadge,
  extractShareableClaimDetails,
  formatIssuerLabel,
} from '../credentialDisplay'
import type { StoredCredential } from '../storage'

function mintSdJwtVc(args: {
  issuerPriv: Uint8Array
  issuerDid: string
  holderDid: string
  vct?: string
  claims: Record<string, unknown>
  exp?: number
}): { compact: string; names: string[] } {
  const disclosures = Object.entries(args.claims).map(([k, v]) => ({
    name: k,
    ...createObjectDisclosure(k, v),
  }))
  const now = Math.floor(Date.now() / 1000)
  const payload: Record<string, unknown> = {
    iss: args.issuerDid,
    sub: args.holderDid,
    iat: now - 10,
    exp: args.exp ?? now + 3600,
    vct: args.vct ?? 'BachelorOfScience',
    cnf: { kid: args.holderDid },
    _sd_alg: 'sha-256',
    _sd: disclosures.map((d) => d.hash),
  }
  const jwt = signJws(
    payload,
    args.issuerPriv,
    `${args.issuerDid}#${args.issuerDid.slice('did:key:'.length)}`,
    'vc+sd-jwt',
  )
  const compact = [jwt, ...disclosures.map((d) => d.disclosure), ''].join('~')
  return { compact, names: disclosures.map((d) => d.name) }
}

describe('issuerTrust (fail-closed)', () => {
  it('fails closed by default and matches exact DIDs only', () => {
    const did = 'did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH'
    expect(IssuerTrustPolicy.NONE.isTrusted(did)).toBe(false)
    const policy = buildIssuerTrustPolicy({ configured: [did], backend: [], accepted: [] })
    expect(policy.isTrusted(did)).toBe(true)
    expect(policy.isTrusted(`${did}x`)).toBe(false)
  })

  it('validates DID shape and formats short fingerprint', () => {
    expect(isPlausibleIssuerDid('did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH')).toBe(true)
    expect(isPlausibleIssuerDid('not-a-did')).toBe(false)
    expect(() => withAcceptedIssuer([], 'http://evil.example')).toThrow()
    expect(shortDidFingerprint('did:key:z6MkpTHR8VNsBxYAAWHut2Geadd9jSwuBV8xRoAnwWsdvktH')).toBe('z6MkpT…vktH')
  })
})

describe('verifyImportedCredential', () => {
  const issuerKp = generateEd25519KeyPair()
  const issuerDid = publicKeyToDidKey(issuerKp.publicKey)
  const holderKp = generateEd25519KeyPair()
  const holderDid = publicKeyToDidKey(holderKp.publicKey)

  it('rejects untrusted issuers with UntrustedIssuerError after verifying signature', () => {
    const { compact } = mintSdJwtVc({
      issuerPriv: issuerKp.privateKey,
      issuerDid,
      holderDid,
      claims: { name: 'Alice', degree: 'B.S. Computer Science' },
    })

    expect(() =>
      verifyImportedCredential(compact, 'vc+sd-jwt', {
        holderDid,
        issuerPolicy: IssuerTrustPolicy.NONE,
      }),
    ).toThrow(UntrustedIssuerError)
  })

  it('accepts valid SD-JWT VC when issuer is trusted and holder matches', () => {
    const { compact } = mintSdJwtVc({
      issuerPriv: issuerKp.privateKey,
      issuerDid,
      holderDid,
      claims: { name: 'Alice', degree: 'B.S. Computer Science' },
    })

    expect(() =>
      verifyImportedCredential(compact, 'vc+sd-jwt', {
        holderDid,
        issuerPolicy: IssuerTrustPolicy.allowList([issuerDid]),
      }),
    ).not.toThrow()
  })

  it('rejects tampered disclosures and wrong holder binding', () => {
    const { compact } = mintSdJwtVc({
      issuerPriv: issuerKp.privateKey,
      issuerDid,
      holderDid,
      claims: { name: 'Alice' },
    })
    const fakeDisclosure = b64uEncodeString(JSON.stringify(['salt', 'role', 'admin']))
    const tampered = `${compact.slice(0, -1)}~${fakeDisclosure}~`

    expect(() =>
      verifyImportedCredential(tampered, 'vc+sd-jwt', {
        holderDid,
        issuerPolicy: IssuerTrustPolicy.allowList([issuerDid]),
      }),
    ).toThrow(/uniquely bound/i)

    const otherHolder = publicKeyToDidKey(generateEd25519KeyPair().publicKey)
    expect(() =>
      verifyImportedCredential(compact, 'vc+sd-jwt', {
        holderDid: otherHolder,
        issuerPolicy: IssuerTrustPolicy.allowList([issuerDid]),
      }),
    ).toThrow(/different wallet/i)
  })
})

describe('qrRouter (unified scan/tap auto-routing)', () => {
  it('routes OpenID4VCI offers, direct offers, verifier URLs, openid4vp, and public verify links', () => {
    const oidOffer = classifyIncomingPayload(
      'openid-credential-offer://?credential_offer=%7B%22credential_issuer%22%3A%22https%3A%2F%2Fissuer.example%22%2C%22credential_configuration_ids%22%3A%5B%22Degree%22%5D%2C%22grants%22%3A%7B%22urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Apre-authorized_code%22%3A%7B%22pre-authorized_code%22%3A%22abc123%22%7D%7D%7D',
    )
    expect(oidOffer.kind).toBe('offer')

    const verifier = classifyIncomingPayload('https://demo.example/verifier')
    expect(verifier.kind).toBe('presentation')

    const vpUri = classifyIncomingPayload(
      'openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Fapi%2Fdemo-verifier%2Frequest',
    )
    expect(vpUri.kind).toBe('presentation')

    const wrapped = classifyIncomingPayload(
      'trustweavewallet://scan?url=https%3A%2F%2Fdemo.example%2Fverifier',
    )
    expect(wrapped.kind).toBe('presentation')

    const holder = classifyIncomingPayload(
      JSON.stringify({ v: 1, type: 'trustweave-holder', did: 'did:key:z6Mkp123' }),
    )
    expect(holder.kind).toBe('holder-did')
  })
})

describe('credentialDisplay (honest trust badges & claim values)', () => {
  const issuerKp = generateEd25519KeyPair()
  const issuerDid = publicKeyToDidKey(issuerKp.publicKey)
  const holderDid = publicKeyToDidKey(generateEd25519KeyPair().publicKey)

  it('never labels arbitrary did:key as "Verified issuer"', () => {
    const label = formatIssuerLabel(issuerDid)
    expect(label).not.toMatch(/verified/i)
    expect(label).toContain('Issuer · ')
  })

  it('shows honest trust and expiry badges', () => {
    const now = Math.floor(Date.now() / 1000)
    const { compact, names } = mintSdJwtVc({
      issuerPriv: issuerKp.privateKey,
      issuerDid,
      holderDid,
      claims: { fullName: 'Alice Johnson', graduationYear: 2026 },
      exp: now + 365 * 24 * 3600,
    })
    const baseCred: StoredCredential = {
      id: '1',
      format: 'vc+sd-jwt',
      credential: compact,
      receivedAt: new Date().toISOString(),
      issuerDid,
      subjectDid: holderDid,
      type: ['BachelorOfScience'],
      preview: { title: 'Bachelor of Science', subtitle: 'Alice Johnson' },
      selectivelyDisclosable: names,
    }

    expect(credentialTrustBadge({ ...baseCred, trustSource: undefined }).label).toBe('Saved on device')
    expect(credentialTrustBadge({ ...baseCred, trustSource: 'confirmed' }).label).toBe('Confirmed by you')
    expect(credentialTrustBadge({ ...baseCred, trustSource: 'backend' }).label).toBe('Trusted issuer')

    const expiringSoon = mintSdJwtVc({
      issuerPriv: issuerKp.privateKey,
      issuerDid,
      holderDid,
      claims: { fullName: 'Alice Johnson' },
      exp: now + 3600,
    })
    expect(credentialTrustBadge({ ...baseCred, credential: expiringSoon.compact, trustSource: 'backend' }).label).toBe('Expiring soon')

    const expired = mintSdJwtVc({
      issuerPriv: issuerKp.privateKey,
      issuerDid,
      holderDid,
      claims: { fullName: 'Alice Johnson' },
      exp: now - 60,
    })
    expect(credentialTrustBadge({ ...baseCred, credential: expired.compact, trustSource: 'backend' }).label).toBe('Expired')

    const details = extractShareableClaimDetails(baseCred)
    expect(details).toEqual([
      { name: 'fullName', label: 'Full Name', value: 'Alice Johnson' },
      { name: 'graduationYear', label: 'Graduation Year', value: '2026' },
    ])

    expect(credentialIconName(['BachelorOfScience'], 'Bachelor of Science')).toBe('school-outline')
    expect(credentialIconName(['AirspaceAuthorization'], 'FAA Drone Permit')).toBe('airplane-outline')
  })
})
