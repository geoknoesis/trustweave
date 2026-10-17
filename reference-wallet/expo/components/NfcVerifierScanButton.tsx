import { useEffect, useState } from 'react'
import { ActivityIndicator, StyleSheet, Text, TouchableOpacity } from 'react-native'
import { isNfcAvailable, readVerifierTagRaw } from '@/lib/nfcVerifierTag'
import {
  parsePresentationRequestQr,
  type PresentationRequestQrPayload,
} from '@/lib/presentationRequestQr'
import { theme } from '@/lib/credentialDisplay'

interface Props {
  onScan: (request: PresentationRequestQrPayload) => void
  onError: (message: string) => void
  disabled?: boolean
}

export function NfcVerifierScanButton({ onScan, onError, disabled }: Props) {
  const [available, setAvailable] = useState(false)
  const [reading, setReading] = useState(false)

  useEffect(() => {
    let alive = true
    isNfcAvailable().then((ok) => {
      if (alive) setAvailable(ok)
    })
    return () => {
      alive = false
    }
  }, [])

  if (!available) return null

  const onPress = async () => {
    setReading(true)
    try {
      const raw = await readVerifierTagRaw()
      const request = parsePresentationRequestQr(raw)
      if (!request) {
        onError('That NFC tag is not a TrustWeave verifier link.')
        return
      }
      onScan(request)
    } catch (e) {
      onError(e instanceof Error ? e.message : 'Could not read the NFC tag.')
    } finally {
      setReading(false)
    }
  }

  return (
    <TouchableOpacity
      style={[s.btn, (reading || disabled) && s.disabled]}
      onPress={onPress}
      disabled={reading || disabled}
    >
      {reading ? (
        <ActivityIndicator color={theme.primary} />
      ) : (
        <Text style={s.btnText}>📲 Tap an NFC verifier tag</Text>
      )}
    </TouchableOpacity>
  )
}

const s = StyleSheet.create({
  btn: {
    borderWidth: 2,
    borderColor: theme.primary,
    paddingVertical: 12,
    borderRadius: 999,
    alignItems: 'center',
    marginTop: 8,
  },
  disabled: { opacity: 0.6 },
  btnText: { color: theme.primary, fontWeight: '600' },
})
