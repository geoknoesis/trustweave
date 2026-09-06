import { describe, expect, it } from 'vitest'
import { b64uEncodeString, generateEd25519KeyPair, publicKeyToDidKey, signJws } from '../lib/crypto'
import { verifyImportedCredential } from '../lib/credential-verification'
import { createObjectDisclosure, parseDisclosure, issueSdJwtVc } from '../lib/sdjwt'
import { isCredentialBoundToHolder } from '../lib/holder-binding'

const issuer = generateEd25519KeyPair()
const did = publicKeyToDidKey(issuer.publicKey)
const compact = (payload: Record<string, unknown>, disclosure: string) =>
  `${signJws({ iss: did, sub: 'holder', cnf: { kid: 'holder' }, vct: 'Employee', ...payload }, issuer.privateKey, did)}~${disclosure}~`

describe('supported disclosure profile', () => {
  it.each([
    { alwaysVisible: { sub: 'attacker' }, selectivelyDisclosable: [] },
    { alwaysVisible: { name: 'Alice' }, selectivelyDisclosable: [{ name: 'name', value: 'Alice' }] },
    { alwaysVisible: {}, selectivelyDisclosable: [{ name: 'name', value: 'Alice' }, { name: 'name', value: 'Bob' }] },
  ])('rejects issuer configuration that overrides bindings or duplicates claims', claims => {
    expect(() => issueSdJwtVc({ issuerDid: did, issuerPrivateKey: issuer.privateKey, issuerKid: did,
      holderDid: 'holder', vct: 'Employee', now: Math.floor(Date.now() / 1000), ...claims })).toThrow()
  })
  it.each([null, {}, ['salt', 5, 'value'], [1, 'name', 'value'], ['salt', '__proto__', {}],
    ['salt', '_sd', []], ['salt', '...', 'value']])('rejects malformed disclosure %j', value => {
    expect(() => parseDisclosure(b64uEncodeString(JSON.stringify(value)))).toThrow()
  })
  it('accepts a unique top-level disclosure', () => {
    const claim = createObjectDisclosure('name', 'Alice')
    expect(() => verifyImportedCredential(compact({ _sd: [claim.hash] }, claim.disclosure), 'vc+sd-jwt')).not.toThrow()
  })
  it('rejects a disclosure that shadows an always-visible claim', () => {
    const claim = createObjectDisclosure('sub', 'different-holder')
    expect(() => verifyImportedCredential(compact({ sub: 'holder', _sd: [claim.hash] }, claim.disclosure), 'vc+sd-jwt')).toThrow('uniquely bound')
  })
  it('rejects nested placement instead of flattening the claim into a different object', () => {
    const claim = createObjectDisclosure('name', 'Alice')
    expect(() => verifyImportedCredential(compact({ address: { _sd: [claim.hash] } }, claim.disclosure), 'vc+sd-jwt')).toThrow('Nested')
  })
  it('rejects duplicate commitments', () => {
    const claim = createObjectDisclosure('name', 'Alice')
    expect(() => verifyImportedCredential(compact({ _sd: [claim.hash, claim.hash] }, claim.disclosure), 'vc+sd-jwt')).toThrow('Duplicate')
  })
  it('derives holder binding from signed content despite altered local metadata', () => {
    const credential = signJws({ iss: did, sub: 'other-holder' }, issuer.privateKey, did)
    expect(isCredentialBoundToHolder({ format: 'vc+jwt', credential, subjectDid: 'my-holder' }, 'my-holder')).toBe(false)
  })
  it('rejects contradictory VC and JWT holder identifiers', () => {
    const credential = signJws({ iss: did, sub: 'holder', vc: { credentialSubject: { id: 'other' } } }, issuer.privateKey, did)
    expect(isCredentialBoundToHolder({ format: 'vc+jwt', credential, subjectDid: 'holder' }, 'holder')).toBe(false)
  })
})
