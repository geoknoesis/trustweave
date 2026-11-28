import { useCallback, useState } from 'react'
import { ActivityIndicator, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { useFocusEffect, useRouter } from 'expo-router'
import { Ionicons } from '@expo/vector-icons'
import { IssuerTrustSheet } from '@/components/IssuerTrustSheet'
import { OfferQrScanner, OfferQrScannerButton } from '@/components/OfferQrScanner'
import {
  bootstrap,
  storeFromClaim,
  UntrustedIssuerError,
  type StoredCredential,
  type WalletState,
} from '@/lib/wallet'
import { parseIncomingCredentialQr, type WalletClaimResponse } from '@/lib/credentialOfferIngress'
import { claimCredentialOffer, fetchBackendIssuers } from '@/lib/demoBackend'
import { isCredentialBoundToHolder } from '@/lib/holderBinding'
import { credentialSummary, useWalletTheme } from '@/lib/credentialDisplay'
import { consumePendingOfferRaw, setPendingCredentialIdToShare } from '@/lib/pendingScanStore'

type Status =
  | { kind: 'idle' }
  | { kind: 'requesting' }
  | {
      kind: 'confirmIssuer'
      issuerDid: string
      credentialTitle?: string
      claim: WalletClaimResponse
      backendIssuers: string[]
      holderDid: string
    }
  | { kind: 'success'; credential: StoredCredential; replaced: boolean }
  | { kind: 'error'; message: string }

export default function ReceiveScreen() {
  const t = useWalletTheme()
  const router = useRouter()
  const [state, setState] = useState<WalletState | null>(null)
  const [status, setStatus] = useState<Status>({ kind: 'idle' })
  const [scannerOpen, setScannerOpen] = useState(false)
  const [confirming, setConfirming] = useState(false)

  const attemptStore = useCallback(
    async (
      claim: WalletClaimResponse,
      holderDid: string,
      backendIssuers: string[],
      confirmedIssuer: string | null,
    ) => {
      const { credential, replaced } = await storeFromClaim(
        {
          format: claim.format,
          credential: claim.credential,
          issuer: claim.issuer,
          subjectDid: claim.subjectDid,
          type: claim.type,
          selectivelyDisclosable: claim.selectivelyDisclosable,
          preview: claim.preview,
        },
        {
          backendIssuers,
          confirmedIssuer,
        },
      )
      if (!isCredentialBoundToHolder(credential, holderDid)) {
        throw new Error(
          'This credential was issued to a different wallet. Ask the issuer for a new QR code and scan it again.',
        )
      }
      setStatus({ kind: 'success', credential, replaced })
    },
    [],
  )

  const claimOffer = useCallback(
    async (raw: string) => {
      setStatus({ kind: 'requesting' })
      setScannerOpen(false)
      try {
        const parsed = parseIncomingCredentialQr(raw)
        if (!parsed) throw new Error('This QR code is not a recognized credential offer.')
        const wallet = await bootstrap()
        setState(wallet)
        const [body, backendIssuers] = await Promise.all([
          claimCredentialOffer(parsed, wallet.holder.did),
          fetchBackendIssuers(),
        ])
        try {
          await attemptStore(body, wallet.holder.did, backendIssuers, null)
        } catch (err) {
          if (err instanceof UntrustedIssuerError) {
            setStatus({
              kind: 'confirmIssuer',
              issuerDid: err.issuerDid,
              credentialTitle: err.credentialTitle ?? body.preview?.title,
              claim: body,
              backendIssuers,
              holderDid: wallet.holder.did,
            })
            return
          }
          throw err
        }
      } catch (e) {
        setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
      }
    },
    [attemptStore],
  )

  useFocusEffect(
    useCallback(() => {
      bootstrap()
        .then((wallet) => {
          setState(wallet)
          const pending = consumePendingOfferRaw()
          if (pending) {
            void claimOffer(pending)
          }
        })
        .catch((e) => setStatus({ kind: 'error', message: String(e) }))
    }, [claimOffer]),
  )

  const handleConfirmIssuer = async () => {
    if (status.kind !== 'confirmIssuer') return
    setConfirming(true)
    try {
      await attemptStore(status.claim, status.holderDid, status.backendIssuers, status.issuerDid)
    } catch (e) {
      setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
    } finally {
      setConfirming(false)
    }
  }

  if (!state) {
    return (
      <View style={[s.center, s.flex, { backgroundColor: t.bg }]}>
        <ActivityIndicator color={t.primary} />
        <Text style={[s.muted, { color: t.textMuted }]}>Opening your wallet…</Text>
      </View>
    )
  }

  return (
    <>
      <ScrollView style={[s.flex, { backgroundColor: t.bg }]} contentContainerStyle={s.container}>
        {status.kind === 'success' ? (
          <View style={[s.outcomeCard, { backgroundColor: t.surface, borderColor: t.border }, t.shadow]}>
            <View style={[s.outcomeIcon, { backgroundColor: t.successBg }]}>
              <Ionicons name="checkmark-circle" size={44} color={t.success} />
            </View>
            <Text style={[s.outcomeTitle, { color: t.text }]}>
              {status.replaced ? 'Credential updated' : 'Credential added'}
            </Text>
            <Text style={[s.outcomeSub, { color: t.textMuted }]}>
              {credentialSummary(status.credential).title}
              {status.credential.preview.subtitle ? ` · ${status.credential.preview.subtitle}` : ''}
            </Text>
            <View style={s.actionCol}>
              <TouchableOpacity
                style={[s.primaryBtn, { backgroundColor: t.primary }]}
                onPress={() => {
                  setStatus({ kind: 'idle' })
                  router.replace('/')
                }}
                accessibilityRole="button"
              >
                <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>View in wallet</Text>
              </TouchableOpacity>
              {status.credential.format !== 'vc-ld' ? (
                <TouchableOpacity
                  style={[s.outlineBtn, { borderColor: t.primary }]}
                  onPress={() => {
                    setPendingCredentialIdToShare(status.credential.id)
                    setStatus({ kind: 'idle' })
                    router.replace('/present')
                  }}
                  accessibilityRole="button"
                >
                  <Text style={[s.outlineBtnText, { color: t.primary }]}>Share now</Text>
                </TouchableOpacity>
              ) : null}
            </View>
          </View>
        ) : (
          <>
            <Text style={[s.heroSub, { color: t.textMuted, marginTop: 0 }]}>
              Scan the QR code from your issuer to verify and save your credential.
            </Text>

            <View style={[s.scanPanel, { backgroundColor: t.surface, borderColor: t.border }, t.shadow]}>
              <View style={[s.iconCircle, { backgroundColor: t.infoBg }]}>
                <Ionicons name="qr-code-outline" size={34} color={t.primary} />
              </View>
              <Text style={[s.scanTitle, { color: t.text }]}>Scan issuer QR code</Text>
              <Text style={[s.muted, { color: t.textMuted }]}>
                Point your camera at the offer shown by the organization issuing your credential.
              </Text>
              <OfferQrScannerButton
                onPress={() => setScannerOpen(true)}
                loading={status.kind === 'requesting'}
              />
            </View>

            {status.kind === 'requesting' ? (
              <View style={s.loadingRow}>
                <ActivityIndicator color={t.primary} />
                <Text style={[s.muted, { color: t.textMuted }]}>Verifying and saving credential…</Text>
              </View>
            ) : null}

            {status.kind === 'error' ? (
              <View style={[s.errorPanel, { backgroundColor: t.dangerBg, borderColor: t.danger }]}>
                <View style={s.errorHead}>
                  <Ionicons name="alert-circle-outline" size={20} color={t.danger} />
                  <Text style={[s.errorTitle, { color: t.danger }]}>Could not add credential</Text>
                </View>
                <Text style={[s.errorBody, { color: t.text }]}>{status.message}</Text>
                <TouchableOpacity
                  style={[s.retryBtn, { borderColor: t.danger }]}
                  onPress={() => {
                    setStatus({ kind: 'idle' })
                    setScannerOpen(true)
                  }}
                  accessibilityRole="button"
                >
                  <Text style={[s.retryBtnText, { color: t.danger }]}>Scan again</Text>
                </TouchableOpacity>
              </View>
            ) : null}

            <View style={[s.stepsPanel, { backgroundColor: t.surface, borderColor: t.border }]}>
              <Step n={1} text="Open the offer QR from your school, employer, or issuer." t={t} />
              <Step n={2} text="Scan it here to verify the issuer signature." t={t} />
              <Step n={3} text="Your credential is saved privately on this device." t={t} last />
            </View>
          </>
        )}
      </ScrollView>

      <OfferQrScanner
        visible={scannerOpen}
        onClose={() => setScannerOpen(false)}
        onScan={claimOffer}
      />

      <IssuerTrustSheet
        visible={status.kind === 'confirmIssuer'}
        issuerDid={status.kind === 'confirmIssuer' ? status.issuerDid : ''}
        credentialTitle={status.kind === 'confirmIssuer' ? status.credentialTitle : undefined}
        busy={confirming}
        onConfirm={handleConfirmIssuer}
        onCancel={() => setStatus({ kind: 'idle' })}
      />
    </>
  )
}

function Step({
  n,
  text,
  t,
  last,
}: {
  n: number
  text: string
  t: ReturnType<typeof useWalletTheme>
  last?: boolean
}) {
  return (
    <View style={[s.stepRow, !last && { borderBottomWidth: 1, borderBottomColor: t.border }]}>
      <View style={[s.stepNum, { backgroundColor: t.primary }]}>
        <Text style={[s.stepNumText, { color: t.onPrimary }]}>{n}</Text>
      </View>
      <Text style={[s.stepText, { color: t.textMuted }]}>{text}</Text>
    </View>
  )
}

const s = StyleSheet.create({
  flex: { flex: 1 },
  container: { padding: 16, gap: 16, paddingBottom: 32 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 10 },
  heroTitle: { fontSize: 28, fontWeight: '800', letterSpacing: -0.3 },
  heroSub: { fontSize: 14, lineHeight: 20, marginTop: -8 },
  scanPanel: {
    borderRadius: 16,
    padding: 22,
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
  },
  iconCircle: {
    width: 68,
    height: 68,
    borderRadius: 34,
    alignItems: 'center',
    justifyContent: 'center',
  },
  scanTitle: { fontSize: 17, fontWeight: '700' },
  muted: { fontSize: 14, textAlign: 'center', lineHeight: 20 },
  stepsPanel: { borderRadius: 14, padding: 4, borderWidth: 1 },
  stepRow: { flexDirection: 'row', alignItems: 'center', gap: 12, padding: 12 },
  stepNum: {
    width: 26,
    height: 26,
    borderRadius: 13,
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepNumText: { fontWeight: '700', fontSize: 13 },
  stepText: { flex: 1, fontSize: 14, lineHeight: 19 },
  loadingRow: { flexDirection: 'row', alignItems: 'center', gap: 10, justifyContent: 'center' },
  outcomeCard: {
    borderRadius: 18,
    padding: 24,
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    marginTop: 12,
  },
  outcomeIcon: {
    width: 76,
    height: 76,
    borderRadius: 38,
    alignItems: 'center',
    justifyContent: 'center',
  },
  outcomeTitle: { fontSize: 22, fontWeight: '800' },
  outcomeSub: { fontSize: 15, textAlign: 'center' },
  actionCol: { width: '100%', gap: 10, marginTop: 12 },
  primaryBtn: {
    minHeight: 50,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primaryBtnText: { fontSize: 15, fontWeight: '700' },
  outlineBtn: {
    minHeight: 48,
    borderRadius: 999,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  outlineBtnText: { fontSize: 15, fontWeight: '700' },
  errorPanel: { borderRadius: 14, padding: 16, gap: 8, borderWidth: 1 },
  errorHead: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  errorTitle: { fontWeight: '700', fontSize: 15 },
  errorBody: { fontSize: 14, lineHeight: 20 },
  retryBtn: {
    alignSelf: 'flex-start',
    borderWidth: 1.5,
    borderRadius: 999,
    paddingHorizontal: 16,
    paddingVertical: 8,
    marginTop: 4,
    minHeight: 40,
    justifyContent: 'center',
  },
  retryBtnText: { fontSize: 14, fontWeight: '700' },
})
