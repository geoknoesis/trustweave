import { useCallback, useEffect, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Switch, Text, TouchableOpacity, View } from 'react-native'
import { Link, useFocusEffect } from 'expo-router'
import { CredentialLibraryCard } from '@/components/CredentialLibraryCard'
import { VerifierQrScanner, VerifierQrScannerButton } from '@/components/VerifierQrScanner'
import { bootstrap, createPresentation, list, type WalletState } from '@/lib/wallet'
import type { StoredCredential } from '@/lib/storage'
import { demoBackendBaseUrl } from '@/lib/demoBackend'
import { humanClaimName, theme } from '@/lib/credentialDisplay'
import {
  credentialMatchesRequest,
  type PresentationRequestParams,
  type PresentationRequestQrPayload,
} from '@/lib/presentationRequestQr'
import { isCredentialBoundToHolder, bindingMismatchDetail } from '@/lib/holderBinding'
import {
  fetchPresentationRequestFromQr,
  submitPresentationToVerifier,
} from '@/lib/presentationClient'
import type { VerificationResponse } from '@/lib/demoBackend'

type Phase = 'scan' | 'consent' | 'done'

type Status =
  | { kind: 'idle' }
  | { kind: 'loadingRequest' }
  | { kind: 'buildingVp' }
  | { kind: 'submitting' }
  | { kind: 'done'; response: VerificationResponse }
  | { kind: 'error'; message: string }

