import { Platform } from 'react-native'
import NfcManager, { NfcTech } from 'react-native-nfc-manager'
import { decodeNdefUriPayload, decodeNdefTextPayload } from '@/lib/ndefUri'

let started = false

async function ensureStarted(): Promise<boolean> {
  if (Platform.OS === 'web') return false
  if (started) return true
  try {
    const supported = await NfcManager.isSupported()
    if (!supported) return false
    await NfcManager.start()
    started = true
    return true
  } catch (e) {
    if (__DEV__) console.warn('[nfc] NfcManager.start() failed:', e)
    return false
  }
}

/** True only when NFC hardware exists and is usable on this device. */
export async function isNfcAvailable(): Promise<boolean> {
  if (!(await ensureStarted())) return false
  try {
    // Android exposes enabled-state; iOS reports supported only.
    return Platform.OS === 'android' ? await NfcManager.isEnabled() : true
  } catch {
    return false
  }
}

/**
 * Open a foreground NFC session, read the first NDEF URI/Text record, and
 * return its decoded string (e.g. "http://host:3000/verifier"). Throws on
 * unreadable/empty tags or if NFC is unavailable. Always releases the session.
 */
export async function readVerifierTagRaw(): Promise<string> {
  if (!(await ensureStarted())) {
    throw new Error('NFC is not available on this device.')
  }
  try {
    await NfcManager.requestTechnology(NfcTech.Ndef)
    const tag = await NfcManager.getTag()
    const records = tag?.ndefMessage ?? []
    for (const rec of records) {
      const payload = (rec.payload ?? []) as number[]
      // Recognise NDEF well-known records only: type "U" (URI) or "T" (Text).
      // react-native-nfc-manager may surface `type` as a byte array or a string.
      const rawType = rec.type as number[] | string | undefined
      const typeByte = Array.isArray(rawType) && rawType.length === 1 ? rawType[0] : 0
      const typeStr = typeof rawType === 'string' ? rawType : ''
      const decoded =
        typeByte === 0x55 || typeStr === 'U'
          ? decodeNdefUriPayload(payload)
          : typeByte === 0x54 || typeStr === 'T'
            ? decodeNdefTextPayload(payload)
            : null
      if (decoded && decoded.trim()) return decoded.trim()
    }
    throw new Error('This tag has no verifier link on it.')
  } finally {
    try {
      await NfcManager.cancelTechnologyRequest()
    } catch {
      /* session already closed */
    }
  }
}
