import { useCallback, useState } from 'react'
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  Share,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native'
import { useFocusEffect, useRouter } from 'expo-router'
import { Ionicons } from '@expo/vector-icons'
import { HolderDidQr } from '@/components/HolderDidQr'
import { IssuerTrustSheet } from '@/components/IssuerTrustSheet'
import { confirmSensitiveAction, getBiometricAvailability } from '@/lib/biometrics'
import { monoFont, useWalletTheme } from '@/lib/credentialDisplay'
import { demoBackendBaseUrl, fetchBackendIssuers, receiveCredential } from '@/lib/demoBackend'
import { shortDidFingerprint } from '@/lib/issuerTrust'
import {
  bootstrap,
  exportCredentialBackup,
  getPreferences,
  removeAcceptedIssuer,
  resetWallet,
  restoreCredentialBackup,
  reviewableIssuers,
  store,
  UntrustedIssuerError,
  updatePreferences,
  type WalletPreferences,
  type WalletState,
} from '@/lib/wallet'

export default function SettingsScreen() {
  const t = useWalletTheme()
  const router = useRouter()
  const [state, setState] = useState<WalletState | null>(null)
  const [prefs, setPrefs] = useState<WalletPreferences | null>(null)
  const [biometricLabel, setBiometricLabel] = useState('Biometric confirmation')
  const [issuers, setIssuers] = useState<Array<{ did: string; source: 'configured' | 'accepted' }>>([])
  const [showIdentityQr, setShowIdentityQr] = useState(false)
  const [showRestoreBox, setShowRestoreBox] = useState(false)
  const [restoreText, setRestoreText] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [pendingIssuer, setPendingIssuer] = useState<{
    did: string
    onApprove: () => Promise<void>
  } | null>(null)

  const refresh = useCallback(async () => {
    const [w, p, bio, rev] = await Promise.all([
      bootstrap(),
      getPreferences(),
      getBiometricAvailability(),
      reviewableIssuers(),
    ])
    setState(w)
    setPrefs(p)
    setBiometricLabel(bio.label)
    setIssuers(rev)
  }, [])

  useFocusEffect(
    useCallback(() => {
      void refresh()
    }, [refresh]),
  )

  const toggleBiometric = async (next: boolean) => {
    const updated = await updatePreferences({ biometricLockEnabled: next })
    setPrefs(updated)
  }

  const toggleDeveloper = async (next: boolean) => {
    const updated = await updatePreferences({ developerMode: next })
    setPrefs(updated)
  }

  const handleRemoveIssuer = (did: string) =>
    Alert.alert(
      'Remove trusted issuer?',
      `Credentials from issuer ${shortDidFingerprint(did)} will no longer be trusted for sharing.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: async () => {
            await removeAcceptedIssuer(did)
            await refresh()
          },
        },
      ],
    )

  const handleExportBackup = async () => {
    try {
      const json = await exportCredentialBackup()
      await Share.share({
        title: 'TrustWeave Credential Backup',
        message: json,
      })
      setNotice('Credential backup exported. Remember: private keys are never included in backups.')
    } catch (e) {
      Alert.alert('Export failed', e instanceof Error ? e.message : String(e))
    }
  }

  const runRestore = async (confirmedIssuer?: string) => {
    setBusy(true)
    setNotice(null)
    try {
      const backendIssuers = await fetchBackendIssuers()
      const res = await restoreCredentialBackup(restoreText, {
        backendIssuers,
        confirmedIssuer: confirmedIssuer ?? null,
      })
      if (res.untrustedIssuers.length > 0 && !confirmedIssuer) {
        const first = res.untrustedIssuers[0]
        setPendingIssuer({
          did: first,
          onApprove: async () => {
            setPendingIssuer(null)
            await runRestore(first)
          },
        })
        return
      }
      setRestoreText('')
      setShowRestoreBox(false)
      setNotice(
        `Restored ${res.restored} credential${res.restored === 1 ? '' : 's'}${res.skipped > 0 ? ` (${res.skipped} skipped)` : ''}.`,
      )
      await refresh()
    } catch (e) {
      Alert.alert('Restore failed', e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  const handleDemoReceive = async () => {
    if (!state) return
    setBusy(true)
    setNotice(null)
    try {
      const [offer, backendIssuers] = await Promise.all([
        receiveCredential(state.holder.did),
        fetchBackendIssuers(),
      ])
      const finish = async (confirmedIssuer?: string) => {
        await store(offer.credential, offer.format, offer.selectivelyDisclosable ?? [], {
          backendIssuers,
          confirmedIssuer: confirmedIssuer ?? null,
        })
        setNotice('Sample demo credential added to your wallet.')
        await refresh()
      }
      try {
        await finish()
      } catch (err) {
        if (err instanceof UntrustedIssuerError) {
          setPendingIssuer({
            did: err.issuerDid,
            onApprove: async () => {
              setPendingIssuer(null)
              await finish(err.issuerDid)
            },
          })
          return
        }
        throw err
      }
    } catch (e) {
      Alert.alert('Could not fetch demo credential', e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  const handleReset = () => {
    Alert.alert(
      'Reset wallet?',
      'This permanently removes all stored credentials, trusted issuers, and your on-device signing key.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Continue',
          style: 'destructive',
          onPress: () => {
            Alert.alert(
              'Final confirmation',
              'This cannot be undone. You will need issuers to reissue your credentials.',
              [
                { text: 'Cancel', style: 'cancel' },
                {
                  text: 'Reset wallet',
                  style: 'destructive',
                  onPress: async () => {
                    if (prefs?.biometricLockEnabled) {
                      const ok = await confirmSensitiveAction('Confirm wallet reset')
                      if (!ok) return
                    }
                    await resetWallet()
                    await refresh()
                    router.replace('/')
                  },
                },
              ],
            )
          },
        },
      ],
    )
  }

  if (!state || !prefs) {
    return (
      <View style={[s.center, s.flex, { backgroundColor: t.bg }]}>
        <ActivityIndicator color={t.primary} />
      </View>
    )
  }

  return (
    <>
      <ScrollView style={[s.flex, { backgroundColor: t.bg }]} contentContainerStyle={s.container}>
        {notice ? (
          <View style={[s.noticeBanner, { backgroundColor: t.infoBg, borderColor: t.primary }]}>
            <Ionicons name="information-circle-outline" size={18} color={t.primary} />
            <Text style={[s.noticeText, { color: t.text }]}>{notice}</Text>
          </View>
        ) : null}

        <Text style={[s.sectionLabel, { color: t.textMuted }]}>SECURITY & PRIVACY</Text>
        <View style={[s.card, { backgroundColor: t.surface, borderColor: t.border }]}>
          <View style={s.switchRow}>
            <View style={{ flex: 1, gap: 2 }}>
              <Text style={[s.rowTitle, { color: t.text }]}>{biometricLabel}</Text>
              <Text style={[s.rowSub, { color: t.textMuted }]}>
                Ask for confirmation before sharing a credential or resetting your wallet.
              </Text>
            </View>
            <Switch
              value={prefs.biometricLockEnabled}
              onValueChange={toggleBiometric}
              trackColor={{ false: t.border, true: t.primary }}
            />
          </View>

          <View style={[s.divider, { backgroundColor: t.border }]} />

          <View style={s.custodyBox}>
            <Ionicons name="key-outline" size={20} color={t.primary} />
            <View style={{ flex: 1, gap: 3 }}>
              <Text style={[s.rowTitle, { color: t.text }]}>
                Encrypted on-device key (software signing)
              </Text>
              <Text style={[s.rowSub, { color: t.textMuted }]}>
                Your signing key is stored encrypted by your device (Keychain / EncryptedSharedPreferences) and
                loaded in memory to sign. Hardware-isolated signing is not active in this Expo build.
              </Text>
            </View>
          </View>
        </View>

        <Text style={[s.sectionLabel, { color: t.textMuted }]}>TRUSTED ISSUERS</Text>
        <View style={[s.card, { backgroundColor: t.surface, borderColor: t.border }]}>
          <Text style={[s.rowSub, { color: t.textMuted }]}>
            Credentials are only accepted from pre-configured issuers, this wallet’s configured backend, or
            issuers you explicitly confirmed.
          </Text>
          {issuers.length === 0 ? (
            <Text style={[s.emptyInline, { color: t.textMuted }]}>
              No custom issuers confirmed yet.
            </Text>
          ) : (
            issuers.map((item) => (
              <View key={item.did} style={[s.issuerRow, { borderTopColor: t.border }]}>
                <View style={{ flex: 1, gap: 2 }}>
                  <Text style={[s.rowTitle, { color: t.text }]}>
                    Issuer · {shortDidFingerprint(item.did)}
                  </Text>
                  <Text style={[s.mono, { color: t.textMuted }]} numberOfLines={1}>
                    {item.did}
                  </Text>
                </View>
                {item.source === 'configured' ? (
                  <View style={[s.tag, { backgroundColor: t.successBg }]}>
                    <Text style={[s.tagText, { color: t.success }]}>Built-in</Text>
                  </View>
                ) : (
                  <TouchableOpacity
                    onPress={() => handleRemoveIssuer(item.did)}
                    hitSlop={8}
                    accessibilityRole="button"
                  >
                    <Text style={[s.removeText, { color: t.danger }]}>Remove</Text>
                  </TouchableOpacity>
                )}
              </View>
            ))
          )}
        </View>

        <Text style={[s.sectionLabel, { color: t.textMuted }]}>BACKUP & RECOVERY</Text>
        <View style={[s.card, { backgroundColor: t.surface, borderColor: t.border }]}>
          <Text style={[s.rowSub, { color: t.textMuted }]}>
            Backups contain credential records only — never your private signing key. If you lose this phone,
            create a new wallet and ask issuers to reissue your credentials.
          </Text>
          <View style={s.btnRow}>
            <TouchableOpacity
              style={[s.outlineBtn, { borderColor: t.primary, flex: 1 }]}
              onPress={handleExportBackup}
              accessibilityRole="button"
            >
              <Text style={[s.outlineBtnText, { color: t.primary }]}>Export backup</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[s.outlineBtn, { borderColor: t.border, flex: 1 }]}
              onPress={() => setShowRestoreBox((v) => !v)}
              accessibilityRole="button"
            >
              <Text style={[s.outlineBtnText, { color: t.text }]}>Restore backup</Text>
            </TouchableOpacity>
          </View>
          {showRestoreBox ? (
            <View style={{ gap: 8, marginTop: 4 }}>
              <TextInput
                style={[
                  s.input,
                  { backgroundColor: t.surfaceElevated, borderColor: t.border, color: t.text },
                ]}
                value={restoreText}
                onChangeText={setRestoreText}
                placeholder="Paste credential backup JSON…"
                placeholderTextColor={t.textMuted}
                multiline
              />
              <TouchableOpacity
                style={[s.primaryBtn, { backgroundColor: t.primary }, (!restoreText.trim() || busy) && s.disabled]}
                disabled={!restoreText.trim() || busy}
                onPress={() => void runRestore()}
                accessibilityRole="button"
              >
                <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>
                  {busy ? 'Restoring…' : 'Verify & restore'}
                </Text>
              </TouchableOpacity>
            </View>
          ) : null}
        </View>

        <Text style={[s.sectionLabel, { color: t.textMuted }]}>MY WALLET ID</Text>
        <View style={[s.card, { backgroundColor: t.surface, borderColor: t.border }]}>
          <Pressable
            style={s.collapseRow}
            onPress={() => setShowIdentityQr((v) => !v)}
            accessibilityRole="button"
            accessibilityState={{ expanded: showIdentityQr }}
          >
            <Text style={[s.rowTitle, { color: t.text }]}>Show Wallet ID QR</Text>
            <Ionicons
              name={showIdentityQr ? 'chevron-up' : 'chevron-down'}
              size={18}
              color={t.textMuted}
            />
          </Pressable>
          {showIdentityQr ? (
            <View style={{ marginTop: 6 }}>
              <HolderDidQr did={state.holder.did} />
            </View>
          ) : null}
        </View>

        <Text style={[s.sectionLabel, { color: t.textMuted }]}>ADVANCED</Text>
        <View style={[s.card, { backgroundColor: t.surface, borderColor: t.border }]}>
          <View style={s.switchRow}>
            <View style={{ flex: 1, gap: 2 }}>
              <Text style={[s.rowTitle, { color: t.text }]}>Developer mode</Text>
              <Text style={[s.rowSub, { color: t.textMuted }]}>
                Show local demo server URL and quick demo tools.
              </Text>
            </View>
            <Switch
              value={prefs.developerMode}
              onValueChange={toggleDeveloper}
              trackColor={{ false: t.border, true: t.primary }}
            />
          </View>
          {prefs.developerMode ? (
            <View style={{ gap: 10, marginTop: 6 }}>
              <Text style={[s.mono, { color: t.textMuted }]}>Backend: {demoBackendBaseUrl}</Text>
              <TouchableOpacity
                style={[s.outlineBtn, { borderColor: t.primary }, busy && s.disabled]}
                onPress={handleDemoReceive}
                disabled={busy}
                accessibilityRole="button"
              >
                <Text style={[s.outlineBtnText, { color: t.primary }]}>
                  {busy ? 'Requesting…' : 'Add sample demo credential'}
                </Text>
              </TouchableOpacity>
            </View>
          ) : null}
        </View>

        <TouchableOpacity
          style={[s.resetBtn, { borderColor: t.danger, backgroundColor: t.dangerBg }]}
          onPress={handleReset}
          accessibilityRole="button"
        >
          <Ionicons name="trash-outline" size={18} color={t.danger} />
          <Text style={[s.resetText, { color: t.danger }]}>Reset wallet</Text>
        </TouchableOpacity>
      </ScrollView>

      <IssuerTrustSheet
        visible={pendingIssuer !== null}
        issuerDid={pendingIssuer?.did ?? ''}
        busy={busy}
        onConfirm={() => {
          if (pendingIssuer) void pendingIssuer.onApprove()
        }}
        onCancel={() => setPendingIssuer(null)}
      />
    </>
  )
}

const s = StyleSheet.create({
  flex: { flex: 1 },
  center: { alignItems: 'center', justifyContent: 'center' },
  container: { padding: 16, gap: 12, paddingBottom: 40 },
  sectionLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 0.6, marginTop: 6, marginLeft: 4 },
  card: { borderRadius: 14, padding: 16, gap: 12, borderWidth: 1 },
  switchRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  rowTitle: { fontSize: 15, fontWeight: '600' },
  rowSub: { fontSize: 13, lineHeight: 18 },
  divider: { height: 1 },
  custodyBox: { flexDirection: 'row', alignItems: 'flex-start', gap: 10 },
  emptyInline: { fontSize: 13, fontStyle: 'italic' },
  issuerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingTop: 10,
    borderTopWidth: 1,
  },
  mono: { fontFamily: monoFont, fontSize: 11 },
  tag: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 999 },
  tagText: { fontSize: 11, fontWeight: '700' },
  removeText: { fontSize: 13, fontWeight: '700', paddingHorizontal: 6, paddingVertical: 4 },
  btnRow: { flexDirection: 'row', gap: 10 },
  outlineBtn: {
    minHeight: 44,
    borderRadius: 999,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 14,
  },
  outlineBtnText: { fontSize: 14, fontWeight: '700' },
  primaryBtn: {
    minHeight: 46,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryBtnText: { fontSize: 14, fontWeight: '700' },
  input: {
    borderWidth: 1,
    borderRadius: 10,
    padding: 10,
    minHeight: 84,
    fontFamily: monoFont,
    fontSize: 11,
    textAlignVertical: 'top',
  },
  collapseRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 36,
  },
  noticeBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    padding: 12,
    borderRadius: 12,
    borderWidth: 1,
  },
  noticeText: { flex: 1, fontSize: 13, lineHeight: 18 },
  resetBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    minHeight: 50,
    borderRadius: 999,
    borderWidth: 1,
    marginTop: 12,
  },
  resetText: { fontSize: 15, fontWeight: '700' },
  disabled: { opacity: 0.55 },
})
