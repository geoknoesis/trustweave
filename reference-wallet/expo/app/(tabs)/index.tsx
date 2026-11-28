import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ActivityIndicator,
  Alert,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native'
import { useFocusEffect, useRouter } from 'expo-router'
import { Ionicons } from '@expo/vector-icons'
import { CredentialDetailModal } from '@/components/CredentialDetailModal'
import { CredentialLibraryCard } from '@/components/CredentialLibraryCard'
import { OnboardingOverlay } from '@/components/OnboardingOverlay'
import { haptics } from '@/lib/haptics'
import {
  bootstrap,
  deleteCredential,
  getPreferences,
  list,
  updatePreferences,
  type StoredCredential,
  type WalletPreferences,
  type WalletState,
} from '@/lib/wallet'
import { credentialTrustBadge, useWalletTheme } from '@/lib/credentialDisplay'
import { setPendingCredentialIdToShare } from '@/lib/pendingScanStore'

export default function HomeScreen() {
  const t = useWalletTheme()
  const router = useRouter()
  const [state, setState] = useState<WalletState | null>(null)
  const [prefs, setPrefs] = useState<WalletPreferences | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [detailCred, setDetailCred] = useState<StoredCredential | null>(null)
  const [query, setQuery] = useState('')
  const [refreshing, setRefreshing] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const [s, p] = await Promise.all([bootstrap(), getPreferences()])
      setState(s)
      setPrefs(p)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  const onPullRefresh = useCallback(async () => {
    setRefreshing(true)
    try {
      await refresh()
    } finally {
      setRefreshing(false)
    }
  }, [refresh])

  useEffect(() => {
    refresh()
  }, [refresh])

  useFocusEffect(
    useCallback(() => {
      Promise.all([list(), getPreferences()])
        .then(([creds, p]) => {
          setPrefs(p)
          setState((prev) => (prev ? { ...prev, credentials: creds } : prev))
        })
        .catch((e) => setError(e instanceof Error ? e.message : String(e)))
    }, []),
  )

  const handleOnboardingComplete = async (biometricLockEnabled: boolean) => {
    const updated = await updatePreferences({
      onboardingComplete: true,
      biometricLockEnabled,
    })
    haptics.success()
    setPrefs(updated)
  }

  const onDelete = (cred: StoredCredential) =>
    Alert.alert(
      `Remove "${cred.preview.title}"?`,
      'This removes the credential from this device. You will need the issuer to reissue it if you want it back.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: async () => {
            await deleteCredential(cred.id)
            haptics.warning()
            setDetailCred(null)
            refresh()
          },
        },
      ],
    )

  const handleShareFromDetail = (cred: StoredCredential) => {
    setDetailCred(null)
    setPendingCredentialIdToShare(cred.id)
    router.push('/present')
  }

  const credentials = state?.credentials ?? []
  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return credentials
    return credentials.filter(
      (c) =>
        c.preview.title.toLowerCase().includes(q) ||
        (c.preview.subtitle ?? '').toLowerCase().includes(q) ||
        (c.issuerName ?? '').toLowerCase().includes(q) ||
        c.type.some((ty) => ty.toLowerCase().includes(q)),
    )
  }, [credentials, query])

  if (error) {
    return (
      <View style={[s.center, s.flex, { backgroundColor: t.bg }]}>
        <Ionicons name="alert-circle-outline" size={44} color={t.danger} />
        <Text style={[s.errorTitle, { color: t.danger }]}>Could not open wallet</Text>
        <Text style={[s.errorBody, { color: t.textMuted }]}>{error}</Text>
        <TouchableOpacity
          style={[s.primaryBtn, { backgroundColor: t.primary, marginTop: 12 }]}
          onPress={() => router.push('/settings')}
          accessibilityRole="button"
        >
          <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>Open Settings</Text>
        </TouchableOpacity>
      </View>
    )
  }

  if (!state || !prefs) {
    return (
      <View style={[s.center, s.flex, { backgroundColor: t.bg }]}>
        <ActivityIndicator color={t.primary} size="large" />
        <Text style={[s.muted, { color: t.textMuted }]}>Opening your wallet…</Text>
      </View>
    )
  }

  const count = credentials.length
  const attentionStats = useMemo(() => {
    let expired = 0
    let expiring = 0
    for (const c of credentials) {
      const b = credentialTrustBadge(c)
      if (b.label === 'Expired') expired += 1
      else if (b.label === 'Expiring soon') expiring += 1
    }
    return { expired, expiring }
  }, [credentials])

  return (
    <>
      <OnboardingOverlay visible={!prefs.onboardingComplete} onComplete={handleOnboardingComplete} />

      <View style={[s.flex, { backgroundColor: t.bg }]}>
        <ScrollView
          style={s.flex}
          contentContainerStyle={s.container}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={onPullRefresh}
              tintColor={t.primary}
              colors={[t.primary]}
            />
          }
        >
          <View style={s.hero}>
            <Text style={[s.heroTitle, { color: t.text }]}>My credentials</Text>
            <Text style={[s.heroSub, { color: t.textMuted }]}>
              {count === 0
                ? 'Private IDs, diplomas, and passes on this device.'
                : `${count} credential${count === 1 ? '' : 's'} ready to present.`}
            </Text>
          </View>

          {attentionStats.expired > 0 || attentionStats.expiring > 0 ? (
            <View
              style={[
                s.attentionBanner,
                {
                  backgroundColor: attentionStats.expired > 0 ? t.dangerBg : t.warningBg,
                  borderColor: attentionStats.expired > 0 ? t.danger : t.warning,
                },
              ]}
              accessibilityRole="alert"
            >
              <Ionicons
                name={attentionStats.expired > 0 ? 'alert-circle-outline' : 'time-outline'}
                size={20}
                color={attentionStats.expired > 0 ? t.danger : t.warning}
              />
              <Text
                style={[
                  s.attentionText,
                  { color: attentionStats.expired > 0 ? t.danger : t.warning },
                ]}
              >
                {attentionStats.expired > 0
                  ? `${attentionStats.expired} credential${attentionStats.expired === 1 ? ' has' : 's have'} expired. Contact the issuer for a renewal.`
                  : `${attentionStats.expiring} credential${attentionStats.expiring === 1 ? ' is' : 's are'} expiring soon.`}
              </Text>
            </View>
          ) : null}

          {count >= 4 ? (
            <View style={[s.searchWrap, { backgroundColor: t.surface, borderColor: t.border }]}>
              <Ionicons name="search-outline" size={18} color={t.textMuted} />
              <TextInput
                style={[s.searchInput, { color: t.text }]}
                value={query}
                onChangeText={setQuery}
                placeholder="Search credentials…"
                placeholderTextColor={t.textMuted}
                clearButtonMode="while-editing"
                accessibilityLabel="Search credentials"
              />
            </View>
          ) : null}

          {count === 0 ? (
            <View style={[s.emptyPanel, { backgroundColor: t.surface, borderColor: t.border }, t.shadow]}>
              <View style={[s.emptyIconWrap, { backgroundColor: t.infoBg }]}>
                <Ionicons name="wallet-outline" size={36} color={t.primary} />
              </View>
              <Text style={[s.emptyTitle, { color: t.text }]}>Your wallet is ready</Text>
              <Text style={[s.muted, { color: t.textMuted }]}>
                Keep your verified IDs, diplomas, and passes in one private place. Scan a QR code to add your
                first card.
              </Text>
              <TouchableOpacity
                style={[s.primaryBtn, { backgroundColor: t.primary, width: '100%' }]}
                onPress={() => router.navigate('/scan')}
                accessibilityRole="button"
                accessibilityLabel="Scan QR code or tap NFC"
              >
                <Ionicons name="qr-code-outline" size={18} color={t.onPrimary} />
                <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>Scan QR or tap NFC</Text>
              </TouchableOpacity>
            </View>
          ) : filtered.length === 0 ? (
            <View style={[s.emptyPanel, { backgroundColor: t.surface, borderColor: t.border }]}>
              <Text style={[s.emptyTitle, { color: t.text }]}>No matching credentials</Text>
              <Text style={[s.muted, { color: t.textMuted }]}>
                Try a different search term or clear the filter.
              </Text>
            </View>
          ) : (
            filtered.map((c) => (
              <CredentialLibraryCard key={c.id} cred={c} onPress={() => setDetailCred(c)} />
            ))
          )}
        </ScrollView>

        {count > 0 ? (
          <View style={s.fabDock} pointerEvents="box-none">
            <TouchableOpacity
              style={[s.fab, { backgroundColor: t.primary }, t.shadow]}
              onPress={() => router.navigate('/scan')}
              accessibilityRole="button"
              accessibilityLabel="Scan QR code or tap NFC to add or share"
            >
              <Ionicons name="qr-code-outline" size={20} color={t.onPrimary} />
              <Text style={[s.fabText, { color: t.onPrimary }]}>Scan / Tap</Text>
            </TouchableOpacity>
          </View>
        ) : null}
      </View>

      <CredentialDetailModal
        cred={detailCred}
        visible={detailCred !== null}
        onClose={() => setDetailCred(null)}
        onDelete={() => detailCred && onDelete(detailCred)}
        onShare={handleShareFromDetail}
      />
    </>
  )
}

