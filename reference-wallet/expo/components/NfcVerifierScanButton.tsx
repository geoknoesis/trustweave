import { useEffect, useState } from 'react'
import { ActivityIndicator, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { Ionicons } from '@expo/vector-icons'
import { haptics } from '@/lib/haptics'
import {
  cancelNfcSession,
  getNfcStatus,
  openNfcSettings,
  readVerifierTagRaw,
  type NfcStatus,
} from '@/lib/nfcVerifierTag'
import {
  parsePresentationRequestQr,
  type PresentationRequestQrPayload,
} from '@/lib/presentationRequestQr'
import { useWalletTheme } from '@/lib/credentialDisplay'
import { NfcListeningModal } from './NfcListeningModal'

interface Props {
  onScan?: (request: PresentationRequestQrPayload) => void
  onRawPayload?: (raw: string) => void
  onError: (message: string) => void
  disabled?: boolean
  label?: string
}

export function NfcVerifierScanButton({
  onScan,
  onRawPayload,
  onError,
  disabled,
  label = 'Tap an NFC reader',
}: Props) {
  const t = useWalletTheme()
  const [nfcStatus, setNfcStatus] = useState<NfcStatus>('unsupported')
  const [reading, setReading] = useState(false)

  useEffect(() => {
    let alive = true
    getNfcStatus().then((status) => {
      if (alive) setNfcStatus(status)
    })
    return () => {
      alive = false
    }
  }, [])

  if (nfcStatus === 'unsupported') return null

  if (nfcStatus === 'disabled') {
    return (
      <View style={[s.disabledCard, { backgroundColor: t.surfaceElevated, borderColor: t.border }]}>
        <Ionicons name="radio-outline" size={18} color={t.textMuted} />
        <Text style={[s.disabledText, { color: t.textMuted }]}>NFC is turned off on this phone.</Text>
        <TouchableOpacity
          onPress={async () => {
            await openNfcSettings()
            const next = await getNfcStatus()
            setNfcStatus(next)
          }}
          accessibilityRole="button"
          accessibilityLabel="Turn on NFC in device settings"
        >
          <Text style={[s.settingsLink, { color: t.primary }]}>Turn on NFC</Text>
        </TouchableOpacity>
      </View>
    )
  }

  const handleCancel = async () => {
    await cancelNfcSession()
    setReading(false)
  }

  const onPress = async () => {
    setReading(true)
    try {
      const raw = await readVerifierTagRaw()
      haptics.success()
      if (onRawPayload) {
        onRawPayload(raw)
        return
      }
      const request = parsePresentationRequestQr(raw)
      if (!request) {
        onError('This NFC tag does not contain a supported verification link.')
        return
      }
      onScan?.(request)
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e)
      // Ignore user-initiated cancellation silently.
      if (!/cancel/i.test(msg)) {
        onError(msg || 'Could not read the NFC tag. Hold your phone steady and try again.')
      }
    } finally {
      setReading(false)
    }
  }

  return (
    <>
      <TouchableOpacity
        style={[
          s.btn,
          { borderColor: t.primary, backgroundColor: t.surface },
          (reading || disabled) && s.disabled,
        ]}
        onPress={onPress}
        disabled={reading || disabled}
        accessibilityRole="button"
        accessibilityLabel={label}
      >
        {reading ? (
          <ActivityIndicator color={t.primary} />
        ) : (
          <>
            <Ionicons name="radio-outline" size={18} color={t.primary} />
            <Text style={[s.btnText, { color: t.primary }]}>{label}</Text>
          </>
        )}
      </TouchableOpacity>
      <NfcListeningModal visible={reading} onCancel={handleCancel} />
    </>
  )
}

const s = StyleSheet.create({
  btn: {
    flexDirection: 'row',
    gap: 8,
    borderWidth: 1.5,
    paddingVertical: 13,
    paddingHorizontal: 18,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
    width: '100%',
  },
  disabled: { opacity: 0.6 },
  btnText: { fontWeight: '700', fontSize: 15 },
  disabledCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 12,
    borderWidth: 1,
    width: '100%',
  },
  disabledText: { flex: 1, fontSize: 13 },
  settingsLink: { fontSize: 13, fontWeight: '700' },
})
