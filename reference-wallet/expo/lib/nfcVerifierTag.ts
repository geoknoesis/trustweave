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
  } catch {
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
      // NDEF type "U" (0x55) = URI record; "T" (0x54) = Text record.
      const type = (rec.type ?? []) as number[]
      const typeChar = type.length === 1 ? type[0] : 0
      const decoded =
        typeChar === 0x55
          ? decodeNdefUriPayload(payload)
          : typeChar === 0x54
            ? decodeNdefTextPayload(payload)
            : (decodeNdefUriPayload(payload) ?? decodeNdefTextPayload(payload))
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
