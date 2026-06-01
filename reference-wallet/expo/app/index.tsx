import { useCallback, useEffect, useState } from 'react'
import { Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { Link, useFocusEffect } from 'expo-router'
import { CredentialDetailModal } from '@/components/CredentialDetailModal'
import { CredentialLibraryCard } from '@/components/CredentialLibraryCard'
import { HolderDidQr } from '@/components/HolderDidQr'
import { bootstrap, deleteCredential, list, resetWallet, type WalletState } from '@/lib/wallet'
import type { StoredCredential } from '@/lib/storage'
import { theme } from '@/lib/credentialDisplay'

export default function HomeScreen() {
  const [state, setState] = useState<WalletState | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [detailCred, setDetailCred] = useState<StoredCredential | null>(null)
  const [showIdentity, setShowIdentity] = useState(false)

  const refresh = useCallback(async () => {
    try {
      const s = await bootstrap()
      setState(s)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  useFocusEffect(useCallback(() => {
    list()
      .then((creds) => setState((prev) => (prev ? { ...prev, credentials: creds } : prev)))
      .catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, []))

  const onDelete = (id: string) =>
    Alert.alert('Remove credential?', 'This removes it from your wallet on this device.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Remove',
        style: 'destructive',
        onPress: async () => {
          await deleteCredential(id)
          setDetailCred(null)
          refresh()
        },
      },
    ])

  const onReset = () =>
    Alert.alert('Reset wallet?', 'This removes all credentials and your digital identity.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Reset',
        style: 'destructive',
        onPress: async () => {
          await resetWallet()
          setShowIdentity(false)
          refresh()
        },
      },
    ])

  if (error) {
    return (
      <View style={[s.center, s.flex]}>
        <Text style={s.errorTitle}>Could not open wallet</Text>
        <Text style={s.errorBody}>{error}</Text>
      </View>
    )
  }
  if (!state) {
    return (
      <View style={[s.center, s.flex]}>
        <Text style={s.muted}>Opening your wallet…</Text>
      </View>
    )
  }

  const count = state.credentials.length

  return (
    <>
      <ScrollView style={[s.scroll, s.flex]} contentContainerStyle={s.container}>
        <View style={s.hero}>
          <Text style={s.heroTitle}>My credentials</Text>
          <Text style={s.heroSub}>
            {count === 0
              ? 'Your personal library of verified credentials.'
              : `${count} credential${count === 1 ? '' : 's'} stored on this device.`}
          </Text>
        </View>

        {count === 0 ? (
          <View style={s.emptyPanel}>
            <Text style={s.emptyIcon}>📚</Text>
            <Text style={s.emptyTitle}>Your library is empty</Text>
            <Text style={s.muted}>
              Scan a QR code from an issuer to add your first credential — like Europass stores digital diplomas.
            </Text>
            <Link href="/receive" asChild>
              <TouchableOpacity style={s.btn}><Text style={s.btnText}>Add credential</Text></TouchableOpacity>
            </Link>
          </View>
        ) : (
          <>
            <View style={s.actionRow}>
              <Link href="/receive" asChild>
                <TouchableOpacity style={s.btn}><Text style={s.btnText}>Add</Text></TouchableOpacity>
              </Link>
              <Link href="/present" asChild>
                <TouchableOpacity style={s.btnOutline}><Text style={s.btnOutlineText}>Share</Text></TouchableOpacity>
              </Link>
            </View>
            {state.credentials.map((c) => (
              <CredentialLibraryCard key={c.id} cred={c} onPress={() => setDetailCred(c)} />
            ))}
          </>
        )}

        <TouchableOpacity
          style={s.identityToggle}
          onPress={() => setShowIdentity((v) => !v)}
        >
          <Text style={s.identityToggleText}>
            {showIdentity ? '▾' : '▸'} Your digital identity
          </Text>
        </TouchableOpacity>
        {showIdentity && (
          <View style={s.identityPanel}>
            <Text style={s.muted}>
              Show this QR when someone needs to identify you before you share a credential.
            </Text>
            <HolderDidQr did={state.holder.did} />
            <Text style={s.mono}>{state.holder.did}</Text>
            <TouchableOpacity onPress={onReset} style={s.resetLink}>
              <Text style={s.resetText}>Reset wallet</Text>
            </TouchableOpacity>
          </View>
        )}
      </ScrollView>

      <CredentialDetailModal
        cred={detailCred}
        visible={detailCred !== null}
        onClose={() => setDetailCred(null)}
        onDelete={() => detailCred && onDelete(detailCred.id)}
      />
    </>
  )
}

const s = StyleSheet.create({
  flex: { flex: 1 },
  scroll: { backgroundColor: theme.bg },
  container: { padding: 16, paddingBottom: 32 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  hero: { marginBottom: 16, paddingTop: 4 },
  heroTitle: { fontSize: 26, fontWeight: '700', color: theme.primary },
  heroSub: { fontSize: 14, color: theme.textMuted, marginTop: 4 },
  emptyPanel: {
    backgroundColor: theme.surface,
    borderRadius: theme.radius,
    padding: 28,
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    borderColor: theme.border,
    ...theme.shadow,
  },
  emptyIcon: { fontSize: 40 },
  emptyTitle: { fontSize: 17, fontWeight: '600', color: theme.primary },
  muted: { color: theme.textMuted, fontSize: 14, textAlign: 'center' },
  actionRow: { flexDirection: 'row', gap: 10, marginBottom: 16 },
  btn: {
    backgroundColor: theme.primary,
    paddingHorizontal: 20,
    paddingVertical: 11,
    borderRadius: 999,
    alignItems: 'center',
    minWidth: 100,
  },
  btnText: { color: '#ffffff', fontWeight: '600' },
  btnOutline: {
    borderWidth: 2,
    borderColor: theme.primary,
    paddingHorizontal: 20,
    paddingVertical: 9,
    borderRadius: 999,
    alignItems: 'center',
    minWidth: 100,
  },
  btnOutlineText: { color: theme.primary, fontWeight: '600' },
  identityToggle: { marginTop: 20, paddingVertical: 12 },
  identityToggleText: { color: theme.primary, fontWeight: '600', fontSize: 15 },
  identityPanel: {
    backgroundColor: theme.surface,
    borderRadius: theme.radius,
    padding: 16,
    gap: 10,
    borderWidth: 1,
    borderColor: theme.border,
  },
  mono: { fontFamily: 'Menlo', fontSize: 10, color: theme.textMuted },
  resetLink: { alignSelf: 'center', paddingVertical: 8 },
  resetText: { color: '#dc2626', fontSize: 14, fontWeight: '500' },
  errorTitle: { color: '#dc2626', fontWeight: '600', fontSize: 16 },
  errorBody: { color: theme.textMuted, marginTop: 4, textAlign: 'center' },
})
