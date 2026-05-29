/**
 * Server-side keys for the demo issuer and demo verifier.
 *
 * Runtime: Node.js (Next.js API routes). Browser MUST NOT import this module.
 *
 * NOTE for the design doc: in §8 the demo domain is meant to run on production
 * infrastructure with HSM-backed keys. This in-process variant is the developer-mode
 * fallback so you can `npm run dev` with no external dependencies. A real demo
 * deployment swaps these in-memory keys for the production issuer wallet.
 */
import 'server-only'
import {
  generateEd25519KeyPair,
  publicKeyToDidKey,
  type Ed25519KeyPair,
} from './crypto'

export interface ServerIdentity {
  did: string
  keyPair: Ed25519KeyPair
}

// Generated once per Next.js server process. Restarting the dev server rotates these,
// which means previously-issued VCs become unverifiable — fine for a demo, would not
// be acceptable in production.
let _issuer: ServerIdentity | undefined
let _verifier: ServerIdentity | undefined

export function getIssuer(): ServerIdentity {
  if (!_issuer) {
    const keyPair = generateEd25519KeyPair()
    _issuer = { did: publicKeyToDidKey(keyPair.publicKey), keyPair }
    console.log(`[demo-issuer] DID: ${_issuer.did}`)
  }
  return _issuer
}

export function getVerifier(): ServerIdentity {
  if (!_verifier) {
    const keyPair = generateEd25519KeyPair()
    _verifier = { did: publicKeyToDidKey(keyPair.publicKey), keyPair }
    console.log(`[demo-verifier] DID: ${_verifier.did}`)
  }
  return _verifier
}