export default function PresentScreen() {
  const [state, setState] = useState<WalletState | null>(null)
  const [creds, setCreds] = useState<StoredCredential[]>([])
  const [phase, setPhase] = useState<Phase>('scan')
  const [scannerOpen, setScannerOpen] = useState(false)
  const [verifierQr, setVerifierQr] = useState<PresentationRequestQrPayload | null>(null)
  const [request, setRequest] = useState<PresentationRequestParams | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [disclose, setDisclose] = useState<Record<string, boolean>>({})
  const [status, setStatus] = useState<Status>({ kind: 'idle' })

  useFocusEffect(useCallback(() => {
    bootstrap().then(setState)
    list().then(setCreds)
  }, []))

  useEffect(() => {
    if (!selectedId) return
    const c = creds.find((x) => x.id === selectedId)
    if (!c) return
    const init: Record<string, boolean> = {}
    for (const n of c.selectivelyDisclosable) init[n] = true
    setDisclose(init)
  }, [selectedId, creds])

  useEffect(() => {
    if (phase !== 'consent' || !state) return
    const bound = request
      ? creds.filter(
          (c) =>
            credentialMatchesRequest(c.type, request.acceptedTypes) &&
            isCredentialBoundToHolder(c, state.holder.did),
        )
      : creds.filter((c) => isCredentialBoundToHolder(c, state.holder.did))
    if (bound.length === 0) return
    if (!selectedId || !bound.some((c) => c.id === selectedId)) {
      setSelectedId(bound[0].id)
    }
  }, [phase, state, creds, request, selectedId])

  const onVerifierScanned = async (qr: PresentationRequestQrPayload) => {
    setScannerOpen(false)
    setStatus({ kind: 'loadingRequest' })
    try {
      const wallet = await bootstrap()
      setState(wallet)
      const freshCreds = await list()
      setCreds(freshCreds)
      const req = qr.presentationRequest
        ?? await fetchPresentationRequestFromQr(qr, demoBackendBaseUrl)
      const matching = freshCreds.filter(
        (c) =>
          c.format !== 'vc-ld' &&
          credentialMatchesRequest(c.type, req.acceptedTypes) &&
          isCredentialBoundToHolder(c, wallet.holder.did),
      )
      if (matching.length === 0) {
        const typeMatch = freshCreds.some((c) => credentialMatchesRequest(c.type, req.acceptedTypes))
        const mismatch = freshCreds
          .filter((c) => credentialMatchesRequest(c.type, req.acceptedTypes))
          .map((c) => bindingMismatchDetail(c, wallet.holder.did))
          .find(Boolean)
        throw new Error(
          typeMatch
            ? mismatch
              ? `${mismatch} Go to Add and scan the issuer QR again for this wallet.`
              : 'Your credentials belong to an old wallet identity. Go to Add and scan the issuer QR again.'
            : freshCreds.length === 0
              ? 'No credentials in your wallet. Go to Add and scan the issuer QR first.'
              : 'You have no credentials that match what this verifier accepts.',
        )
      }
      setVerifierQr(qr)
      setRequest(req)
      setSelectedId(matching[0].id)
      setPhase('consent')
      setStatus({ kind: 'idle' })
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  const onShare = async () => {
    let wallet: WalletState
    try {
      wallet = await bootstrap()
      setState(wallet)
    } catch (e) {
      setStatus({
        kind: 'error',
        message: e instanceof Error ? e.message : 'Wallet not ready. Go back and try again.',
      })
      return
    }
    if (!verifierQr) {
      setStatus({ kind: 'error', message: 'Scan a verifier QR first.' })
      return
    }
    if (!selectedId) {
      setStatus({ kind: 'error', message: 'Select a credential to share.' })
      return
    }
    const freshCreds = wallet.credentials.length > 0 ? wallet.credentials : await list()
    setCreds(freshCreds)
    const selected = freshCreds.find((c) => c.id === selectedId)
    if (!selected) {
      setStatus({ kind: 'error', message: 'Selected credential is no longer in your wallet.' })
      return
    }
    if (!isCredentialBoundToHolder(selected, wallet.holder.did)) {
      const detail = bindingMismatchDetail(selected, wallet.holder.did)
      setStatus({
        kind: 'error',
        message: detail
          ? `${detail} Go to Add and scan the issuer QR again.`
          : 'This credential belongs to a different wallet identity. Go to Add and scan the issuer QR again.',
      })
      return
    }
    try {
      setStatus({ kind: 'buildingVp' })
      const req = verifierQr.presentationRequest
        ?? await fetchPresentationRequestFromQr(verifierQr, demoBackendBaseUrl)
      setRequest(req)
      const discloseNames = Object.entries(disclose).filter(([, v]) => v).map(([k]) => k)
      const vp = await createPresentation([selected.id], req.audience, req.nonce, discloseNames)
      setStatus({ kind: 'submitting' })
      const response = await submitPresentationToVerifier(
        verifierQr,
        vp,
        selected.format as 'vc+jwt' | 'vc+sd-jwt',
        req.nonce,
        demoBackendBaseUrl,
      )
      setStatus({ kind: 'done', response })
      setPhase('done')
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  const resetFlow = () => {
    setPhase('scan')
    setVerifierQr(null)
    setRequest(null)
    setSelectedId(null)
    setStatus({ kind: 'idle' })
  }

  if (!state) {
    return (
      <View style={[s.center, s.flex]}>
        <Text style={s.muted}>Opening your wallet…</Text>
      </View>
    )
  }

  if (creds.length === 0) {
    return (
      <View style={[s.center, s.flex]}>
        <Text style={s.emptyIcon}>📤</Text>
        <Text style={s.emptyTitle}>Nothing to share yet</Text>
        <Text style={s.muted}>Add a credential to your library first.</Text>
        <Link href="/receive" asChild>
          <TouchableOpacity style={s.btn}><Text style={s.btnText}>Add credential</Text></TouchableOpacity>
        </Link>
      </View>
    )
  }

  const matchingCreds = request
    ? creds.filter(
        (c) =>
          c.format !== 'vc-ld' &&
          credentialMatchesRequest(c.type, request.acceptedTypes) &&
          isCredentialBoundToHolder(c, state.holder.did),
      )
    : creds.filter(
        (c) => c.format !== 'vc-ld' && isCredentialBoundToHolder(c, state.holder.did),
      )
  const selected = matchingCreds.find((c) => c.id === selectedId)
  const canShare = Boolean(selected && isCredentialBoundToHolder(selected, state.holder.did))
  const inFlight = status.kind === 'loadingRequest' || status.kind === 'buildingVp' || status.kind === 'submitting'

  if (phase === 'scan') {
    return (
      <>
        <ScrollView style={[s.scroll, s.flex]} contentContainerStyle={s.container}>
          <Text style={s.heroTitle}>Share credential</Text>
          <Text style={s.heroSub}>Scan the verifier&apos;s presentation-request QR — not the verify link from your credential.</Text>

          <View style={s.scanPanel}>
            <Text style={s.scanIcon}>📤</Text>
            <Text style={s.scanTitle}>Scan verifier QR</Text>
            <Text style={s.muted}>
              Open the verifier page (/verifier) on your PC, then scan its QR here. A credential &quot;Verify link&quot; is for others to open in a browser — use your camera app for that.
            </Text>
            <VerifierQrScannerButton
              onPress={() => setScannerOpen(true)}
              loading={status.kind === 'loadingRequest'}
            />
          </View>

          {status.kind === 'loadingRequest' && (
            <View style={s.loadingRow}>
              <ActivityIndicator color={theme.primary} />
              <Text style={s.muted}>Reading verifier request…</Text>
            </View>
          )}

          {status.kind === 'error' && (
            <View style={s.errorPanel}>
              <Text style={s.errorTitle}>
                {status.message.includes('demo server') || status.message.includes('demoBackendBaseUrl')
                  ? 'Cannot reach demo server'
                  : status.message.includes('Add') || status.message.includes('issuer')
                    ? 'Credential needed'
                    : 'Could not connect to verifier'}
              </Text>
              <Text style={s.errorMessage}>{status.message}</Text>
              {(status.message.includes('Add') || status.message.includes('issuer')) && (
                <Link href="/receive" asChild>
                  <TouchableOpacity style={[s.btnOutline, { marginTop: 10 }]}>
                    <Text style={s.btnOutlineText}>Add credential</Text>
                  </TouchableOpacity>
                </Link>
              )}
            </View>
          )}

          <View style={s.stepsPanel}>
            <Step n={1} text="Verifier shows a QR on their screen." />
            <Step n={2} text="You scan it here with your wallet." />
            <Step n={3} text="You review and approve what to share." last />
          </View>
        </ScrollView>

        <VerifierQrScanner
          visible={scannerOpen}
          onClose={() => setScannerOpen(false)}
          onScan={onVerifierScanned}
        />
      </>
    )
  }

  return (
    <ScrollView style={[s.scroll, s.flex]} contentContainerStyle={s.container}>
      <Text style={s.heroTitle}>Review & share</Text>
      <Text style={s.heroSub}>Choose what to disclose to the verifier.</Text>

      {request && (
        <View style={s.requestPanel}>
          <Text style={s.requestTitle}>Verifier request</Text>
          <Text style={s.muted}>Accepts: {request.acceptedTypes.join(', ') || 'any credential'}</Text>
        </View>
      )}

      <Text style={s.sectionLabel}>SELECT CREDENTIAL</Text>
      {matchingCreds.map((c) => (
        <View key={c.id} style={selectedId === c.id ? s.selectedWrap : undefined}>
          <CredentialLibraryCard cred={c} onPress={() => setSelectedId(c.id)} />
        </View>
      ))}

      {selected && selected.format === 'vc+sd-jwt' && selected.selectivelyDisclosable.length > 0 && (
        <View style={s.sharePanel}>
          <Text style={s.sectionLabel}>CHOOSE WHAT TO SHARE</Text>
          {selected.selectivelyDisclosable.map((name) => (
            <View key={name} style={s.shareRow}>
              <Switch
                value={disclose[name] ?? false}
                onValueChange={(v) => setDisclose((prev) => ({ ...prev, [name]: v }))}
                trackColor={{ true: theme.success }}
              />
              <Text style={s.shareLabel}>{humanClaimName(name)}</Text>
            </View>
          ))}
        </View>
      )}

      {matchingCreds.length === 0 && request && (
        <View style={s.warnPanel}>
          <Text style={s.warnTitle}>No matching credentials</Text>
          <Text style={s.errorMessage}>
            Delete old credentials and scan the issuer QR again to receive a fresh copy for this wallet.
          </Text>
        </View>
      )}

      {status.kind === 'error' && (
        <View style={s.errorPanel}>
          <Text style={s.errorTitle}>Sharing failed</Text>
          <Text style={s.errorMessage}>{status.message}</Text>
        </View>
      )}

      <TouchableOpacity
        style={[s.btn, (inFlight || !canShare) && s.btnDisabled]}
        onPress={onShare}
        disabled={inFlight || !canShare}
      >
        {inFlight ? (
          <View style={s.loadingRow}>
            <ActivityIndicator color="#fff" />
            <Text style={s.btnText}>{statusLabel(status)}</Text>
          </View>
        ) : (
          <Text style={s.btnText}>Share with verifier</Text>
        )}
      </TouchableOpacity>

      <TouchableOpacity style={s.btnOutline} onPress={resetFlow} disabled={inFlight}>
        <Text style={s.btnOutlineText}>Scan another QR</Text>
      </TouchableOpacity>

      {phase === 'done' && status.kind === 'done' && (
        <VerificationResultPanel response={status.response} onDone={resetFlow} />
      )}
    </ScrollView>
  )
}

function Step({ n, text, last }: { n: number; text: string; last?: boolean }) {
  return (
    <View style={[s.stepRow, !last && s.stepBorder]}>
      <View style={s.stepNum}><Text style={s.stepNumText}>{n}</Text></View>
      <Text style={s.stepText}>{text}</Text>
    </View>
  )
}

function VerificationResultPanel({
  response,
  onDone,
}: {
  response: VerificationResponse
  onDone: () => void
}) {
  return (
    <View style={s.resultPanel}>
      <Text style={s.sectionLabel}>VERIFICATION RESULT</Text>
      <View style={[s.summary, response.valid ? s.successPanel : s.errorPanel]}>
        <Text style={response.valid ? s.successTitle : s.errorTitle}>
          {response.valid ? '✓ Credential verified' : '✗ Verification failed'}
        </Text>
      </View>
      {response.checks.map((c, i) => (
        <View key={i} style={[s.checkRow, c.passed ? s.checkPass : s.checkFail]}>
          <Text style={{ color: c.passed ? theme.success : '#dc2626', fontWeight: '700' }}>
            {c.passed ? '✓' : '✗'}
          </Text>
          <Text style={s.checkStep}>{c.step}</Text>
        </View>
      ))}
      <TouchableOpacity style={s.btnOutline} onPress={onDone}>
        <Text style={s.btnOutlineText}>Done</Text>
      </TouchableOpacity>
    </View>
  )
}

function statusLabel(s: Status): string {
  switch (s.kind) {
    case 'loadingRequest': return 'Reading request…'
    case 'buildingVp': return 'Preparing…'
    case 'submitting': return 'Sending…'
    default: return ''
  }
}

const s = StyleSheet.create({
  flex: { flex: 1 },
  scroll: { backgroundColor: theme.bg },
  container: { padding: 16, gap: 12, paddingBottom: 32 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 10 },
  heroTitle: { fontSize: 26, fontWeight: '700', color: theme.primary },
  heroSub: { fontSize: 14, color: theme.textMuted, marginBottom: 4 },
  scanPanel: {
    backgroundColor: theme.surface,
    borderRadius: theme.radius,
    padding: 20,
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    borderColor: theme.border,
    ...theme.shadow,
  },
  scanIcon: { fontSize: 36 },
  scanTitle: { fontSize: 16, fontWeight: '600', color: theme.text },
  muted: { color: theme.textMuted, fontSize: 14, textAlign: 'center' },
  stepsPanel: {
    backgroundColor: theme.surface,
    borderRadius: theme.radius,
    padding: 4,
    borderWidth: 1,
    borderColor: theme.border,
  },
  stepRow: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12 },
  stepBorder: { borderBottomWidth: 1, borderBottomColor: theme.border },
  stepNum: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: theme.primary,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepNumText: { color: '#fff', fontWeight: '700', fontSize: 13 },
  stepText: { flex: 1, fontSize: 14, color: theme.textMuted },
  sectionLabel: { fontSize: 11, fontWeight: '600', color: theme.textMuted, letterSpacing: 0.5, marginTop: 8 },
  requestPanel: {
    backgroundColor: '#eff6ff',
    borderRadius: theme.radius,
    padding: 14,
    gap: 4,
    borderWidth: 1,
    borderColor: theme.primary,
  },
  requestTitle: { fontWeight: '600', color: theme.primary },
  selectedWrap: { borderRadius: theme.radius, borderWidth: 2, borderColor: theme.primary },
  sharePanel: {
    backgroundColor: theme.surface,
    borderRadius: theme.radius,
    padding: 16,
    gap: 10,
    borderWidth: 1,
    borderColor: theme.border,
  },
  shareRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 4 },
  shareLabel: { fontSize: 15, color: theme.text, flex: 1 },
  btn: {
    backgroundColor: theme.primary,
    paddingVertical: 14,
    borderRadius: 999,
    alignItems: 'center',
    marginTop: 8,
  },
  btnDisabled: { opacity: 0.7 },
  btnText: { color: '#fff', fontWeight: '600', fontSize: 16 },
  btnOutline: {
    borderWidth: 2,
    borderColor: theme.primary,
    paddingVertical: 12,
    borderRadius: 999,
    alignItems: 'center',
    marginTop: 8,
  },
  btnOutlineText: { color: theme.primary, fontWeight: '600' },
  loadingRow: { flexDirection: 'row', alignItems: 'center', gap: 10, justifyContent: 'center' },
  emptyIcon: { fontSize: 40 },
  emptyTitle: { fontSize: 17, fontWeight: '600', color: theme.primary },
  errorPanel: { backgroundColor: '#fef2f2', padding: 14, borderRadius: theme.radius, gap: 4 },
  warnPanel: { backgroundColor: '#fffbeb', padding: 14, borderRadius: theme.radius, gap: 4, borderWidth: 1, borderColor: '#f59e0b' },
  warnTitle: { color: '#b45309', fontWeight: '600' },
  errorTitle: { color: '#dc2626', fontWeight: '600' },
  errorMessage: { color: '#991b1b', fontSize: 14, lineHeight: 20 },
  resultPanel: { gap: 10, marginTop: 8 },
  summary: { padding: 14, borderRadius: theme.radius },
  successPanel: { backgroundColor: theme.successBg },
  successTitle: { color: theme.success, fontWeight: '700' },
  checkRow: { flexDirection: 'row', gap: 10, padding: 10, borderRadius: 8 },
  checkPass: { backgroundColor: theme.successBg },
  checkFail: { backgroundColor: '#fef2f2' },
  checkStep: { fontSize: 14, color: theme.text, flex: 1 },
})
