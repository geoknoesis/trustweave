import { decodeNdefUriPayload, decodeNdefTextPayload, bytesOf } from '@/lib/ndefUri'

describe('decodeNdefUriPayload', () => {
  it('expands the http:// prefix code 0x03', () => {
    const payload = [0x03, ...bytesOf('host:3000/verifier')]
    expect(decodeNdefUriPayload(payload)).toBe('http://host:3000/verifier')
  })

  it('expands the https:// prefix code 0x04', () => {
    const payload = [0x04, ...bytesOf('example.org/verifier')]
    expect(decodeNdefUriPayload(payload)).toBe('https://example.org/verifier')
  })

  it('handles the no-prefix code 0x00 (full URI in body)', () => {
    const payload = [0x00, ...bytesOf('http://1.2.3.4:3000/verifier')]
    expect(decodeNdefUriPayload(payload)).toBe('http://1.2.3.4:3000/verifier')
  })

  it('returns null on empty payload', () => {
    expect(decodeNdefUriPayload([])).toBeNull()
  })

  it('returns null on an unknown prefix code', () => {
    expect(decodeNdefUriPayload([0x7f, ...bytesOf('x')])).toBeNull()
  })
})

describe('decodeNdefTextPayload', () => {
  it('strips the status byte + language code and returns the text', () => {
    const payload = [0x02, ...bytesOf('en'), ...bytesOf('http://host:3000/verifier')]
    expect(decodeNdefTextPayload(payload)).toBe('http://host:3000/verifier')
  })

  it('returns null on empty payload', () => {
    expect(decodeNdefTextPayload([])).toBeNull()
  })

  it('returns null when the language length exceeds the payload', () => {
    // status byte claims a 5-byte language code, but only 2 bytes follow
    expect(decodeNdefTextPayload([0x05, ...bytesOf('ab')])).toBeNull()
  })
})