const s = StyleSheet.create({
  flex: { flex: 1 },
  container: { padding: 16, paddingBottom: 96 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 10 },
  hero: { marginBottom: 16, paddingTop: 4 },
  heroTitle: { fontSize: 28, fontWeight: '800', letterSpacing: -0.3 },
  heroSub: { fontSize: 14, marginTop: 4 },
  searchWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 14,
    minHeight: 46,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 14,
  },
  searchInput: { flex: 1, fontSize: 15, paddingVertical: 8 },
  emptyPanel: {
    borderRadius: 16,
    padding: 28,
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
  },
  emptyIconWrap: {
    width: 72,
    height: 72,
    borderRadius: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyTitle: { fontSize: 19, fontWeight: '700' },
  muted: { fontSize: 14, textAlign: 'center', lineHeight: 20 },
  primaryBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingHorizontal: 22,
    minHeight: 50,
    borderRadius: 999,
    marginTop: 6,
  },
  primaryBtnText: { fontWeight: '700', fontSize: 15 },
  fabDock: {
    position: 'absolute',
    right: 16,
    bottom: 16,
    alignItems: 'flex-end',
  },
  fab: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 20,
    minHeight: 52,
    borderRadius: 999,
  },
  fabText: { fontSize: 15, fontWeight: '700' },
  errorTitle: { fontWeight: '700', fontSize: 18 },
  errorBody: { textAlign: 'center', lineHeight: 20 },
  attentionBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 14,
  },
  attentionText: { flex: 1, fontSize: 13, fontWeight: '600', lineHeight: 18 },
})
