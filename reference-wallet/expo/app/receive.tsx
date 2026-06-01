import { useCallback, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { Link, useFocusEffect } from 'expo-router'
import { OfferQrScanner, OfferQrScannerButton } from '@/components/OfferQrScanner'
import { bootstrap, storeFromClaim, type WalletState } from '@/lib/wallet'
import type { StoredCredential } from '@/lib/storage'
import { parseIncomingCredentialQr } from '@/lib/credentialOfferIngress'
import { claimCredentialOffer } from '@/lib/demoBackend'
import { isCredentialBoundToHolder } from '@/lib/holderBinding'
import { credentialSummary, theme } from '@/lib/credentialDisplay'

type Status =
  | { kind: 'idle' }
  | { kind: 'requesting' }
  | { kind: 'success'; credential: StoredCredential; replaced: boolean }
  | { kind: 'error'; message: string }

export default function ReceiveScreen() {
  const [state, setState] = useState<WalletState | null>(null)
  const [status, setStatus] = useState<Status>({ kind: 'idle' })
  const [scannerOpen, setScannerOpen] = useState(false)

  useFocusEffect(useCallback(() => {
    bootstrap()
      .then(setState)
      .catch((e) => setStatus({ kind: 'error', message: String(e) }))
  }, []))

  const claimOffer = async (raw: string) => {
    setStatus({ kind: 'requesting' })
    setScannerOpen(false)
    try {
      const parsed = parseIncomingCredentialQr(raw)
      if (!parsed) throw new Error('Unrecognized credential offer QR')
      const wallet = await bootstrap()
      setState(wallet)
      const body = await claimCredentialOffer(parsed, wallet.holder.did)
      const { credential, replaced } = await storeFromClaim({
        format: body.format,
        credential: body.credential,
        issuer: body.issuer,
        subjectDid: body.subjectDid,
        type: body.type,
        selectivelyDisclosable: body.selectivelyDisclosable,
        preview: body.preview,
      })
      if (!isCredentialBoundToHolder(credential, wallet.holder.did)) {
        throw new Error(
          'Credential was not issued to this wallet. Close the app, reopen Add, and scan the issuer QR again.',
        )
      }
      setStatus({ kind: 'success', credential, replaced })
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    }
  }

  if (!state) {
    return (
      <View style={[s.center, s.flex]}>
        <Text style={s.muted}>Opening your wallet…</Text>
      </View>
    )
  }

  return (
    <>
      <ScrollView style={[s.scroll, s.flex]} contentContainerStyle={s.container}>
        <Text style={s.heroTitle}>Add credential</Text>
        <Text style={s.heroSub}>Scan the QR code from your issuer to receive a verified credential.</Text>

        <View style={s.scanPanel}>
          <Text style={s.scanIcon}>📷</Text>
          <Text style={s.scanTitle}>Scan issuer QR code</Text>
          <Text style={s.muted}>
            Point your camera at the offer shown by the organisation that issued your credential.
          </Text>
          <OfferQrScannerButton
            onPress={() => setScannerOpen(true)}
            loading={status.kind === 'requesting'}
          />
        </View>

        <View style={s.stepsPanel}>
          <Step n={1} text="Open the offer QR from your school, employer, or issuer." />
          <Step n={2} text="Scan it here — your wallet sends a secure identity reference." />
          <Step n={3} text="The signed credential is stored in your library." last />
        </View>

        {status.kind === 'requesting' && (
          <View style={s.loadingRow}>
            <ActivityIndicator color={theme.primary} />
            <Text style={s.muted}>Receiving credential…</Text>
          </View>
        )}

        {status.kind === 'success' && (
          <View style={s.successPanel}>
            <Text style={s.successTitle}>
              {status.replaced ? 'Credential updated' : 'Credential added'}
            </Text>
            <Text style={s.muted}>
              {credentialSummary(status.credential).title}
              {status.credential.preview.subtitle ? ` — ${status.credential.preview.subtitle}` : ''}
            </Text>
            <View style={s.actionRow}>
              <Link href="/" asChild>
                <TouchableOpacity style={s.btn}><Text style={s.btnText}>View library</Text></TouchableOpacity>
              </Link>
              <Link href="/present" asChild>
                <TouchableOpacity style={s.btnOutline}><Text style={s.btnOutlineText}>Share now</Text></TouchableOpacity>
              </Link>
            </View>
          </View>
        )}

        {status.kind === 'error' && (
          <View style={s.errorPanel}>
            <Text style={s.errorTitle}>Could not add credential</Text>
            <Text style={s.muted}>{status.message}</Text>
          </View>
        )}
      </ScrollView>

      <OfferQrScanner
        visible={scannerOpen}
        onClose={() => setScannerOpen(false)}
        onScan={claimOffer}
      />
    </>
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

const s = StyleSheet.create({
  flex: { flex: 1 },
  scroll: { backgroundColor: theme.bg },
  container: { padding: 16, gap: 16 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  heroTitle: { fontSize: 26, fontWeight: '700', color: theme.primary },
  heroSub: { fontSize: 14, color: theme.textMuted, marginTop: -8 },
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
  loadingRow: { flexDirection: 'row', alignItems: 'center', gap: 10, justifyContent: 'center' },
  successPanel: {
    backgroundColor: theme.successBg,
    borderRadius: theme.radius,
    padding: 16,
    gap: 8,
    borderWidth: 1,
    borderColor: theme.success,
  },
  successTitle: { color: theme.success, fontWeight: '700', fontSize: 16 },
  errorPanel: {
    backgroundColor: '#fef2f2',
    borderRadius: theme.radius,
    padding: 16,
    gap: 6,
    borderWidth: 1,
    borderColor: '#dc2626',
  },
  errorTitle: { color: '#dc2626', fontWeight: '600' },
  actionRow: { flexDirection: 'row', gap: 10, marginTop: 8 },
  btn: {
    backgroundColor: theme.primary,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 999,
  },
  btnText: { color: '#fff', fontWeight: '600' },
  btnOutline: {
    borderWidth: 2,
    borderColor: theme.primary,
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 999,
  },
  btnOutlineText: { color: theme.primary, fontWeight: '600' },
})
