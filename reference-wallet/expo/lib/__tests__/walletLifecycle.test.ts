/**
 * End-to-end unit tests of the Expo wallet lifecycle:
 *  - Fail-closed UntrustedIssuerError rejection (persists nothing)
 *  - Explicit confirmation persists credential + accepted issuer + activity entries
 *  - Removing an accepted issuer blocks presenting its credential
 *  - Credential-only backup NEVER exports private keys and enforces matching holder on restore
 */
const mockSecureStoreMap = new Map<string, string>()
const mockAsyncStoreMap = new Map<string, string>()

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(async (k: string) => mockSecureStoreMap.get(k) ?? null),
  setItemAsync: jest.fn(async (k: string, v: string) => {
    mockSecureStoreMap.set(k, v)
  }),
  deleteItemAsync: jest.fn(async (k: string) => {
    mockSecureStoreMap.delete(k)
  }),
}))

jest.mock('@react-native-async-storage/async-storage', () => ({
  __esModule: true,
  default: {
    getItem: jest.fn(async (k: string) => mockAsyncStoreMap.get(k) ?? null),
    setItem: jest.fn(async (k: string, v: string) => {
      mockAsyncStoreMap.set(k, v)
    }),
    removeItem: jest.fn(async (k: string) => {
      mockAsyncStoreMap.delete(k)
    }),
  },
}))

import { generateEd25519KeyPair, publicKeyToDidKey, signJws } from '../crypto'
import { createObjectDisclosure } from '../sdjwt'
import {
  acceptedIssuers,
  bootstrap,
  createPresentation,
  exportCredentialBackup,
  getActivityLog,
  list,
  removeAcceptedIssuer,
  resetWallet,
  restoreCredentialBackup,
  store,
  UntrustedIssuerError,
} from '../wallet'

function mintTestSdJwt(args: {
  issuerPriv: Uint8Array
  issuerDid: string
  holderDid: string
  claims: Record<string, unknown>
}) {
  const disclosures = Object.entries(args.claims).map(([k, v]) => ({
    name: k,
    ...createObjectDisclosure(k, v),
  }))
  const now = Math.floor(Date.now() / 1000)
  const payload = {
    iss: args.issuerDid,
    sub: args.holderDid,
    iat: now - 5,
    exp: now + 365 * 24 * 3600,
    vct: 'BachelorOfScience',
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
  return {
    compact: [jwt, ...disclosures.map((d) => d.disclosure), ''].join('~'),
    names: disclosures.map((d) => d.name),
  }
}

describe('Expo wallet end-to-end security & UX lifecycle', () => {
  beforeEach(async () => {
    mockSecureStoreMap.clear()
    mockAsyncStoreMap.clear()
    await resetWallet()
  })

  it('enforces fail-closed trust, persists issuer on confirmation, blocks presentation after removal, and keeps backups key-free', async () => {
    const state = await bootstrap()
    expect(state.holder.did.startsWith('did:key:z')).toBe(true)

    const issuerKp = generateEd25519KeyPair()
    const issuerDid = publicKeyToDidKey(issuerKp.publicKey)
    const { compact, names } = mintTestSdJwt({
      issuerPriv: issuerKp.privateKey,
      issuerDid,
      holderDid: state.holder.did,
      claims: { fullName: 'Alice Johnson', degree: 'B.S. Computer Science' },
    })

    // 1. Unknown issuer fails closed and persists nothing.
    await expect(store(compact, 'vc+sd-jwt', names, { configuredIssuers: [] })).rejects.toThrow(
      UntrustedIssuerError,
    )
    expect(await list()).toHaveLength(0)
    expect(await acceptedIssuers()).toHaveLength(0)

    // 2. Explicit user confirmation stores credential, persists accepted issuer, and logs activity.
    const stored = await store(compact, 'vc+sd-jwt', names, {
      configuredIssuers: [],
      confirmedIssuer: issuerDid,
    })
    expect(stored.credential.trustSource).toBe('confirmed')
    expect(await acceptedIssuers()).toEqual([issuerDid])
    const logAfterAdd = await getActivityLog()
    expect(logAfterAdd.map((e) => e.kind)).toEqual(expect.arrayContaining(['added', 'trusted_issuer']))

    // 3. Selective disclosure presentation succeeds with chosen claim only.
    const vp = await createPresentation([stored.credential.id], 'https://verifier.example', 'nonce-1', ['degree'])
    expect(vp.split('~').length).toBe(3) // issuerJwt ~ 1 selected disclosure ~ kbJwt

    // 4. Backup JSON explicitly omits private keys.
    const backupJson = await exportCredentialBackup()
    expect(backupJson).not.toContain(state.holder.privateKey)
    const parsedBackup = JSON.parse(backupJson) as { containsPrivateKeys: boolean; credentials: unknown[] }
    expect(parsedBackup.containsPrivateKeys).toBe(false)
    expect(parsedBackup.credentials).toHaveLength(1)

    // 5. Removing accepted issuer blocks future presentations of that issuer's credentials.
    await removeAcceptedIssuer(issuerDid)
    await expect(
      createPresentation([stored.credential.id], 'https://verifier.example', 'nonce-2', ['degree']),
    ).rejects.toThrow(/removed from your trusted issuers/i)

    // 6. Restoring backup fails closed on untrusted issuer until confirmed, and rejects foreign holder backups.
    const restoreUntrusted = await restoreCredentialBackup(backupJson, { configuredIssuers: [] })
    expect(restoreUntrusted.restored).toBe(0)
    expect(restoreUntrusted.untrustedIssuers).toEqual([issuerDid])

    const restoreConfirmed = await restoreCredentialBackup(backupJson, {
      configuredIssuers: [],
      confirmedIssuer: issuerDid,
    })
    expect(restoreConfirmed.restored).toBe(1)

    const foreignBackup = JSON.stringify({
      ...JSON.parse(backupJson),
      holderDid: 'did:key:z6MkDifferentHolder',
    })
    await expect(restoreCredentialBackup(foreignBackup)).rejects.toThrow(/different wallet key/i)
  })
})
