import { Linking, Platform } from 'react-native'
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

export type NfcStatus = 'ready' | 'disabled' | 'unsupported'

/** Distinguish ready, disabled-in-OS-settings (Android), and unsupported devices. */
export async function getNfcStatus(): Promise<NfcStatus> {
  if (!(await ensureStarted())) return 'unsupported'
  try {
    if (Platform.OS === 'android') {
      const enabled = await NfcManager.isEnabled()
      return enabled ? 'ready' : 'disabled'
    }
    return 'ready'
  } catch {
    return 'unsupported'
  }
}

/** True only when NFC hardware exists and is usable on this device. */
export async function isNfcAvailable(): Promise<boolean> {
  return (await getNfcStatus()) === 'ready'
}

/** Open Android system NFC settings so users can turn NFC on in one tap. */
export async function openNfcSettings(): Promise<void> {
  if (Platform.OS === 'android') {
    try {
      await NfcManager.goToNfcSetting()
      return
    } catch {
      try {
        await Linking.sendIntent('android.settings.NFC_SETTINGS')
        return
      } catch {
        await Linking.openSettings()
      }
    }
    return
  }
  await Linking.openSettings()
}

/** Cancel any active foreground NFC session (used by the Android Cancel button). */
export async function cancelNfcSession(): Promise<void> {
  try {
    await NfcManager.cancelTechnologyRequest()
  } catch {
    /* session already closed */
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
    await NfcManager.requestTechnology(NfcTech.Ndef, {
      alertMessage: 'Hold the top of your iPhone near the NFC reader.',
    })
    const tag = await NfcManager.getTag()
    const records = tag?.ndefMessage ?? []
    for (const rec of records) {
      const payload = (rec.payload ?? []) as number[]
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
    throw new Error('This NFC tag does not contain a credential or verification link.')
  } finally {
    await cancelNfcSession()
  }
}
