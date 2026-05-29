import { useEffect, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { Link } from 'expo-router'
import { bootstrap, store, type WalletState } from '@/lib/wallet'
import type { StoredCredential } from '@/lib/storage'
import { receiveCredential } from '@/lib/demoBackend'

type Status =
  | { kind: 'idle' }
  | { kind: 'requesting' }
  | { kind: 'success'; stored: StoredCredential }
  | { kind: 'error'; message: string }

export default function ReceiveScreen() {
  const [state, setState] = useState<WalletState | null>(null)
  const [status, setStatus] = useState<Status>({ kind: 'idle' })

  useEffect(() => {
    bootstrap().then(setState).catch((e) => setStatus({ kind: 'error', message: String(e) }))
  }, [])

  const onReceive = async () => {
    if (!state) return
    setStatus({ kind: 'requesting' })
    try {
      const offer = await receiveCredential(state.holder.did)
      const stored = await store(offer.credential, offer.format, offer.selectivelyDisclosable ?? [])
      setStatus({ kind: 'success', stored })
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  if (!state) return <View style={s.center}><Text>Initialising wallet…</Text></View>

  return (
    <ScrollView style={s.scroll} contentContainerStyle={s.container}>
      <View style={s.panel}>
        <Text style={s.h2}>Receive a credential</Text>
        <Text style={s.muted}>
          The demo issuer signs a Bachelor of Science credential as an SD-JWT VC. At
          presentation time you choose which claims to reveal.
        </Text>
        <View style={s.identity}>
          <Text style={s.label}>SUBJECT DID (YOU)</Text>
          <Text style={s.mono}>{state.holder.did}</Text>
        </View>
        <TouchableOpacity
          style={[s.btn, status.kind === 'requesting' && s.btnDisabled]}
          onPress={onReceive}
          disabled={status.kind === 'requesting'}
        >
          {status.kind === 'requesting' ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <ActivityIndicator color="#fff" />
              <Text style={s.btnText}>Requesting…</Text>
            </View>
          ) : (
            <Text style={s.btnText}>Receive demo credential</Text>
          )}
        </TouchableOpacity>
      </View>

      {status.kind === 'success' && (
        <View style={[s.panel, s.successCallout]}>
          <Text style={s.successTitle}>Received and stored</Text>
          <Text>{status.stored.preview.title} ({status.stored.format})</Text>
          {status.stored.preview.subtitle && <Text>{status.stored.preview.subtitle}</Text>}
          {status.stored.selectivelyDisclosable.length > 0 && (
            <>
              <Text style={[s.label, { marginTop: 8 }]}>SELECTIVELY DISCLOSABLE</Text>
              <Text style={s.mono}>{status.stored.selectivelyDisclosable.join(', ')}</Text>
            </>
          )}
          <View style={{ flexDirection: 'row', gap: 8, marginTop: 12 }}>
            <Link href="/" asChild>
              <TouchableOpacity style={s.btn}><Text style={s.btnText}>View in wallet</Text></TouchableOpacity>
            </Link>
            <Link href="/present" asChild>
              <TouchableOpacity style={s.btnOutline}><Text style={s.btnOutlineText}>Present it now</Text></TouchableOpacity>
            </Link>
          </View>
        </View>
      )}

      {status.kind === 'error' && (
        <View style={[s.panel, s.errorCallout]}>
          <Text style={s.errorTitle}>Failed</Text>
          <Text>{status.message}</Text>
        </View>
      )}
    </ScrollView>
  )
}

const s = StyleSheet.create({
  scroll: { backgroundColor: '#f8fafc' },
  container: { padding: 16, gap: 16 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  panel: { backgroundColor: '#ffffff', borderRadius: 8, padding: 16, gap: 12, borderWidth: 1, borderColor: '#e2e8f0' },
  h2: { color: '#1e3a8a', fontSize: 18, fontWeight: '600' },
  label: { color: '#64748b', fontSize: 11, letterSpacing: 0.5, textTransform: 'uppercase' },
  mono: { fontFamily: 'Menlo', fontSize: 11, color: '#1f2937' },
  muted: { color: '#64748b', fontSize: 13 },
  identity: { backgroundColor: '#f1f5f9', padding: 12, borderRadius: 6, gap: 4 },
  btn: { backgroundColor: '#1e3a8a', paddingHorizontal: 16, paddingVertical: 12, borderRadius: 6, alignItems: 'center' },
  btnDisabled: { opacity: 0.6 },
  btnText: { color: '#ffffff', fontWeight: '500' },
  btnOutline: { borderWidth: 1, borderColor: '#1e3a8a', paddingHorizontal: 16, paddingVertical: 12, borderRadius: 6, alignItems: 'center' },
  btnOutlineText: { color: '#1e3a8a', fontWeight: '500' },
  successCallout: { backgroundColor: '#ecfdf5', borderColor: '#059669' },
  successTitle: { color: '#059669', fontWeight: '600' },
  errorCallout: { backgroundColor: '#fef2f2', borderColor: '#dc2626' },
  errorTitle: { color: '#dc2626', fontWeight: '600' },
})
