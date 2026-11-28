import { useCallback, useRef, useState } from 'react'
import {
  AccessibilityInfo,
  Alert,
  KeyboardAvoidingView,
  Linking,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { useFocusEffect, useRouter } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import { CameraView, useCameraPermissions } from 'expo-camera'
import { Ionicons } from '@expo/vector-icons'
import { NfcVerifierScanButton } from '@/components/NfcVerifierScanButton'
import { haptics } from '@/lib/haptics'
import { monoFont, useWalletTheme } from '@/lib/credentialDisplay'
import { classifyIncomingPayload } from '@/lib/qrRouter'
import { setPendingOfferRaw, setPendingPresentationQr } from '@/lib/pendingScanStore'

/**
 * 1-Tap Live Unified Scan / Tap screen.
 *
 * Opening the Scan tab immediately activates the live QR viewfinder (released
 * automatically on blur), with Flashlight toggle, NFC Tap, and Manual Link/Code
 * docked cleanly in the thumb zone. Zero extra interstitial taps!
 */
export default function ScanScreen() {
  const t = useWalletTheme()
  const router = useRouter()
  const [permission, requestPermission] = useCameraPermissions()
  const [isFocused, setIsFocused] = useState(false)
  const [torch, setTorch] = useState(false)
  const [manualOpen, setManualOpen] = useState(false)
  const [paste, setPaste] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const [scanSuccessFlash, setScanSuccessFlash] = useState(false)
  const scanLockRef = useRef(false)

  useFocusEffect(
    useCallback(() => {
      setIsFocused(true)
      scanLockRef.current = false
      setScanSuccessFlash(false)
      setNotice(null)
      return () => {
        setIsFocused(false)
        setTorch(false)
      }
    }, []),
  )

  const handlePayload = useCallback(
    (raw: string): boolean => {
      const routed = classifyIncomingPayload(raw)
      if (routed.kind === 'offer') {
        haptics.success()
        setScanSuccessFlash(true)
        AccessibilityInfo.announceForAccessibility('Credential offer detected. Opening review screen.')
        setNotice(null)
        setPendingOfferRaw(routed.raw)
        router.push('/receive')
        return true
      }
      if (routed.kind === 'presentation') {
        haptics.success()
        setScanSuccessFlash(true)
        AccessibilityInfo.announceForAccessibility('Verification request detected. Opening review and share screen.')
        setNotice(null)
        setPendingPresentationQr(routed.request)
        router.push('/present')
        return true
      }
      if (routed.kind === 'public-verify') {
        haptics.selection()
        Alert.alert(
          'Web verification link',
          'This link lets someone view verification details in a web browser. Open it now?',
          [
            {
              text: 'Cancel',
              style: 'cancel',
              onPress: () => {
                scanLockRef.current = false
              },
            },
            {
              text: 'Open in browser',
              onPress: () => {
                scanLockRef.current = false
                void Linking.openURL(routed.url).catch(() => {})
              },
            },
          ],
        )
        return true
      }
      const msg =
        routed.kind === 'holder-did'
          ? 'This is a Wallet ID QR. Scan a credential offer or a requester’s verification QR instead.'
          : routed.reason
      haptics.warning()
      setNotice(msg)
      AccessibilityInfo.announceForAccessibility(msg)
      return false
    },
    [router],
  )

  const onBarcodeScanned = ({ data }: { data: string }) => {
    if (scanLockRef.current || manualOpen) return
    scanLockRef.current = true
    const accepted = handlePayload(data)
    if (!accepted) {
      // Brief cooldown so an invalid QR doesn't vibrate continuously every frame.
      setTimeout(() => {
        scanLockRef.current = false
      }, 1600)
    }
  }

  return (
    <SafeAreaView style={s.root} edges={['top']}>
      {isFocused ? <StatusBar style="light" /> : null}
      <View style={s.topBar}>
        <Text style={s.topTitle}>Scan or tap</Text>
        <View style={s.topActions}>
          {permission?.granted ? (
            <Pressable
              onPress={() => {
                haptics.selection()
                setTorch((v) => !v)
              }}
              hitSlop={12}
              style={s.iconBtn}
              accessibilityRole="button"
              accessibilityLabel={torch ? 'Turn off flashlight' : 'Turn on flashlight'}
              accessibilityState={{ selected: torch }}
            >
              <Ionicons
                name={torch ? 'flash' : 'flash-outline'}
                size={22}
                color={torch ? '#FBBF24' : '#FFFFFF'}
              />
            </Pressable>
          ) : null}
          <Pressable
            onPress={() => router.push('/settings')}
            hitSlop={12}
            style={s.iconBtn}
            accessibilityRole="button"
            accessibilityLabel="Open settings and security"
          >
            <Ionicons name="settings-outline" size={22} color="#FFFFFF" />
          </Pressable>
        </View>
      </View>

      <View style={s.stage}>
        {!permission?.granted ? (
          <View style={[s.permCard, { backgroundColor: t.surface, borderColor: t.border }]}>
            <View style={[s.permIcon, { backgroundColor: t.infoBg }]}>
              <Ionicons name="camera-outline" size={38} color={t.primary} />
            </View>
            <Text style={[s.permTitle, { color: t.text }]}>Allow camera to scan</Text>
            <Text style={[s.permBody, { color: t.textMuted }]}>
              Point your camera at any issuer offer or verification QR code — the wallet routes it automatically.
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
          <View style={s.cameraWrap}>
            {isFocused && !manualOpen ? (
              <CameraView
                style={StyleSheet.absoluteFill}
                facing="back"
                enableTorch={torch}
                barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
                onBarcodeScanned={onBarcodeScanned}
              />
            ) : null}
            <View style={s.overlay} pointerEvents="none">
              <View style={s.reticle}>
                <View style={[s.corner, s.tl, scanSuccessFlash && s.cornerSuccess]} />
                <View style={[s.corner, s.tr, scanSuccessFlash && s.cornerSuccess]} />
                <View style={[s.corner, s.bl, scanSuccessFlash && s.cornerSuccess]} />
                <View style={[s.corner, s.br, scanSuccessFlash && s.cornerSuccess]} />
              </View>
              <Text style={s.hintPill}>
                {scanSuccessFlash ? 'Code recognized!' : 'Point at an issuer offer or verification QR'}
              </Text>
            </View>
          </View>
        )}
      </View>

      <View style={s.bottomDock}>
        {notice ? (
          <View style={s.errorBanner} accessibilityLiveRegion="polite">
            <Ionicons name="alert-circle-outline" size={18} color="#FCA5A5" />
            <Text style={s.errorText}>{notice}</Text>
          </View>
        ) : null}

        <NfcVerifierScanButton
          label="Tap an NFC reader"
          onRawPayload={(raw) => {
            handlePayload(raw)
          }}
          onError={(msg) => {
            haptics.warning()
            setNotice(msg)
          }}
        />

        <TouchableOpacity
          style={s.manualBtn}
          onPress={() => {
            setNotice(null)
            setManualOpen(true)
          }}
          accessibilityRole="button"
        >
          <Ionicons name="keypad-outline" size={18} color="#E2E8F0" />
          <Text style={s.manualBtnText}>Enter link or code manually</Text>
        </TouchableOpacity>
      </View>

      <Modal visible={manualOpen} animationType="slide" presentationStyle="pageSheet" onRequestClose={() => setManualOpen(false)}>
        <SafeAreaView style={[s.modalSafe, { backgroundColor: t.bg }]} edges={['top', 'bottom']}>
          <KeyboardAvoidingView
            style={s.modalBody}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
          >
            <View style={s.modalHead}>
              <Text style={[s.modalTitle, { color: t.text }]}>Enter link or code</Text>
              <Pressable onPress={() => setManualOpen(false)} hitSlop={12} accessibilityRole="button">
                <Text style={[s.closeText, { color: t.primary }]}>Close</Text>
              </Pressable>
            </View>
            <Text style={[s.modalSub, { color: t.textMuted }]}>
              Paste a credential offer link, OpenID4VCI URI, or verification request.
            </Text>
            <TextInput
              style={[s.input, { backgroundColor: t.surface, borderColor: t.border, color: t.text }]}
              value={paste}
              onChangeText={(txt) => {
                setPaste(txt)
                if (notice) setNotice(null)
              }}
              placeholder="https://… or openid-credential-offer://…"
              placeholderTextColor={t.textMuted}
              multiline
              autoCapitalize="none"
              autoCorrect={false}
            />
            {notice ? (
              <Text style={[s.modalError, { color: t.danger }]}>{notice}</Text>
            ) : null}
            <TouchableOpacity
              style={[s.primaryBtn, { backgroundColor: t.primary }, !paste.trim() && s.disabled]}
              disabled={!paste.trim()}
              onPress={() => {
                const ok = handlePayload(paste)
                if (ok) {
                  setPaste('')
                  setManualOpen(false)
                }
              }}
              accessibilityRole="button"
            >
              <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>Continue</Text>
            </TouchableOpacity>
          </KeyboardAvoidingView>
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
  )
}

const CORNER = 28
const STROKE = 4

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0B1120' },
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 18,
    paddingVertical: 10,
    minHeight: 52,
  },
  topTitle: { color: '#FFFFFF', fontSize: 20, fontWeight: '800' },
  topActions: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  iconBtn: { width: 44, height: 44, alignItems: 'center', justifyContent: 'center', borderRadius: 22 },
  stage: { flex: 1, paddingHorizontal: 12, justifyContent: 'center' },
  cameraWrap: { flex: 1, borderRadius: 22, overflow: 'hidden' },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(11, 17, 32, 0.22)',
    padding: 20,
  },
  reticle: { width: 236, height: 236, position: 'relative' },
  corner: { position: 'absolute', width: CORNER, height: CORNER, borderColor: '#FFFFFF' },
  cornerSuccess: { borderColor: '#34D399' },
  tl: { top: 0, left: 0, borderTopWidth: STROKE, borderLeftWidth: STROKE, borderTopLeftRadius: 14 },
  tr: { top: 0, right: 0, borderTopWidth: STROKE, borderRightWidth: STROKE, borderTopRightRadius: 14 },
  bl: { bottom: 0, left: 0, borderBottomWidth: STROKE, borderLeftWidth: STROKE, borderBottomLeftRadius: 14 },
  br: { bottom: 0, right: 0, borderBottomWidth: STROKE, borderRightWidth: STROKE, borderBottomRightRadius: 14 },
  hintPill: {
    marginTop: 22,
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '600',
    backgroundColor: 'rgba(11, 17, 32, 0.72)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 999,
    overflow: 'hidden',
  },
  permCard: {
    marginHorizontal: 12,
    borderRadius: 18,
    padding: 24,
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
  },
  permIcon: { width: 72, height: 72, borderRadius: 36, alignItems: 'center', justifyContent: 'center' },
  permTitle: { fontSize: 19, fontWeight: '700' },
  permBody: { fontSize: 14, textAlign: 'center', lineHeight: 20 },
  bottomDock: { padding: 16, gap: 10 },
  manualBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    minHeight: 48,
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.12)',
  },
  manualBtnText: { color: '#F8FAFC', fontSize: 15, fontWeight: '600' },
  errorBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    padding: 12,
    borderRadius: 12,
    backgroundColor: '#450A0A',
    borderWidth: 1,
    borderColor: '#F87171',
  },
  errorText: { flex: 1, color: '#FEE2E2', fontSize: 13, lineHeight: 18 },
  modalSafe: { flex: 1 },
  modalBody: { flex: 1, padding: 20, gap: 12 },
  modalHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  modalTitle: { fontSize: 20, fontWeight: '700' },
  closeText: { fontSize: 16, fontWeight: '600' },
  modalSub: { fontSize: 14, lineHeight: 20 },
  input: {
    borderWidth: 1,
    borderRadius: 12,
    padding: 12,
    minHeight: 104,
    fontFamily: monoFont,
    fontSize: 12,
    textAlignVertical: 'top',
  },
  modalError: { fontSize: 13, lineHeight: 18 },
  primaryBtn: { minHeight: 50, borderRadius: 999, alignItems: 'center', justifyContent: 'center' },
  primaryBtnText: { fontSize: 15, fontWeight: '700' },
  disabled: { opacity: 0.5 },
})
