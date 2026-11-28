import { ActivityIndicator, Linking, StyleSheet, Text, TouchableOpacity } from 'react-native'
import { Ionicons } from '@expo/vector-icons'
import {
  parsePresentationRequestQr,
  type PresentationRequestQrPayload,
} from '@/lib/presentationRequestQr'
import { explainPublicVerificationUrlMisscan, parsePublicVerificationUrl } from '@/lib/publicVerificationUrl'
import { explainVerifierScanFailure } from '@/lib/qrScanFeedback'
import { useWalletTheme } from '@/lib/credentialDisplay'
import { QrScannerSheet } from './UnifiedScannerModal'

interface VerifierQrScannerProps {
  visible: boolean
  onClose: () => void
  onScan: (request: PresentationRequestQrPayload) => void
}

export function VerifierQrScanner({ visible, onClose, onScan }: VerifierQrScannerProps) {
  return (
    <QrScannerSheet
      visible={visible}
      title="Scan verification QR"
      subtitle="Point at the QR shown by the organization verifying you."
      manualPlaceholder="Paste verification link or request payload"
      manualSubmitLabel="Continue"
      onClose={onClose}
      onValidateAndAccept={(raw) => {
        const publicVerifyUrl = parsePublicVerificationUrl(raw)
        if (publicVerifyUrl) {
          void Linking.openURL(publicVerifyUrl).catch(() => {})
          return explainPublicVerificationUrlMisscan()
        }
        const request = parsePresentationRequestQr(raw)
        if (!request) return explainVerifierScanFailure(raw)
        onScan(request)
        return null
      }}
    />
  )
}

export function VerifierQrScannerButton({
  onPress,
  loading,
}: {
  onPress: () => void
  loading?: boolean
}) {
  const t = useWalletTheme()
  return (
    <TouchableOpacity
      style={[s.btn, { backgroundColor: t.primary }, loading && s.btnDisabled]}
      onPress={onPress}
      disabled={loading}
      accessibilityRole="button"
      accessibilityLabel="Scan verification QR code"
    >
      {loading ? (
        <ActivityIndicator color={t.onPrimary} />
      ) : (
        <>
          <Ionicons name="qr-code-outline" size={18} color={t.onPrimary} />
          <Text style={[s.btnText, { color: t.onPrimary }]}>Scan verification QR</Text>
        </>
      )}
    </TouchableOpacity>
  )
}

const s = StyleSheet.create({
  btn: {
    flexDirection: 'row',
    gap: 8,
    paddingVertical: 13,
    paddingHorizontal: 20,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
    width: '100%',
  },
  btnDisabled: { opacity: 0.6 },
  btnText: { fontWeight: '700', fontSize: 15 },
})
