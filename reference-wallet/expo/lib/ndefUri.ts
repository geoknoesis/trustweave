/**
 * Pure helpers to decode NFC Forum NDEF record payloads into strings.
 * No native imports — unit-testable in node. Used by lib/nfcVerifierTag.ts.
 */

/** NFC Forum URI Record Type Definition abbreviation table (codes 0x00-0x23). */
const URI_PREFIXES: string[] = [
  '', // 0x00 — no prefix, full URI follows
  'http://www.',
  'https://www.',
  'http://',
  'https://',
  'tel:',
  'mailto:',
  'ftp://anonymous:anonymous@',
  'ftp://ftp.',
  'ftps://',
  'sftp://',
  'smb://',
  'nfs://',
  'ftp://',
  'dav://',
  'news:',
  'telnet://',
  'imap:',
  'rtsp://',
  'urn:',
  'pop:',
  'sip:',
  'sips:',
  'tftp:',
  'btspp://',
  'btl2cap://',
  'btgoep://',
  'tcpobex://',
  'irdaobex://',
  'file://',
  'urn:epc:id:',
  'urn:epc:tag:',
  'urn:epc:pat:',
  'urn:epc:raw:',
  'urn:epc:',
  'urn:nfc:',
]

/** Encode an ASCII string to a byte array (test helper + internal use). */
export function bytesOf(s: string): number[] {
  return Array.from(s, (c) => c.charCodeAt(0))
}

function bytesToUtf8(bytes: number[]): string {
  // Demo URLs are ASCII; this is sufficient and dependency-free.
  return String.fromCharCode(...bytes)
}

/** Decode an NDEF URI record payload (`number[]`) into a full URI string, or null. */
export function decodeNdefUriPayload(payload: number[]): string | null {
  if (!payload || payload.length === 0) return null
  const code = payload[0]
  const prefix = URI_PREFIXES[code]
  if (prefix === undefined) return null
  return prefix + bytesToUtf8(payload.slice(1))
}

/** Decode an NDEF Text record payload (`number[]`) into its text, or null. */
export function decodeNdefTextPayload(payload: number[]): string | null {
  if (!payload || payload.length === 0) return null
  const status = payload[0]
  const langLen = status & 0x3f // low 6 bits = language-code length
  if (payload.length < 1 + langLen) return null
  return bytesToUtf8(payload.slice(1 + langLen))
}
