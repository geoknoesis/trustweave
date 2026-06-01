/**
 * Server-side keys for the demo issuer and demo verifier.
 *
 * Runtime: Node.js (Next.js API routes). Browser MUST NOT import this module.
 *
 * Keys are persisted to `.demo-server-keys.json` so dev-server restarts and
 * hot reloads do not rotate DIDs mid-demo. Without this, a presentation built
 * with one verifier `aud` fails verification after the server reloads.
 */
import 'server-only'
import fs from 'fs'
import path from 'path'
import * as ed25519 from '@noble/ed25519'
import { sha512 } from '@noble/hashes/sha512'
import {
  b64uDecode,
  b64uEncode,
  generateEd25519KeyPair,
  publicKeyToDidKey,
  type Ed25519KeyPair,
} from './crypto'

ed25519.etc.sha512Sync = (...m) => sha512(ed25519.etc.concatBytes(...m))

export interface ServerIdentity {
  did: string
  keyPair: Ed25519KeyPair
}

interface PersistedIdentity {
  did: string
  privateKey: string
}

interface PersistedKeys {
  issuer: PersistedIdentity
  verifier: PersistedIdentity
  faa?: PersistedIdentity
  cac?: PersistedIdentity
}

const KEYS_FILE = path.join(process.cwd(), '.demo-server-keys.json')

let _issuer: ServerIdentity | undefined
let _verifier: ServerIdentity | undefined
let _faa: ServerIdentity | undefined
let _cac: ServerIdentity | undefined

function createPersistedIdentity(keyPair: Ed25519KeyPair): PersistedIdentity {
  return {
    did: publicKeyToDidKey(keyPair.publicKey),
    privateKey: b64uEncode(keyPair.privateKey),
  }
}

function loadPersistedKeys(): PersistedKeys {
  if (fs.existsSync(KEYS_FILE)) {
    const data = JSON.parse(fs.readFileSync(KEYS_FILE, 'utf8')) as PersistedKeys
    if (!data.faa) {
      data.faa = createPersistedIdentity(generateEd25519KeyPair())
      fs.writeFileSync(KEYS_FILE, JSON.stringify(data, null, 2), 'utf8')
      console.log(`[demo-faa] added FAA issuer DID: ${data.faa.did}`)
    }
    if (!data.cac) {
      data.cac = createPersistedIdentity(generateEd25519KeyPair())
      fs.writeFileSync(KEYS_FILE, JSON.stringify(data, null, 2), 'utf8')
      console.log(`[demo-cac] added CAC issuer DID: ${data.cac.did}`)
    }
    return data
  }

  const data: PersistedKeys = {
    issuer: createPersistedIdentity(generateEd25519KeyPair()),
    verifier: createPersistedIdentity(generateEd25519KeyPair()),
    faa: createPersistedIdentity(generateEd25519KeyPair()),
    cac: createPersistedIdentity(generateEd25519KeyPair()),
  }
  fs.writeFileSync(KEYS_FILE, JSON.stringify(data, null, 2), 'utf8')
  console.log(`[demo-keys] created ${KEYS_FILE}`)
  console.log(`[demo-issuer] DID: ${data.issuer.did}`)
  console.log(`[demo-verifier] DID: ${data.verifier.did}`)
  console.log(`[demo-faa] DID: ${data.faa!.did}`)
  console.log(`[demo-cac] DID: ${data.cac!.did}`)
  return data
}

function identityFromPersisted(entry: PersistedIdentity): ServerIdentity {
  const privateKey = b64uDecode(entry.privateKey)
  const publicKey = ed25519.getPublicKey(privateKey)
  const did = publicKeyToDidKey(publicKey)
  if (did !== entry.did) {
    throw new Error(`Persisted ${KEYS_FILE} entry DID mismatch — delete the file to regenerate`)
  }
  return { did: entry.did, keyPair: { privateKey, publicKey } }
}

function ensureLoaded(): void {
  if (_issuer && _verifier && _faa && _cac) return
  const persisted = loadPersistedKeys()
  _issuer = identityFromPersisted(persisted.issuer)
  _verifier = identityFromPersisted(persisted.verifier)
  _faa = identityFromPersisted(persisted.faa!)
  _cac = identityFromPersisted(persisted.cac!)
  if (fs.existsSync(KEYS_FILE)) {
    console.log(`[demo-issuer] DID: ${_issuer.did}`)
    console.log(`[demo-verifier] DID: ${_verifier.did}`)
    console.log(`[demo-faa] DID: ${_faa.did}`)
    console.log(`[demo-cac] DID: ${_cac.did}`)
  }
}

export function getIssuer(): ServerIdentity {
  ensureLoaded()
  return _issuer!
}

export function getVerifier(): ServerIdentity {
  ensureLoaded()
  return _verifier!
}

/** FAA registry authority — separate DID from the spatial domain / university issuer. */
export function getFaaIssuer(): ServerIdentity {
  ensureLoaded()
  return _faa!
}

/** CAC issuer — separate DID for Common Access Card demo credentials. */
export function getCacIssuer(): ServerIdentity {
  ensureLoaded()
  return _cac!
}
