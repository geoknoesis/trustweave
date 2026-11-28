import { ActivityIndicator, StyleSheet, Text, TouchableOpacity } from 'react-native'
import { Ionicons } from '@expo/vector-icons'
import { parseIncomingCredentialQr } from '@/lib/credentialOfferIngress'
import { explainOfferScanFailure } from '@/lib/qrScanFeedback'
import { useWalletTheme } from '@/lib/credentialDisplay'
import { QrScannerSheet } from './UnifiedScannerModal'

interface OfferQrScannerProps {
  visible: boolean
  onClose: () => void
  onScan: (raw: string) => void
}

export function OfferQrScanner({ visible, onClose, onScan }: OfferQrScannerProps) {
  return (
    <QrScannerSheet
      visible={visible}
      title="Scan credential QR"
      subtitle="Point at the QR code from your issuer."
      manualPlaceholder="Paste credential offer link or JSON"
      manualSubmitLabel="Add from link"
      onClose={onClose}
      onValidateAndAccept={(raw) => {
        const offer = parseIncomingCredentialQr(raw)
        if (!offer) return explainOfferScanFailure(raw)
        onScan(raw)
        return null
      }}
    />
  )
}

export function OfferQrScannerButton({
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
      accessibilityLabel="Scan credential QR code"
    >
      {loading ? (
        <ActivityIndicator color={t.onPrimary} />
      ) : (
        <>
          <Ionicons name="qr-code-outline" size={18} color={t.onPrimary} />
          <Text style={[s.btnText, { color: t.onPrimary }]}>Scan QR code</Text>
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
