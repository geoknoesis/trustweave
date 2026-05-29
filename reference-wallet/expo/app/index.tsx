import { useCallback, useEffect, useState } from 'react'
import { Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { Link, useFocusEffect } from 'expo-router'
import { bootstrap, deleteCredential, list, resetWallet, type WalletState } from '@/lib/wallet'
import type { StoredCredential } from '@/lib/storage'

export default function HomeScreen() {
  const [state, setState] = useState<WalletState | null>(null)
  const [error, setError] = useState<string | null>(null)

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

  // Refresh when the tab regains focus (so a Receive in another tab shows up here).
  useFocusEffect(useCallback(() => {
    list().then((creds) => {
      setState((prev) => (prev ? { ...prev, credentials: creds } : prev))
    })
  }, []))

  const onDelete = (id: string) =>
    Alert.alert('Delete credential?', 'This cannot be undone.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          await deleteCredential(id)
          refresh()
        },
      },
    ])

  const onReset = () =>
    Alert.alert('Reset wallet?', 'Wipes your holder identity AND every stored credential.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Reset',
        style: 'destructive',
        onPress: async () => {
          await resetWallet()
          refresh()
        },
      },
    ])

  if (error) {
    return (
      <View style={s.center}>
        <Text style={s.errorTitle}>Bootstrap failed</Text>
        <Text style={s.errorBody}>{error}</Text>
      </View>
    )
  }
  if (!state) {
    return <View style={s.center}><Text>Initialising wallet…</Text></View>
  }

  return (
    <ScrollView style={s.scroll} contentContainerStyle={s.container}>
      <View style={s.panel}>
        <Text style={s.h2}>Your wallet</Text>
        <View style={s.identity}>
          <Text style={s.label}>HOLDER DID</Text>
          <Text style={s.mono}>{state.holder.did}</Text>
        </View>
        <Text style={s.muted}>
          🔒 Key in expo-secure-store (Keychain on iOS, EncryptedSharedPreferences on Android).
          The Ed25519 seed itself is loaded into JS for signing — that's the React Native
          trade-off. For hardware-bound Ed25519 see the native Android wallet's Phase 2.5b.
        </Text>
      </View>

      <View style={s.panel}>
        <Text style={s.h2}>Credentials ({state.credentials.length})</Text>
        {state.credentials.length === 0 ? (
          <View style={s.empty}>
            <Text style={s.emptyIcon}>📭</Text>
            <Text style={s.muted}>No credentials yet.</Text>
            <Link href="/receive" asChild>
              <TouchableOpacity style={s.btn}><Text style={s.btnText}>Receive a demo credential</Text></TouchableOpacity>
            </Link>
          </View>
        ) : (
          state.credentials.map((c) => (
            <CredCard key={c.id} cred={c} onDelete={() => onDelete(c.id)} />
          ))
        )}
      </View>

      <View style={s.panel}>
        <Text style={s.h3}>Danger zone</Text>
        <Text style={s.muted}>Wipe the wallet — irrecoverable.</Text>
        <TouchableOpacity style={s.btnDanger} onPress={onReset}>
          <Text style={s.btnText}>Reset wallet</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  )
}

function CredCard({ cred, onDelete }: { cred: StoredCredential; onDelete: () => void }) {
  return (
    <View style={s.credCard}>
      <Text style={s.credIcon}>🎓</Text>
      <View style={{ flex: 1 }}>
        <Text style={s.credTitle}>{cred.preview.title} <Text style={s.credFormat}>({cred.format})</Text></Text>
        {cred.preview.subtitle && <Text style={s.credSubtitle}>{cred.preview.subtitle}</Text>}
        <Text style={s.credIssuer}>issued by {cred.issuerDid.slice(0, 30)}…</Text>
      </View>
      <TouchableOpacity onPress={onDelete}>
        <Text style={s.deleteIcon}>🗑️</Text>
      </TouchableOpacity>
    </View>
  )
}

const s = StyleSheet.create({
  scroll: { backgroundColor: '#f8fafc' },
  container: { padding: 16, gap: 16 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  panel: { backgroundColor: '#ffffff', borderRadius: 8, padding: 16, gap: 8, borderWidth: 1, borderColor: '#e2e8f0' },
  h2: { color: '#1e3a8a', fontSize: 18, fontWeight: '600' },
  h3: { color: '#1e3a8a', fontSize: 16, fontWeight: '600' },
  label: { color: '#64748b', fontSize: 11, letterSpacing: 0.5, textTransform: 'uppercase' },
  mono: { fontFamily: 'Menlo', fontSize: 12, color: '#1f2937' },
  muted: { color: '#64748b', fontSize: 13 },
  identity: { backgroundColor: '#f1f5f9', borderLeftWidth: 4, borderLeftColor: '#0d9488', padding: 12, borderRadius: 6, gap: 4 },
  empty: { alignItems: 'center', paddingVertical: 24, gap: 8 },
  emptyIcon: { fontSize: 32 },
  btn: { backgroundColor: '#1e3a8a', paddingHorizontal: 16, paddingVertical: 10, borderRadius: 6, marginTop: 8 },
  btnDanger: { backgroundColor: '#dc2626', paddingHorizontal: 16, paddingVertical: 10, borderRadius: 6, alignSelf: 'flex-start', marginTop: 4 },
  btnText: { color: '#ffffff', fontWeight: '500' },
  credCard: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#f1f5f9', padding: 12, borderRadius: 6, gap: 12 },
  credIcon: { fontSize: 28 },
  credTitle: { color: '#1e3a8a', fontWeight: '600' },
  credFormat: { fontSize: 10, color: '#64748b', fontWeight: '400' },
  credSubtitle: { color: '#1f2937', fontSize: 13 },
  credIssuer: { color: '#64748b', fontSize: 11, fontFamily: 'Menlo' },
  deleteIcon: { fontSize: 20 },
  errorTitle: { color: '#dc2626', fontWeight: '600', fontSize: 16 },
  errorBody: { color: '#64748b', marginTop: 4, textAlign: 'center' },
})
