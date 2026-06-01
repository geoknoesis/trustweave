import { useRef, useState } from 'react'
import { ActivityIndicator, Linking, Modal, Pressable, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native'
import { CameraView, useCameraPermissions } from 'expo-camera'
import {
  parsePresentationRequestQr,
  type PresentationRequestQrPayload,
} from '@/lib/presentationRequestQr'
import { explainPublicVerificationUrlMisscan, parsePublicVerificationUrl } from '@/lib/publicVerificationUrl'
import { explainVerifierScanFailure } from '@/lib/qrScanFeedback'
import { theme } from '@/lib/credentialDisplay'

interface VerifierQrScannerProps {
  visible: boolean
  onClose: () => void
  onScan: (request: PresentationRequestQrPayload) => void
}

export function VerifierQrScanner({ visible, onClose, onScan }: VerifierQrScannerProps) {
  const [permission, requestPermission] = useCameraPermissions()
  const [paste, setPaste] = useState('')
  const [error, setError] = useState<string | null>(null)
  const handledRef = useRef(false)

  const handleRaw = (raw: string) => {
    if (handledRef.current) return
    const publicVerifyUrl = parsePublicVerificationUrl(raw)
    if (publicVerifyUrl) {
      handledRef.current = true
      setError(explainPublicVerificationUrlMisscan())
      void Linking.openURL(publicVerifyUrl).catch(() => {
        setError(
          `${explainPublicVerificationUrlMisscan()} Could not open the link — copy it from the credential page and paste in your browser.`,
        )
      })
      return
    }
    const request = parsePresentationRequestQr(raw)
    if (!request) {
      setError(explainVerifierScanFailure(raw))
      return
    }
    handledRef.current = true
    setError(null)
    onScan(request)
    onClose()
  }

  const resetAndClose = () => {
    handledRef.current = false
    setError(null)
    onClose()
  }

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={resetAndClose}>
      <View style={s.container}>
        <Text style={s.title}>Scan verifier QR</Text>
        <Text style={s.muted}>Point at the QR shown by the organisation verifying your credential.</Text>

        {!permission?.granted ? (
          <TouchableOpacity style={s.btn} onPress={requestPermission}>
            <Text style={s.btnText}>Allow camera</Text>
          </TouchableOpacity>
        ) : (
          <View style={s.cameraWrap}>
            <CameraView
              style={s.camera}
              facing="back"
              barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
              onBarcodeScanned={({ data }) => handleRaw(data)}
            />
          </View>
        )}

        <Text style={s.label}>OR PASTE VERIFIER JSON / URL</Text>
        <TextInput
          style={s.input}
          value={paste}
          onChangeText={setPaste}
          placeholder='https://…/verifier or JSON payload'
          placeholderTextColor="#94a3b8"
          multiline
        />
        <TouchableOpacity
          style={[s.btn, !paste.trim() && s.btnDisabled]}
          disabled={!paste.trim()}
          onPress={() => handleRaw(paste)}
        >
          <Text style={s.btnText}>Use pasted request</Text>
        </TouchableOpacity>

        {error && <Text style={s.error}>{error}</Text>}

        <Pressable onPress={resetAndClose} style={s.close}>
          <Text style={s.closeText}>Cancel</Text>
        </Pressable>
      </View>
    </Modal>
  )
}

export function VerifierQrScannerButton({
  onPress,
  loading,
}: {
  onPress: () => void
  loading?: boolean
}) {
  return (
    <TouchableOpacity style={[s.btnOutline, loading && s.btnDisabled]} onPress={onPress} disabled={loading}>
      {loading ? (
        <ActivityIndicator color={theme.primary} />
      ) : (
        <Text style={s.btnOutlineText}>Scan verifier QR</Text>
      )}
    </TouchableOpacity>
  )
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: theme.bg, padding: 16, gap: 12 },
  title: { color: theme.primary, fontSize: 20, fontWeight: '600', marginTop: 24 },
  muted: { color: theme.textMuted, fontSize: 13 },
  cameraWrap: { height: 280, borderRadius: 12, overflow: 'hidden', borderWidth: 1, borderColor: theme.border },
  camera: { flex: 1 },
  label: { color: theme.textMuted, fontSize: 11, letterSpacing: 0.5, marginTop: 8 },
  input: {
    borderWidth: 1,
    borderColor: theme.border,
    borderRadius: 8,
    padding: 10,
    minHeight: 72,
    fontFamily: 'Menlo',
    fontSize: 11,
    backgroundColor: theme.surface,
    color: theme.text,
  },
  btn: { backgroundColor: theme.primary, paddingVertical: 12, borderRadius: 999, alignItems: 'center' },
  btnDisabled: { opacity: 0.6 },
  btnText: { color: '#ffffff', fontWeight: '600' },
  btnOutline: { borderWidth: 2, borderColor: theme.primary, paddingVertical: 12, borderRadius: 999, alignItems: 'center' },
  btnOutlineText: { color: theme.primary, fontWeight: '600' },
  error: { color: '#dc2626', fontSize: 13 },
  close: { alignItems: 'center', paddingVertical: 16 },
  closeText: { color: theme.textMuted, fontSize: 15 },
})
