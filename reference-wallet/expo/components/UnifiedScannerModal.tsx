import { useEffect, useRef, useState } from 'react'
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  Vibration,
  View,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { CameraView, useCameraPermissions } from 'expo-camera'
import { Ionicons } from '@expo/vector-icons'
import { monoFont, useWalletTheme } from '@/lib/credentialDisplay'

export interface QrScannerSheetProps {
  visible: boolean
  title: string
  subtitle: string
  manualPlaceholder: string
  manualSubmitLabel: string
  /** Return an error string to keep scanning and show calm inline feedback, or `null` when accepted. */
  onValidateAndAccept: (raw: string) => string | null
  onClose: () => void
}

/**
 * Shared full-bleed QR camera modal with:
 * - Guaranteed `handledRef` reset on every open (fixes the stale second-scan bug)
 * - SafeAreaView support for notch / Dynamic Island / gesture bar
 * - Centered viewfinder reticle + flashlight torch toggle
 * - Secondary manual link/code entry sheet with KeyboardAvoidingView
 */
export function QrScannerSheet({
  visible,
  title,
  subtitle,
  manualPlaceholder,
  manualSubmitLabel,
  onValidateAndAccept,
  onClose,
}: QrScannerSheetProps) {
  const t = useWalletTheme()
  const [permission, requestPermission] = useCameraPermissions()
  const [torch, setTorch] = useState(false)
  const [manualOpen, setManualOpen] = useState(false)
  const [paste, setPaste] = useState('')
  const [error, setError] = useState<string | null>(null)
  const handledRef = useRef(false)

  // Always reset lock & transient UI state whenever the modal opens.
  useEffect(() => {
    if (visible) {
      handledRef.current = false
      setError(null)
      setTorch(false)
      setManualOpen(false)
      setPaste('')
    }
  }, [visible])

  const handleRaw = (raw: string) => {
    if (handledRef.current) return
    const rejection = onValidateAndAccept(raw)
    if (rejection) {
      setError(rejection)
      return
    }
    handledRef.current = true
    Vibration.vibrate(20)
    setError(null)
    onClose()
  }

  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <SafeAreaView style={[s.root, { backgroundColor: '#0B1120' }]} edges={['top', 'bottom']}>
        <View style={s.topBar}>
          <Pressable
            onPress={onClose}
            hitSlop={12}
            style={s.iconBtn}
            accessibilityRole="button"
            accessibilityLabel="Close scanner"
          >
            <Ionicons name="close" size={24} color="#FFFFFF" />
          </Pressable>
          <Text style={s.topTitle}>{title}</Text>
          {permission?.granted && !manualOpen ? (
            <Pressable
              onPress={() => setTorch((v) => !v)}
              hitSlop={12}
              style={s.iconBtn}
              accessibilityRole="button"
              accessibilityLabel={torch ? 'Turn off flashlight' : 'Turn on flashlight'}
              accessibilityState={{ selected: torch }}
            >
              <Ionicons name={torch ? 'flash' : 'flash-outline'} size={22} color={torch ? '#FBBF24' : '#FFFFFF'} />
            </Pressable>
          ) : (
            <View style={s.iconBtn} />
          )}
        </View>

        {manualOpen ? (
          <KeyboardAvoidingView
            style={[s.manualWrap, { backgroundColor: t.bg }]}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
          >
            <Text style={[s.manualTitle, { color: t.text }]}>Enter link or code manually</Text>
            <Text style={[s.manualSub, { color: t.textMuted }]}>{subtitle}</Text>
            <TextInput
              style={[
                s.input,
                {
                  backgroundColor: t.surface,
                  borderColor: t.border,
                  color: t.text,
                },
              ]}
              value={paste}
              onChangeText={(text) => {
                setPaste(text)
                if (error) setError(null)
              }}
              placeholder={manualPlaceholder}
              placeholderTextColor={t.textMuted}
              multiline
              autoCapitalize="none"
              autoCorrect={false}
            />
            {error ? (
              <View style={[s.errorBanner, { backgroundColor: t.dangerBg, borderColor: t.danger }]}>
                <Ionicons name="alert-circle-outline" size={18} color={t.danger} />
                <Text style={[s.errorText, { color: t.danger }]}>{error}</Text>
              </View>
            ) : null}
            <TouchableOpacity
              style={[s.primaryBtn, { backgroundColor: t.primary }, !paste.trim() && s.disabled]}
              disabled={!paste.trim()}
              onPress={() => handleRaw(paste)}
              accessibilityRole="button"
            >
              <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>{manualSubmitLabel}</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={s.switchModeBtn}
              onPress={() => {
                setError(null)
                setManualOpen(false)
              }}
              accessibilityRole="button"
            >
              <Ionicons name="camera-outline" size={18} color={t.primary} />
              <Text style={[s.switchModeText, { color: t.primary }]}>Back to camera</Text>
            </TouchableOpacity>
          </KeyboardAvoidingView>
        ) : (
          <View style={s.cameraStage}>
            {!permission?.granted ? (
              <View style={[s.permCard, { backgroundColor: t.surface, borderColor: t.border }]}>
                <Ionicons name="camera-outline" size={44} color={t.primary} />
                <Text style={[s.permTitle, { color: t.text }]}>Camera access needed</Text>
                <Text style={[s.permBody, { color: t.textMuted }]}>
                  TrustWeave uses your camera only to scan credential and verification QR codes.
                </Text>
                <TouchableOpacity
                  style={[s.primaryBtn, { backgroundColor: t.primary, width: '100%' }]}
                  onPress={requestPermission}
                  accessibilityRole="button"
                >
                  <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>Allow camera</Text>
                </TouchableOpacity>
              </View>
            ) : (
              <View style={s.cameraContainer}>
                <CameraView
                  style={StyleSheet.absoluteFill}
                  facing="back"
                  enableTorch={torch}
                  barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
                  onBarcodeScanned={({ data }) => handleRaw(data)}
                />
                <View style={s.overlay} pointerEvents="none">
                  <View style={s.reticle}>
                    <View style={[s.corner, s.tl]} />
                    <View style={[s.corner, s.tr]} />
                    <View style={[s.corner, s.bl]} />
                    <View style={[s.corner, s.br]} />
                  </View>
                  <Text style={s.overlayHint}>{subtitle}</Text>
                </View>
              </View>
            )}

            <View style={s.bottomPanel}>
              {error ? (
                <View style={[s.errorBanner, { backgroundColor: '#450A0A', borderColor: '#F87171' }]}>
                  <Ionicons name="alert-circle-outline" size={18} color="#FCA5A5" />
                  <Text style={[s.errorText, { color: '#FEE2E2' }]}>{error}</Text>
                </View>
              ) : null}

              <TouchableOpacity
                style={s.manualLinkBtn}
                onPress={() => {
                  setError(null)
                  setManualOpen(true)
                }}
                accessibilityRole="button"
              >
                <Ionicons name="keypad-outline" size={18} color="#E2E8F0" />
                <Text style={s.manualLinkText}>Enter link or code manually</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
      </SafeAreaView>
    </Modal>
  )
}

const CORNER_SIZE = 28
const CORNER_WIDTH = 4

const s = StyleSheet.create({
  root: { flex: 1 },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 10,
    minHeight: 52,
  },
  iconBtn: {
    width: 44,
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 22,
  },
  topTitle: { color: '#FFFFFF', fontSize: 17, fontWeight: '700' },
  cameraStage: { flex: 1, justifyContent: 'space-between' },
  cameraContainer: { flex: 1, overflow: 'hidden', borderRadius: 20, marginHorizontal: 12 },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(11, 17, 32, 0.25)',
    padding: 24,
  },
  reticle: {
    width: 240,
    height: 240,
    position: 'relative',
  },
  corner: {
    position: 'absolute',
    width: CORNER_SIZE,
    height: CORNER_SIZE,
    borderColor: '#FFFFFF',
  },
  tl: { top: 0, left: 0, borderTopWidth: CORNER_WIDTH, borderLeftWidth: CORNER_WIDTH, borderTopLeftRadius: 12 },
  tr: { top: 0, right: 0, borderTopWidth: CORNER_WIDTH, borderRightWidth: CORNER_WIDTH, borderTopRightRadius: 12 },
  bl: { bottom: 0, left: 0, borderBottomWidth: CORNER_WIDTH, borderLeftWidth: CORNER_WIDTH, borderBottomLeftRadius: 12 },
  br: { bottom: 0, right: 0, borderBottomWidth: CORNER_WIDTH, borderRightWidth: CORNER_WIDTH, borderBottomRightRadius: 12 },
  overlayHint: {
    marginTop: 24,
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
    backgroundColor: 'rgba(11, 17, 32, 0.72)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 999,
    overflow: 'hidden',
  },
  permCard: {
    margin: 24,
    padding: 24,
    borderRadius: 16,
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
  },
  permTitle: { fontSize: 18, fontWeight: '700' },
  permBody: { fontSize: 14, textAlign: 'center', lineHeight: 20 },
  bottomPanel: { padding: 16, gap: 10 },
  manualLinkBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    minHeight: 48,
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  manualLinkText: { color: '#F8FAFC', fontSize: 15, fontWeight: '600' },
  manualWrap: { flex: 1, padding: 20, gap: 12 },
  manualTitle: { fontSize: 20, fontWeight: '700' },
  manualSub: { fontSize: 14, lineHeight: 20 },
  input: {
    borderWidth: 1,
    borderRadius: 12,
    padding: 12,
    minHeight: 96,
    fontFamily: monoFont,
    fontSize: 12,
    textAlignVertical: 'top',
  },
  primaryBtn: {
    minHeight: 48,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 20,
  },
  primaryBtnText: { fontSize: 15, fontWeight: '700' },
  disabled: { opacity: 0.5 },
  switchModeBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    minHeight: 48,
  },
  switchModeText: { fontSize: 15, fontWeight: '600' },
  errorBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
  },
  errorText: { flex: 1, fontSize: 13, lineHeight: 18 },
})
