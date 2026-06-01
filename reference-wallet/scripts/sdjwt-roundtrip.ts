/**
 * Smoke test: issue SD-JWT VC → present with KB-JWT → verify holder signature.
 * Run: npx tsx scripts/sdjwt-roundtrip.ts
 */
import {
  didKeyToPublicKey,
  generateEd25519KeyPair,
  publicKeyToDidKey,
  verifyJws,
} from '../lib/crypto'
import { issueSdJwtVc, presentSdJwtVc } from '../lib/sdjwt'

const issuerKp = generateEd25519KeyPair()
const holderKp = generateEd25519KeyPair()
const verifierKp = generateEd25519KeyPair()

const issuerDid = publicKeyToDidKey(issuerKp.publicKey)
const holderDid = publicKeyToDidKey(holderKp.publicKey)
const verifierDid = publicKeyToDidKey(verifierKp.publicKey)

const now = Math.floor(Date.now() / 1000)
const sdJwtVc = issueSdJwtVc({
  issuerDid,
  issuerPrivateKey: issuerKp.privateKey,
  issuerKid: `${issuerDid}#key`,
  holderDid,
  alwaysVisible: { studentId: 'STU-001' },
  selectivelyDisclosable: [{ name: 'name', value: 'Ada Lovelace' }],
  vct: 'UniversityDegreeCredential',
  now,
})

const nonce = crypto.randomUUID()
const presentation = presentSdJwtVc({
  sdJwtVc,
  selectDisclose: ['name'],
  holderPrivateKey: holderKp.privateKey,
  holderDid,
  audience: verifierDid,
  nonce,
  now,
})

const kbJwt = presentation.split('~').filter(Boolean).at(-1)!
const holderPub = didKeyToPublicKey(holderDid)
const kbPayload = verifyJws(kbJwt, holderPub)

if (kbPayload.aud !== verifierDid) throw new Error('aud mismatch')
if (kbPayload.nonce !== nonce) throw new Error('nonce mismatch')

console.log('SD-JWT VC round-trip OK')
console.log('  holder:', holderDid.slice(0, 28) + '…')
console.log('  kb-jwt verified')

// Wrong-key presentation must fail (simulates wallet reset without re-receive).
const wrongHolderKp = generateEd25519KeyPair()
const badPresentation = presentSdJwtVc({
  sdJwtVc,
  selectDisclose: ['name'],
  holderPrivateKey: wrongHolderKp.privateKey,
  holderDid: publicKeyToDidKey(wrongHolderKp.publicKey),
  audience: verifierDid,
  nonce,
  now,
})
const badKbJwt = badPresentation.split('~').filter(Boolean).at(-1)!
try {
  verifyJws(badKbJwt, holderPub)
  throw new Error('expected wrong-key KB-JWT to fail')
} catch (e) {
  const msg = e instanceof Error ? e.message : String(e)
  if (!msg.includes('verification failed')) throw e
  console.log('  wrong-key KB-JWT correctly rejected')
}
