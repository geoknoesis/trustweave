import { useCallback, useEffect, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Switch, Text, TouchableOpacity, View } from 'react-native'
import { Link, useFocusEffect } from 'expo-router'
import { bootstrap, createPresentation, list, type WalletState } from '@/lib/wallet'
import type { StoredCredential } from '@/lib/storage'
import { fetchPresentationRequest, verify, type VerificationResponse } from '@/lib/demoBackend'

type Status =
  | { kind: 'idle' }
  | { kind: 'fetchingRequest' }
  | { kind: 'buildingVp' }
  | { kind: 'verifying' }
  | { kind: 'done'; response: VerificationResponse }
  | { kind: 'error'; message: string }

export default function PresentScreen() {
  const [state, setState] = useState<WalletState | null>(null)
  const [creds, setCreds] = useState<StoredCredential[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [disclose, setDisclose] = useState<Record<string, boolean>>({})
  const [status, setStatus] = useState<Status>({ kind: 'idle' })

  useEffect(() => { bootstrap().then(setState) }, [])

  useFocusEffect(useCallback(() => {
    list().then((all) => {
      setCreds(all)
      if (all.length > 0 && !selectedId) setSelectedId(all[0].id)
    })
  }, [selectedId]))

  // When selected credential changes, default-disclose all of its disclosable claims.
  useEffect(() => {
    if (!selectedId) return
    const c = creds.find((x) => x.id === selectedId)
    if (!c) return
    const init: Record<string, boolean> = {}
    for (const n of c.selectivelyDisclosable) init[n] = true
    setDisclose(init)
  }, [selectedId, creds])

  if (!state) return <View style={s.center}><Text>Initialising wallet…</Text></View>

  if (creds.length === 0) {
    return (
      <View style={s.center}>
        <Text style={s.emptyIcon}>📭</Text>
        <Text style={s.muted}>No credentials to present.</Text>
        <Link href="/receive" asChild>
          <TouchableOpacity style={s.btn}><Text style={s.btnText}>Receive one first</Text></TouchableOpacity>
        </Link>
      </View>
    )
  }

  const selected = creds.find((c) => c.id === selectedId)
  const isSdJwt = selected?.format === 'vc+sd-jwt'

  const onPresent = async () => {
    if (!selected) return
    try {
      setStatus({ kind: 'fetchingRequest' })
      const req = await fetchPresentationRequest()
      setStatus({ kind: 'buildingVp' })
      const discloseNames = Object.entries(disclose).filter(([, v]) => v).map(([k]) => k)
      const vp = await createPresentation([selected.id], req.audience, req.nonce, discloseNames)
      setStatus({ kind: 'verifying' })
      const response = await verify(vp, selected.format, req.nonce)
      setStatus({ kind: 'done', response })
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  const inFlight = status.kind === 'fetchingRequest' || status.kind === 'buildingVp' || status.kind === 'verifying'

  return (
    <ScrollView style={s.scroll} contentContainerStyle={s.container}>
      <View style={s.panel}>
        <Text style={s.h2}>Present a credential</Text>
        <Text style={s.muted}>
          The wallet builds a Verifiable Presentation containing the selected credential, signed by
          your holder DID, and sends it to the demo verifier.
        </Text>
        <Text style={s.h3}>Choose a credential</Text>
        {creds.map((c) => (
          <TouchableOpacity
            key={c.id}
            style={[s.credCard, selectedId === c.id && s.credCardSelected]}
            onPress={() => setSelectedId(c.id)}
          >
            <Text style={s.radio}>{selectedId === c.id ? '◉' : '○'}</Text>
            <View style={{ flex: 1 }}>
              <Text style={s.credTitle}>{c.preview.title} <Text style={s.credFormat}>({c.format})</Text></Text>
              {c.preview.subtitle && <Text style={s.credSubtitle}>{c.preview.subtitle}</Text>}
            </View>
          </TouchableOpacity>
        ))}
      </View>

      {isSdJwt && selected!.selectivelyDisclosable.length > 0 && (
        <View style={s.panel}>
          <Text style={s.h3}>Disclose to verifier</Text>
          <Text style={s.muted}>
            Tick the claims you're willing to reveal. Unticked claims stay hashed inside the
            credential — the verifier sees only that the issuer signed something, not what.
          </Text>
          <View style={s.discloseBox}>
            {selected!.selectivelyDisclosable.map((name) => (
              <View key={name} style={s.discloseRow}>
                <Switch
                  value={disclose[name] ?? false}
                  onValueChange={(v) => setDisclose((prev) => ({ ...prev, [name]: v }))}
                />
                <Text style={[s.mono, { marginLeft: 12, color: disclose[name] ? '#059669' : '#64748b' }]}>
                  {name}
                </Text>
              </View>
            ))}
          </View>
        </View>
      )}

      <View style={s.panel}>
        <TouchableOpacity style={[s.btn, inFlight && s.btnDisabled]} onPress={onPresent} disabled={inFlight}>
          {inFlight ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
              <ActivityIndicator color="#fff" />
              <Text style={s.btnText}>{statusLabel(status)}</Text>
            </View>
          ) : (
            <Text style={s.btnText}>Present to demo verifier</Text>
          )}
        </TouchableOpacity>
        {status.kind === 'error' && (
          <View style={s.errorCallout}>
            <Text style={s.errorTitle}>Failed</Text>
            <Text>{status.message}</Text>
          </View>
        )}
      </View>

      {status.kind === 'done' && <VerificationResultPanel response={status.response} />}
    </ScrollView>
  )
}

function VerificationResultPanel({ response }: { response: VerificationResponse }) {
  return (
    <View style={s.panel}>
      <Text style={s.h2}>Verification result</Text>
      <View style={[s.summary, response.valid ? s.successCallout : s.errorCallout]}>
        <Text style={response.valid ? s.successTitle : s.errorTitle}>
          {response.valid ? '✓ Presentation verified.' : '✗ Verification failed.'}
        </Text>
      </View>
      <Text style={s.h3}>Checklist</Text>
      {response.checks.map((c, i) => (
        <View key={i} style={[s.checkRow, c.passed ? s.checkRowPass : s.checkRowFail]}>
          <Text style={[s.checkMark, c.passed ? { color: '#059669' } : { color: '#dc2626' }]}>
            {c.passed ? '✓' : '✗'}
          </Text>
          <View style={{ flex: 1 }}>
            <Text>{c.step}</Text>
            {c.detail && <Text style={s.detail}>{c.detail}</Text>}
          </View>
        </View>
      ))}
      {response.credentials && response.credentials.length > 0 && (
        <>
          <Text style={s.h3}>What the verifier saw</Text>
          {response.credentials.map((cred, i) => (
            <View key={i} style={s.discloseBlock}>
              <Text style={[s.mono, { color: '#1e3a8a', fontWeight: '600' }]}>type: {cred.type.join(', ')}</Text>
              {Object.entries(cred.disclosedClaims).map(([k, v]) => (
                <Text key={k} style={s.mono}>
                  {k}: {typeof v === 'object' ? JSON.stringify(v) : String(v)}
                </Text>
              ))}
              {cred.withheldClaimNames && cred.withheldClaimNames.length > 0 && (
                <View style={s.withheldBlock}>
                  <Text style={[s.mono, { color: '#64748b' }]}>withheld: {cred.withheldClaimNames.join(', ')}</Text>
                </View>
              )}
            </View>
          ))}
        </>
      )}
    </View>
  )
}

function statusLabel(s: Status): string {
  switch (s.kind) {
    case 'fetchingRequest': return 'Fetching verifier request…'
    case 'buildingVp': return 'Building presentation…'
    case 'verifying': return 'Verifying…'
    default: return ''
  }
}

const s = StyleSheet.create({
  scroll: { backgroundColor: '#f8fafc' },
  container: { padding: 16, gap: 16 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 8 },
  panel: { backgroundColor: '#ffffff', borderRadius: 8, padding: 16, gap: 12, borderWidth: 1, borderColor: '#e2e8f0' },
  h2: { color: '#1e3a8a', fontSize: 18, fontWeight: '600' },
  h3: { color: '#1e3a8a', fontSize: 14, fontWeight: '600' },
  muted: { color: '#64748b', fontSize: 13 },
  mono: { fontFamily: 'Menlo', fontSize: 12 },
  emptyIcon: { fontSize: 32 },
  credCard: { flexDirection: 'row', alignItems: 'center', backgroundColor: '#f1f5f9', padding: 12, borderRadius: 6, gap: 12 },
  credCardSelected: { borderWidth: 2, borderColor: '#1e3a8a' },
  radio: { fontSize: 18, color: '#1e3a8a' },
  credTitle: { color: '#1e3a8a', fontWeight: '600' },
  credFormat: { fontSize: 10, color: '#64748b', fontWeight: '400' },
  credSubtitle: { color: '#1f2937', fontSize: 13 },
  discloseBox: { backgroundColor: '#f1f5f9', padding: 12, borderRadius: 6, gap: 4 },
  discloseRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 2 },
  btn: { backgroundColor: '#1e3a8a', paddingHorizontal: 16, paddingVertical: 12, borderRadius: 6, alignItems: 'center' },
  btnDisabled: { opacity: 0.6 },
  btnText: { color: '#ffffff', fontWeight: '500' },
  summary: { padding: 12, borderRadius: 6, borderLeftWidth: 4 },
  successCallout: { backgroundColor: '#ecfdf5', borderLeftColor: '#059669' },
  successTitle: { color: '#059669', fontWeight: '700' },
  errorCallout: { backgroundColor: '#fef2f2', borderLeftColor: '#dc2626' },
  errorTitle: { color: '#dc2626', fontWeight: '700' },
  checkRow: { flexDirection: 'row', alignItems: 'flex-start', padding: 8, borderRadius: 4, gap: 8 },
  checkRowPass: { backgroundColor: '#ecfdf5' },
  checkRowFail: { backgroundColor: '#fef2f2' },
  checkMark: { fontWeight: '700', fontSize: 16 },
  detail: { color: '#64748b', fontSize: 11, fontFamily: 'Menlo', marginTop: 2 },
  discloseBlock: { backgroundColor: '#f1f5f9', padding: 12, borderRadius: 6, gap: 4 },
  withheldBlock: { marginTop: 6, paddingTop: 6, borderTopWidth: 1, borderTopColor: '#cbd5e1', borderStyle: 'dashed' },
})
