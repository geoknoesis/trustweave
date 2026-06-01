import { useRef, useState } from 'react'
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native'
import { CameraView, useCameraPermissions } from 'expo-camera'
import { parseIncomingCredentialQr } from '@/lib/credentialOfferIngress'
import { explainOfferScanFailure } from '@/lib/qrScanFeedback'

interface OfferQrScannerProps {
  visible: boolean
  onClose: () => void
  onScan: (raw: string) => void
}

export function OfferQrScanner({ visible, onClose, onScan }: OfferQrScannerProps) {
  const [permission, requestPermission] = useCameraPermissions()
  const [paste, setPaste] = useState('')
  const [error, setError] = useState<string | null>(null)
  const handledRef = useRef(false)

  const handleRaw = (raw: string) => {
    if (handledRef.current) return
    const offer = parseIncomingCredentialQr(raw)
    if (!offer) {
      setError(explainOfferScanFailure(raw))
      return
    }
    handledRef.current = true
    setError(null)
    onScan(raw)
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
        <Text style={s.title}>Scan QR code</Text>
        <Text style={s.muted}>Point at the credential offer from your issuer.</Text>

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

        <Text style={s.label}>OR PASTE OFFER JSON / URL</Text>
        <TextInput
          style={s.input}
          value={paste}
          onChangeText={setPaste}
          placeholder='Offer JSON or https://…/issuer/offer?…'
          placeholderTextColor="#94a3b8"
          multiline
        />
        <TouchableOpacity
          style={[s.btn, !paste.trim() && s.btnDisabled]}
          disabled={!paste.trim()}
          onPress={() => handleRaw(paste)}
        >
          <Text style={s.btnText}>Claim pasted offer</Text>
        </TouchableOpacity>

        {error && <Text style={s.error}>{error}</Text>}

        <Pressable onPress={resetAndClose} style={s.close}>
          <Text style={s.closeText}>Cancel</Text>
        </Pressable>
      </View>
    </Modal>
  )
}

export function OfferQrScannerButton({
  onPress,
  loading,
}: {
  onPress: () => void
  loading?: boolean
}) {
  return (
    <TouchableOpacity style={[s.btnOutline, loading && s.btnDisabled]} onPress={onPress} disabled={loading}>
      {loading ? (
        <ActivityIndicator color="#1e3a8a" />
      ) : (
        <Text style={s.btnOutlineText}>Scan QR code</Text>
      )}
    </TouchableOpacity>
  )
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#f8fafc', padding: 16, gap: 12 },
  title: { color: '#1e3a8a', fontSize: 20, fontWeight: '600', marginTop: 24 },
  muted: { color: '#64748b', fontSize: 13 },
  cameraWrap: { height: 280, borderRadius: 8, overflow: 'hidden', borderWidth: 1, borderColor: '#e2e8f0' },
  camera: { flex: 1 },
  label: { color: '#64748b', fontSize: 11, letterSpacing: 0.5, marginTop: 8 },
  input: {
    borderWidth: 1,
    borderColor: '#e2e8f0',
    borderRadius: 6,
    padding: 10,
    minHeight: 72,
    fontFamily: 'Menlo',
    fontSize: 11,
    backgroundColor: '#ffffff',
    color: '#1f2937',
  },
  btn: { backgroundColor: '#1e3a8a', paddingVertical: 12, borderRadius: 6, alignItems: 'center' },
  btnDisabled: { opacity: 0.6 },
  btnText: { color: '#ffffff', fontWeight: '500' },
  btnOutline: { borderWidth: 1, borderColor: '#1e3a8a', paddingVertical: 12, borderRadius: 6, alignItems: 'center' },
  btnOutlineText: { color: '#1e3a8a', fontWeight: '600' },
  error: { color: '#dc2626', fontSize: 13 },
  close: { alignItems: 'center', paddingVertical: 16 },
  closeText: { color: '#64748b', fontSize: 15 },
})
