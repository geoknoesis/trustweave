import { beforeEach, describe, expect, it, vi } from 'vitest'
import { extractClaimsForDisplay } from '../lib/credential-claims'
import { decryptClaimJwe } from '../lib/claim-jwe'
import { b64uEncodeString } from '../lib/crypto'
import { loadHolder, type StoredCredential } from '../lib/storage'

vi.mock('../lib/claim-jwe', async original => ({
  ...await original<typeof import('../lib/claim-jwe')>(),
  decryptClaimJwe: vi.fn(),
}))
vi.mock('../lib/storage', async original => ({
  ...await original<typeof import('../lib/storage')>(),
  loadHolder: vi.fn(),
}))

const encrypted = {
  alg: 'X25519-HKDF-SHA256-A256GCM', epk: '', iv: '', ciphertext: '', tag: '',
  encrypted_key: '', key_iv: '', key_tag: '',
}

function credential(claims: Record<string, unknown>): StoredCredential {
  return {
    id: 'stored', format: 'vc+jwt',
    credential: `e30.${b64uEncodeString(JSON.stringify({ vc: { credentialSubject: claims } }))}.signature`,
    receivedAt: '2026-10-16T00:00:00Z', issuerDid: 'did:key:issuer', subjectDid: 'did:key:holder',
    type: ['VerifiableCredential'], preview: { title: 'Credential' }, selectivelyDisclosable: [],
  }
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(loadHolder).mockReturnValue(null)
})

describe('credential claim display', () => {
  it('uses the public holder DID to decrypt through device-bound custody', async () => {
    const holder = { did: 'did:key:holder', publicKey: 'public', createdAt: '2026-10-16T00:00:00Z' }
    vi.mocked(loadHolder).mockReturnValue(holder)
    vi.mocked(decryptClaimJwe).mockResolvedValue('Private address')

    const rows = await extractClaimsForDisplay(credential({ address: encrypted }))

    expect(decryptClaimJwe).toHaveBeenCalledWith(encrypted, holder.did)
    expect(rows.find(row => row.key === 'address')?.value).toBe('Private address')
  })

  it('renders plain claims without accessing holder metadata and hides portraits', async () => {
    vi.mocked(loadHolder).mockImplementation(() => { throw new Error('Metadata unavailable') })
    const rows = await extractClaimsForDisplay(credential({ name: 'Alice', photo: 'data:image/png;base64,AA==' }))
    expect(rows.map(row => row.value)).toEqual(['Alice'])
    expect(loadHolder).not.toHaveBeenCalled()
  })

  it('omits encrypted claims when holder metadata is absent', async () => {
    expect(await extractClaimsForDisplay(credential({ address: encrypted }))).toEqual([])
    expect(decryptClaimJwe).not.toHaveBeenCalled()
  })

  it('contains metadata and decryption failures within the detail display', async () => {
    vi.mocked(loadHolder).mockImplementation(() => { throw new Error('Invalid metadata') })
    const rows = await extractClaimsForDisplay(credential({ address: encrypted }))
    expect(rows.find(row => row.key === 'address')?.value).toContain('could not decrypt')
    expect(await extractClaimsForDisplay({ ...credential({}), credential: 'not-a-credential' })).toEqual([])
  })
})
