import { describe, expect, it } from 'vitest'
import { generateEd25519KeyPair, publicKeyToDidKey, signJws } from '../lib/crypto'
import { verifyImportedCredential } from '../lib/credential-verification'
import { createObjectDisclosure } from '../lib/sdjwt'

const issuer = generateEd25519KeyPair()
const did = publicKeyToDidKey(issuer.publicKey)
const holder = publicKeyToDidKey(generateEd25519KeyPair().publicKey)
const now = Math.floor(Date.now() / 1000)
const base = { iss: did, sub: holder, iat: now, exp: now + 3600 }
const vc = { ...base, vc: { type: ['VerifiableCredential', 'Employee'], credentialSubject: { id: holder } } }
const sd = { ...base, vct: 'Employee', cnf: { kid: holder }, _sd: [] }
const jwt = (payload: Record<string, unknown>, kid = did) => signJws(payload, issuer.privateKey, kid)

describe('explicit issuer credential profiles', () => {
  it('accepts both bounded profiles with DID or canonical verification-method kid', () => {
    verifyImportedCredential(jwt(vc), 'vc+jwt')
    verifyImportedCredential(jwt(sd, `${did}#${did.slice(8)}`) + '~', 'vc+sd-jwt')
  })
  it('rejects format confusion even with a valid issuer signature', () => {
    expect(() => verifyImportedCredential(jwt(vc) + '~', 'vc+jwt')).toThrow()
    expect(() => verifyImportedCredential(jwt(sd), 'vc+jwt')).toThrow()
    expect(() => verifyImportedCredential(jwt(vc) + '~', 'vc+sd-jwt')).toThrow()
    expect(() => verifyImportedCredential(jwt(sd), 'vc+sd-jwt')).toThrow()
  })
  it('rejects an arbitrary DID fragment instead of treating it as the canonical key', () => {
    expect(() => verifyImportedCredential(jwt(vc, `${did}#unrelated`), 'vc+jwt')).toThrow('signing key')
  })
  it.each([
    { ...vc, vc: { type: ['Employee'], issuer: holder } },
    { ...vc, vc: { type: ['Employee'], credentialSubject: { id: did } } },
    { ...vc, vc: { type: [42] } },
    { ...vc, sub: undefined },
    { ...vc, iat: now + 7200 },
    { ...vc, nbf: now, exp: now - 1 },
  ])('rejects malformed or conflicting signed VC claims %#', payload => {
    expect(() => verifyImportedCredential(jwt(payload), 'vc+jwt')).toThrow()
  })
  it.each([
    { ...sd, cnf: { kid: did } },
    { ...sd, cnf: { kid: holder, jwk: {} } },
    { ...sd, vct: '' },
    { ...sd, list: [{ '...': 'digest' }] },
  ])('rejects unsupported signed SD-JWT profiles %#', payload => {
    expect(() => verifyImportedCredential(jwt(payload) + '~', 'vc+sd-jwt')).toThrow()
  })
  it('accepts signed top-level disclosure and rejects nested array placeholders', () => {
    const claim = createObjectDisclosure('name', 'Alice')
    verifyImportedCredential(`${jwt({ ...sd, _sd: [claim.hash] })}~${claim.disclosure}~`, 'vc+sd-jwt')
    const nested = createObjectDisclosure('groups', [{ '...': 'digest' }])
    expect(() => verifyImportedCredential(`${jwt({ ...sd, _sd: [nested.hash] })}~${nested.disclosure}~`, 'vc+sd-jwt')).toThrow('Array selective')
  })
  it('refuses selectively hidden validity and protocol claims', () => {
    for (const name of ['exp', '_sd_alg']) {
      const claim = createObjectDisclosure(name, 'hidden')
      expect(() => verifyImportedCredential(`${jwt({ ...sd, exp: undefined, _sd: [claim.hash] })}~${claim.disclosure}~`, 'vc+sd-jwt')).toThrow('uniquely bound')
    }
  })
})
