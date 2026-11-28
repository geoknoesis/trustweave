import { Modal, Platform, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { Ionicons } from '@expo/vector-icons'
import { useWalletTheme } from '@/lib/credentialDisplay'

interface NfcListeningModalProps {
  visible: boolean
  onCancel: () => void
}

/**
 * Android-focused listening sheet while a foreground NFC session is open.
 * (On iOS, CoreNFC shows its native system sheet with our custom alertMessage.)
 */
export function NfcListeningModal({ visible, onCancel }: NfcListeningModalProps) {
  const t = useWalletTheme()

  // iOS shows its native CoreNFC modal sheet automatically.
  if (Platform.OS === 'ios') return null

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <View style={s.backdrop}>
        <SafeAreaView style={[s.sheet, { backgroundColor: t.surface, borderColor: t.border }]} edges={['bottom']}>
          <View style={[s.handle, { backgroundColor: t.border }]} />
          <View style={[s.ringOuter, { backgroundColor: t.infoBg }]}>
            <View style={[s.ringInner, { backgroundColor: t.primary }]}>
              <Ionicons name="wifi" size={30} color={t.onPrimary} style={s.nfcGlyph} />
            </View>
          </View>
          <Text style={[s.title, { color: t.text }]}>Ready to scan NFC</Text>
          <Text style={[s.body, { color: t.textMuted }]}>
            Hold the back of your phone near the NFC reader or tag until you feel a vibration.
          </Text>
          <TouchableOpacity
            style={[s.cancelBtn, { borderColor: t.border }]}
            onPress={onCancel}
            accessibilityRole="button"
            accessibilityLabel="Cancel NFC scan"
          >
            <Text style={[s.cancelText, { color: t.text }]}>Cancel</Text>
          </TouchableOpacity>
        </SafeAreaView>
      </View>
    </Modal>
  )
}

const s = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.48)',
    justifyContent: 'flex-end',
  },
  sheet: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 24,
    alignItems: 'center',
    gap: 12,
    borderTopWidth: 1,
  },
  handle: {
    width: 36,
    height: 4,
    borderRadius: 2,
    marginBottom: 8,
  },
  ringOuter: {
    width: 88,
    height: 88,
    borderRadius: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ringInner: {
    width: 60,
    height: 60,
    borderRadius: 30,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nfcGlyph: {
    transform: [{ rotate: '90deg' }],
  },
  title: { fontSize: 19, fontWeight: '700' },
  body: { fontSize: 14, textAlign: 'center', lineHeight: 20, paddingHorizontal: 12 },
  cancelBtn: {
    marginTop: 8,
    minHeight: 48,
    width: '100%',
    borderRadius: 999,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelText: { fontSize: 15, fontWeight: '600' },
})
