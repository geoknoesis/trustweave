import { useEffect, useState } from 'react'
import { ActivityIndicator, Modal, StyleSheet, Switch, Text, TouchableOpacity, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { Ionicons } from '@expo/vector-icons'
import { getBiometricAvailability } from '@/lib/biometrics'
import { useWalletTheme } from '@/lib/credentialDisplay'

interface OnboardingOverlayProps {
  visible: boolean
  onComplete: (biometricLockEnabled: boolean) => void
}

/**
 * Calm, zero-jargon 2-step first-run onboarding.
 * Resolves exact lock capability cleanly without flickering between labels.
 */
export function OnboardingOverlay({ visible, onComplete }: OnboardingOverlayProps) {
  const t = useWalletTheme()
  const [step, setStep] = useState<0 | 1>(0)
  const [biometricEnabled, setBiometricEnabled] = useState(true)
  const [biometricLabel, setBiometricLabel] = useState<string | null>(null)

  useEffect(() => {
    if (!visible) return
    let alive = true
    getBiometricAvailability().then((info) => {
      if (alive) setBiometricLabel(info.label)
    })
    return () => {
      alive = false
    }
  }, [visible])

  return (
    <Modal visible={visible} animationType="fade" presentationStyle="fullScreen">
      <SafeAreaView style={[s.safe, { backgroundColor: t.bg }]} edges={['top', 'bottom']}>
        <View style={s.content}>
          <View style={s.dots}>
            <View style={[s.dot, { backgroundColor: step === 0 ? t.primary : t.border }]} />
            <View style={[s.dot, { backgroundColor: step === 1 ? t.primary : t.border }]} />
          </View>

          {step === 0 ? (
            <View style={s.hero}>
              <View style={[s.iconCircle, { backgroundColor: t.infoBg }]}>
                <Ionicons name="wallet-outline" size={48} color={t.primary} />
              </View>
              <Text style={[s.title, { color: t.text }]}>Your private credential wallet</Text>
              <Text style={[s.body, { color: t.textMuted }]}>
                Keep verified diplomas, IDs, and passes safely on this phone — and choose exactly which details
                you share every time.
              </Text>
              <View style={[s.featureCard, { backgroundColor: t.surface, borderColor: t.border }]}>
                <FeatureRow
                  icon="qr-code-outline"
                  text="Scan any credential QR or tap an NFC reader — the wallet handles the rest."
                  t={t}
                />
                <FeatureRow
                  icon="eye-off-outline"
                  text="Share only what is needed. Everything switched off stays completely hidden."
                  t={t}
                />
                <FeatureRow
                  icon="phone-portrait-outline"
                  text="Your credentials and signing key stay private on this device."
                  t={t}
                />
              </View>
            </View>
          ) : (
            <View style={s.hero}>
              <View style={[s.iconCircle, { backgroundColor: t.successBg }]}>
                <Ionicons name="shield-checkmark-outline" size={48} color={t.success} />
              </View>
              <Text style={[s.title, { color: t.text }]}>Protect your wallet</Text>
              <Text style={[s.body, { color: t.textMuted }]}>
                Require explicit confirmation before sharing credentials or resetting your wallet.
              </Text>

              <View style={[s.toggleCard, { backgroundColor: t.surface, borderColor: t.border }]}>
                <View style={s.toggleText}>
                  <Text style={[s.toggleTitle, { color: t.text }]}>
                    {biometricLabel ?? 'Checking device protection…'}
                  </Text>
                  <Text style={[s.toggleSub, { color: t.textMuted }]}>
                    Recommended. Ask for confirmation before sharing any credential.
                  </Text>
                </View>
                {biometricLabel === null ? (
                  <ActivityIndicator color={t.primary} />
                ) : (
                  <Switch
                    value={biometricEnabled}
                    onValueChange={setBiometricEnabled}
                    trackColor={{ false: t.border, true: t.primary }}
                    accessibilityLabel={`Require ${biometricLabel}`}
                  />
                )}
              </View>

              <Text style={[s.honestNote, { color: t.textMuted }]}>
                Your signing key is stored encrypted on this device and cannot be moved to another phone.
              </Text>
            </View>
          )}
        </View>

        <View style={s.footer}>
          {step === 0 ? (
            <>
              <TouchableOpacity
                style={[s.primaryBtn, { backgroundColor: t.primary }]}
                onPress={() => setStep(1)}
                accessibilityRole="button"
              >
                <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>Continue</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={s.skipBtn}
                onPress={() => onComplete(true)}
                accessibilityRole="button"
              >
                <Text style={[s.skipText, { color: t.textMuted }]}>Skip setup</Text>
              </TouchableOpacity>
            </>
          ) : (
            <>
              <TouchableOpacity
                style={[s.primaryBtn, { backgroundColor: t.primary }]}
                onPress={() => onComplete(biometricEnabled)}
                accessibilityRole="button"
              >
                <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>Start using wallet</Text>
              </TouchableOpacity>
              <TouchableOpacity style={s.skipBtn} onPress={() => setStep(0)} accessibilityRole="button">
                <Text style={[s.skipText, { color: t.textMuted }]}>Back</Text>
              </TouchableOpacity>
            </>
          )}
        </View>
      </SafeAreaView>
    </Modal>
  )
}

function FeatureRow({
  icon,
  text,
  t,
}: {
  icon: 'qr-code-outline' | 'eye-off-outline' | 'phone-portrait-outline'
  text: string
  t: ReturnType<typeof useWalletTheme>
}) {
  return (
    <View style={s.featureRow}>
      <Ionicons name={icon} size={22} color={t.primary} />
      <Text style={[s.featureText, { color: t.text }]}>{text}</Text>
    </View>
  )
}

const s = StyleSheet.create({
  safe: { flex: 1 },
  content: { flex: 1, paddingHorizontal: 24, paddingTop: 24, justifyContent: 'center' },
  dots: { flexDirection: 'row', justifyContent: 'center', gap: 8, marginBottom: 28 },
  dot: { width: 28, height: 4, borderRadius: 2 },
  hero: { alignItems: 'center', gap: 14 },
  iconCircle: {
    width: 92,
    height: 92,
    borderRadius: 46,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 4,
  },
  title: { fontSize: 26, fontWeight: '800', textAlign: 'center' },
  body: { fontSize: 15, textAlign: 'center', lineHeight: 22, paddingHorizontal: 8 },
  featureCard: {
    width: '100%',
    borderRadius: 14,
    padding: 16,
    gap: 14,
    borderWidth: 1,
    marginTop: 12,
  },
  featureRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  featureText: { flex: 1, fontSize: 14, lineHeight: 20 },
  toggleCard: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    marginTop: 8,
  },
  toggleText: { flex: 1, gap: 4 },
  toggleTitle: { fontSize: 16, fontWeight: '700' },
  toggleSub: { fontSize: 13, lineHeight: 18 },
  honestNote: { fontSize: 12, textAlign: 'center', lineHeight: 18, marginTop: 6, paddingHorizontal: 12 },
  footer: { padding: 24, gap: 8 },
  primaryBtn: { minHeight: 52, borderRadius: 999, alignItems: 'center', justifyContent: 'center' },
  primaryBtnText: { fontSize: 16, fontWeight: '700' },
  skipBtn: { minHeight: 44, alignItems: 'center', justifyContent: 'center' },
  skipText: { fontSize: 14, fontWeight: '600' },
})
