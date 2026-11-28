import '../polyfills'

import { useEffect, useRef, useState } from 'react'
import { Alert, AppState, Linking, StyleSheet, Text, TouchableOpacity, View, type AppStateStatus } from 'react-native'
import { Stack, useRouter } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import { SafeAreaProvider } from 'react-native-safe-area-context'
import { Ionicons } from '@expo/vector-icons'
import { AppErrorBoundary } from '@/components/AppErrorBoundary'
import { confirmSensitiveAction, getBiometricAvailability } from '@/lib/biometrics'
import { useWalletTheme } from '@/lib/credentialDisplay'
import { classifyIncomingPayload } from '@/lib/qrRouter'
import { setPendingOfferRaw, setPendingPresentationQr } from '@/lib/pendingScanStore'
import { getPreferences } from '@/lib/wallet'

function WalletShell() {
  const t = useWalletTheme()
  const router = useRouter()
  const lastHandledRef = useRef<{ url: string; at: number } | null>(null)
  const wasBackgroundedRef = useRef(false)
  const [appState, setAppState] = useState<AppStateStatus>(AppState.currentState)
  const [isLocked, setIsLocked] = useState(false)
  const [lockLabel, setLockLabel] = useState('Biometrics')

  // App-switcher privacy shield + native biometric resume lock when enrolled.
  useEffect(() => {
    const sub = AppState.addEventListener('change', async (nextState) => {
      setAppState(nextState)
      if (nextState === 'background') {
        wasBackgroundedRef.current = true
        const [prefs, bio] = await Promise.all([getPreferences(), getBiometricAvailability()])
        if (prefs.onboardingComplete && prefs.biometricLockEnabled && bio.nativeBiometricsAvailable) {
          setLockLabel(bio.label)
          setIsLocked(true)
        }
      } else if (nextState === 'active' && wasBackgroundedRef.current) {
        wasBackgroundedRef.current = false
      }
    })
    return () => sub.remove()
  }, [])

  const handleUnlock = async () => {
    const ok = await confirmSensitiveAction('Unlock your TrustWeave wallet')
    if (ok) setIsLocked(false)
  }

  // OS deep-link handler (allows re-tapping the same link after a 1.5s debounce window).
  useEffect(() => {
    const handleIncomingUrl = (url: string | null) => {
      if (!url) return
      const now = Date.now()
      if (
        lastHandledRef.current &&
        lastHandledRef.current.url === url &&
        now - lastHandledRef.current.at < 1500
      ) {
        return
      }
      if (
        !url.startsWith('openid-credential-offer://') &&
        !url.startsWith('openid4vp://') &&
        !url.includes('data=') &&
        !url.includes('offer=') &&
        !url.includes('request=')
      ) {
        return
      }
      lastHandledRef.current = { url, at: now }
      const routed = classifyIncomingPayload(url)
      if (routed.kind === 'offer') {
        setPendingOfferRaw(routed.raw)
        router.push('/receive')
      } else if (routed.kind === 'presentation') {
        setPendingPresentationQr(routed.request)
        router.push('/present')
      } else if (routed.kind === 'public-verify') {
        Alert.alert(
          'Web verification link',
          'This link is meant to be viewed in a web browser. Open it now?',
          [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Open in browser', onPress: () => void Linking.openURL(routed.url) },
          ],
        )
      }
    }

    Linking.getInitialURL().then(handleIncomingUrl).catch(() => {})
    const sub = Linking.addEventListener('url', ({ url }) => handleIncomingUrl(url))
    return () => sub.remove()
  }, [router])

  return (
    <View style={s.root}>
      <StatusBar style={t.isDark ? 'light' : 'dark'} />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: t.surface },
          headerTintColor: t.text,
          headerTitleStyle: { fontWeight: '700', fontSize: 18 },
          headerShadowVisible: false,
          headerBackTitle: 'Back',
          contentStyle: { backgroundColor: t.bg },
        }}
      >
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="receive" options={{ title: 'Add credential' }} />
        <Stack.Screen name="present" options={{ title: 'Review & share' }} />
        <Stack.Screen
          name="settings"
          options={{
            title: 'Settings & security',
            presentation: 'card',
          }}
        />
      </Stack>

      {appState !== 'active' || isLocked ? (
        <View
          style={[s.privacyCurtain, { backgroundColor: t.bg }]}
          accessibilityLabel="TrustWeave privacy screen active"
        >
          <View style={[s.lockBadge, { backgroundColor: t.infoBg }]}>
            <Ionicons name="shield-checkmark" size={44} color={t.primary} />
          </View>
          <Text style={[s.lockTitle, { color: t.text }]}>TrustWeave</Text>
          <Text style={[s.lockSub, { color: t.textMuted }]}>
            {isLocked ? 'Wallet locked for your privacy' : 'Protected while in background'}
          </Text>
          {isLocked && appState === 'active' ? (
            <TouchableOpacity
              style={[s.unlockBtn, { backgroundColor: t.primary }]}
              onPress={handleUnlock}
              accessibilityRole="button"
              accessibilityLabel={`Unlock with ${lockLabel}`}
            >
              <Ionicons name="lock-open-outline" size={18} color={t.onPrimary} />
              <Text style={[s.unlockBtnText, { color: t.onPrimary }]}>Unlock with {lockLabel}</Text>
            </TouchableOpacity>
          ) : null}
        </View>
      ) : null}
    </View>
  )
}

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <AppErrorBoundary>
        <WalletShell />
      </AppErrorBoundary>
    </SafeAreaProvider>
  )
}

const s = StyleSheet.create({
  root: { flex: 1 },
  privacyCurtain: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    zIndex: 999,
  },
  lockBadge: {
    width: 84,
    height: 84,
    borderRadius: 42,
    alignItems: 'center',
    justifyContent: 'center',
  },
  lockTitle: { fontSize: 22, fontWeight: '800' },
  lockSub: { fontSize: 14 },
  unlockBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 22,
    minHeight: 48,
    borderRadius: 999,
    marginTop: 8,
  },
  unlockBtnText: { fontSize: 15, fontWeight: '700' },
})
