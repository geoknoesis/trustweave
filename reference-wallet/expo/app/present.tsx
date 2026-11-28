import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TouchableOpacity,
  View,
} from 'react-native'
import { useFocusEffect, useRouter } from 'expo-router'
import { Ionicons } from '@expo/vector-icons'
import { CredentialLibraryCard } from '@/components/CredentialLibraryCard'
import { VerifierQrScanner, VerifierQrScannerButton } from '@/components/VerifierQrScanner'
import { NfcVerifierScanButton } from '@/components/NfcVerifierScanButton'
import { confirmSensitiveAction } from '@/lib/biometrics'
import { haptics } from '@/lib/haptics'
import {
  bootstrap,
  createPresentation,
  getPreferences,
  list,
  recordWalletActivity,
  type StoredCredential,
  type WalletPreferences,
  type WalletState,
} from '@/lib/wallet'
import {
  demoBackendBaseUrl,
  fetchBackendIssuers,
  type VerificationResponse,
} from '@/lib/demoBackend'
import {
  credentialTrustBadge,
  extractShareableClaimDetails,
  useWalletTheme,
} from '@/lib/credentialDisplay'
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
import {
  consumePendingCredentialIdToShare,
  consumePendingPresentationQr,
} from '@/lib/pendingScanStore'

type Phase = 'scan' | 'consent' | 'done'

type Status =
  | { kind: 'idle' }
  | { kind: 'loadingRequest' }
  | { kind: 'buildingVp' }
  | { kind: 'submitting' }
  | { kind: 'done'; response: VerificationResponse; sharedCount: number; totalCount: number }
  | { kind: 'error'; message: string }

function formatRequesterLabel(qr: PresentationRequestQrPayload | null, req: PresentationRequestParams | null): string {
  if (req?.verifier && !req.verifier.startsWith('did:')) return req.verifier
  if (qr?.verifierUrl) {
    try {
      return new URL(qr.verifierUrl).host
    } catch {
      return qr.verifierUrl
    }
  }
  return 'Requesting organization'
}

export default function PresentScreen() {
  const t = useWalletTheme()
  const router = useRouter()
  const [state, setState] = useState<WalletState | null>(null)
  const [prefs, setPrefs] = useState<WalletPreferences | null>(null)
  const [creds, setCreds] = useState<StoredCredential[]>([])
  const [phase, setPhase] = useState<Phase>('scan')
  const [scannerOpen, setScannerOpen] = useState(false)
  const [verifierQr, setVerifierQr] = useState<PresentationRequestQrPayload | null>(null)
  const [request, setRequest] = useState<PresentationRequestParams | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [preferredId, setPreferredId] = useState<string | null>(null)
  const [disclose, setDisclose] = useState<Record<string, boolean>>({})
  const [status, setStatus] = useState<Status>({ kind: 'idle' })
  const [showTechChecks, setShowTechChecks] = useState(false)

  const onVerifierScanned = useCallback(
    async (qr: PresentationRequestQrPayload, preferredCredId?: string | null) => {
      setScannerOpen(false)
      setStatus({ kind: 'loadingRequest' })
      try {
        const wallet = await bootstrap()
        setState(wallet)
        const freshCreds = await list()
        setCreds(freshCreds)
        const req =
          qr.presentationRequest ?? (await fetchPresentationRequestFromQr(qr, demoBackendBaseUrl))
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
                ? `${mismatch} Scan the issuer QR again to add a fresh copy for this wallet.`
                : 'Your matching credential belongs to an older wallet identity. Ask the issuer for a new QR code.'
              : freshCreds.length === 0
                ? 'You have no credentials in your wallet yet. Scan an issuer QR to add one first.'
                : 'None of your stored credentials match what this requester accepts.',
          )
        }
        const chosen =
          preferredCredId && matching.some((c) => c.id === preferredCredId)
            ? preferredCredId
            : matching[0].id
        setVerifierQr(qr)
        setRequest(req)
        setSelectedId(chosen)
        setShowTechChecks(false)
        setPhase('consent')
        setStatus({ kind: 'idle' })
      } catch (e) {
        setStatus({ kind: 'error', message: e instanceof Error ? e.message : String(e) })
      }
    },
    [],
  )

  useFocusEffect(
    useCallback(() => {
      Promise.all([bootstrap(), list(), getPreferences()]).then(([w, c, p]) => {
        setState(w)
        setCreds(c)
        setPrefs(p)
        const prefId = consumePendingCredentialIdToShare()
        if (prefId) setPreferredId(prefId)
        const pendingQr = consumePendingPresentationQr()
        if (pendingQr) {
          void onVerifierScanned(pendingQr, prefId)
        }
      })
    }, [onVerifierScanned]),
  )

  // Privacy-first default: start optional selectively disclosable claims OFF so users consciously choose what to share.
  useEffect(() => {
    if (!selectedId) return
    const c = creds.find((x) => x.id === selectedId)
    if (!c) return
    const init: Record<string, boolean> = {}
    for (const n of c.selectivelyDisclosable) init[n] = false
    setDisclose(init)
  }, [selectedId, creds])

  const matchingCreds = useMemo(() => {
    if (!state) return []
    return request
      ? creds.filter(
          (c) =>
            c.format !== 'vc-ld' &&
            credentialMatchesRequest(c.type, request.acceptedTypes) &&
            isCredentialBoundToHolder(c, state.holder.did),
        )
      : creds.filter((c) => c.format !== 'vc-ld' && isCredentialBoundToHolder(c, state.holder.did))
  }, [creds, request, state])

  useEffect(() => {
    if (phase !== 'consent' || matchingCreds.length === 0) return
    if (!selectedId || !matchingCreds.some((c) => c.id === selectedId)) {
      const target =
        preferredId && matchingCreds.some((c) => c.id === preferredId)
          ? preferredId
          : matchingCreds[0].id
      setSelectedId(target)
    }
  }, [phase, matchingCreds, selectedId, preferredId])

  const selected = matchingCreds.find((c) => c.id === selectedId)
  const selectedExpired = selected ? credentialTrustBadge(selected).label === 'Expired' : false
  const shareableClaims = useMemo(
    () => (selected && selected.format === 'vc+sd-jwt' ? extractShareableClaimDetails(selected) : []),
    [selected],
  )
  const sharedCount = useMemo(
    () => shareableClaims.filter((c) => disclose[c.name]).length,
    [shareableClaims, disclose],
  )
  const totalCount = shareableClaims.length
  const hiddenCount = totalCount - sharedCount
  const requesterName = formatRequesterLabel(verifierQr, request)

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
      setStatus({ kind: 'error', message: 'Scan a verification QR code first.' })
      return
    }
    if (!selectedId) {
      setStatus({ kind: 'error', message: 'Select a credential to share.' })
      return
    }
    const freshCreds = wallet.credentials.length > 0 ? wallet.credentials : await list()
    setCreds(freshCreds)
    const chosen = freshCreds.find((c) => c.id === selectedId)
    if (!chosen) {
      setStatus({ kind: 'error', message: 'The selected credential is no longer in your wallet.' })
      return
    }
    if (!isCredentialBoundToHolder(chosen, wallet.holder.did)) {
      setStatus({
        kind: 'error',
        message: 'This credential belongs to a different wallet identity. Ask the issuer for a fresh copy.',
      })
      return
    }

    // Explicit biometric / device confirmation when protection is enabled.
    if (prefs?.biometricLockEnabled) {
      const detailSummary =
        totalCount > 0
          ? `Share "${chosen.preview.title}" (${sharedCount} of ${totalCount} details) with ${requesterName}?`
          : `Share "${chosen.preview.title}" with ${requesterName}?`
      const approved = await confirmSensitiveAction(detailSummary)
      if (!approved) return
    }

    try {
      setStatus({ kind: 'buildingVp' })
      const [req, backendIssuers] = await Promise.all([
        verifierQr.presentationRequest
          ? Promise.resolve(verifierQr.presentationRequest)
          : fetchPresentationRequestFromQr(verifierQr, demoBackendBaseUrl),
        fetchBackendIssuers(),
      ])
      setRequest(req)
      const discloseNames = Object.entries(disclose)
        .filter(([, v]) => v)
        .map(([k]) => k)
      const vp = await createPresentation(
        [chosen.id],
        req.audience,
        req.nonce,
        discloseNames,
        { backendIssuers, verifierLabel: requesterName },
      )
      setStatus({ kind: 'submitting' })
      const response = await submitPresentationToVerifier(
        verifierQr,
        vp,
        chosen.format as 'vc+jwt' | 'vc+sd-jwt',
        req.nonce,
        demoBackendBaseUrl,
      )
      await recordWalletActivity({
        kind: 'shared',
        title: chosen.preview.title,
        counterparty: requesterName,
        detailsShared: totalCount > 0 ? discloseNames.length : undefined,
        totalDetails: totalCount > 0 ? totalCount : undefined,
        status: response.valid ? 'success' : 'failed',
      })
      if (response.valid) haptics.success()
      else haptics.error()
      setStatus({ kind: 'done', response, sharedCount: discloseNames.length, totalCount })
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
    setShowTechChecks(false)
    setStatus({ kind: 'idle' })
  }

  if (!state) {
    return (
      <View style={[s.center, s.flex, { backgroundColor: t.bg }]}>
        <ActivityIndicator color={t.primary} />
        <Text style={[s.muted, { color: t.textMuted }]}>Opening your wallet…</Text>
      </View>
    )
  }

  if (creds.length === 0) {
    return (
      <View style={[s.center, s.flex, { backgroundColor: t.bg }]}>
        <View style={[s.emptyIconCircle, { backgroundColor: t.infoBg }]}>
          <Ionicons name="wallet-outline" size={36} color={t.primary} />
        </View>
        <Text style={[s.emptyTitle, { color: t.text }]}>Nothing to share yet</Text>
        <Text style={[s.muted, { color: t.textMuted }]}>
          Add a credential to your wallet first, then scan a requester’s QR code or tap NFC.
        </Text>
        <TouchableOpacity
          style={[s.primaryBtn, { backgroundColor: t.primary, width: '100%' }]}
          onPress={() => router.replace('/scan')}
          accessibilityRole="button"
        >
          <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>Scan QR to add credential</Text>
        </TouchableOpacity>
      </View>
    )
  }

  const canShare = Boolean(selected && isCredentialBoundToHolder(selected, state.holder.did))
  const inFlight =
    status.kind === 'loadingRequest' || status.kind === 'buildingVp' || status.kind === 'submitting'

  // Dedicated full-screen outcome view after sharing completes (no leftover consent switches!).
  if (phase === 'done' && status.kind === 'done') {
    const ok = status.response.valid
    return (
      <ScrollView style={[s.flex, { backgroundColor: t.bg }]} contentContainerStyle={s.container}>
        <View style={[s.outcomeCard, { backgroundColor: t.surface, borderColor: t.border }, t.shadow]}>
          <View style={[s.outcomeIcon, { backgroundColor: ok ? t.successBg : t.dangerBg }]}>
            <Ionicons
              name={ok ? 'checkmark-circle' : 'close-circle'}
              size={48}
              color={ok ? t.success : t.danger}
            />
          </View>
          <Text style={[s.outcomeTitle, { color: t.text }]}>
            {ok ? 'Shared & verified' : 'Verification failed'}
          </Text>
          <Text style={[s.outcomeSub, { color: t.textMuted }]}>
            {ok
              ? `${selected?.preview.title ?? 'Credential'} was verified by ${requesterName}.`
              : `${requesterName} could not verify this presentation.`}
          </Text>
          {status.totalCount > 0 ? (
            <View style={[s.summaryPill, { backgroundColor: t.infoBg }]}>
              <Ionicons name="eye-off-outline" size={14} color={t.info} />
              <Text style={[s.summaryPillText, { color: t.info }]}>
                Shared {status.sharedCount} of {status.totalCount} details · {status.totalCount - status.sharedCount} kept hidden
              </Text>
            </View>
          ) : null}

          <TouchableOpacity
            style={[s.primaryBtn, { backgroundColor: t.primary, width: '100%', marginTop: 12 }]}
            onPress={() => {
              resetFlow()
              if (router.canDismiss()) router.dismissAll()
              router.replace('/')
            }}
            accessibilityRole="button"
          >
            <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>Done</Text>
          </TouchableOpacity>
        </View>

        <View style={[s.card, { backgroundColor: t.surface, borderColor: t.border }]}>
          <Pressable
            style={s.collapseHead}
            onPress={() => setShowTechChecks((v) => !v)}
            accessibilityRole="button"
            accessibilityState={{ expanded: showTechChecks }}
          >
            <Text style={[s.sectionLabel, { color: t.textMuted, marginTop: 0 }]}>
              TECHNICAL VERIFICATION CHECKS ({status.response.checks.length})
            </Text>
            <Ionicons
              name={showTechChecks ? 'chevron-up' : 'chevron-down'}
              size={18}
              color={t.textMuted}
            />
          </Pressable>
          {showTechChecks
            ? status.response.checks.map((c, i) => (
                <View
                  key={i}
                  style={[
                    s.checkRow,
                    { backgroundColor: c.passed ? t.successBg : t.dangerBg },
                  ]}
                >
                  <Ionicons
                    name={c.passed ? 'checkmark-circle' : 'close-circle'}
                    size={16}
                    color={c.passed ? t.success : t.danger}
                  />
                  <Text style={[s.checkStep, { color: t.text }]}>{c.step}</Text>
                </View>
              ))
            : null}
        </View>
      </ScrollView>
    )
  }

  if (phase === 'scan') {
    return (
      <>
        <ScrollView style={[s.flex, { backgroundColor: t.bg }]} contentContainerStyle={s.container}>
          <Text style={[s.heroSub, { color: t.textMuted, marginTop: 0 }]}>
            Point your camera at a verification QR code, or hold your phone near an NFC reader.
          </Text>

          <View style={[s.scanPanel, { backgroundColor: t.surface, borderColor: t.border }, t.shadow]}>
            <View style={[s.emptyIconCircle, { backgroundColor: t.infoBg }]}>
              <Ionicons name="shield-checkmark-outline" size={34} color={t.primary} />
            </View>
            <Text style={[s.scanTitle, { color: t.text }]}>Connect to requester</Text>
            <Text style={[s.muted, { color: t.textMuted }]}>
              You will review and approve every detail before anything leaves your phone.
            </Text>
            <VerifierQrScannerButton
              onPress={() => setScannerOpen(true)}
              loading={status.kind === 'loadingRequest'}
            />
            <NfcVerifierScanButton
              onScan={(qr) => void onVerifierScanned(qr, preferredId)}
              onError={(message) => setStatus({ kind: 'error', message })}
              disabled={status.kind === 'loadingRequest'}
            />
          </View>

          {status.kind === 'loadingRequest' ? (
            <View style={s.loadingRow}>
              <ActivityIndicator color={t.primary} />
              <Text style={[s.muted, { color: t.textMuted }]}>Reading verification request…</Text>
            </View>
          ) : null}

          {status.kind === 'error' ? (
            <View style={[s.errorPanel, { backgroundColor: t.dangerBg, borderColor: t.danger }]}>
              <Text style={[s.errorTitle, { color: t.danger }]}>Could not start sharing</Text>
              <Text style={[s.errorMessage, { color: t.text }]}>{status.message}</Text>
              {status.message.includes('issuer') ? (
                <TouchableOpacity
                  style={[s.outlineBtn, { borderColor: t.primary, marginTop: 8 }]}
                  onPress={() => router.replace('/scan')}
                  accessibilityRole="button"
                >
                  <Text style={[s.outlineBtnText, { color: t.primary }]}>Scan issuer QR</Text>
                </TouchableOpacity>
              ) : null}
            </View>
          ) : null}
        </ScrollView>

        <VerifierQrScanner
          visible={scannerOpen}
          onClose={() => setScannerOpen(false)}
          onScan={(qr) => void onVerifierScanned(qr, preferredId)}
        />
      </>
    )
  }

  return (
    <ScrollView style={[s.flex, { backgroundColor: t.bg }]} contentContainerStyle={s.container}>
      <View style={[s.requesterCard, { backgroundColor: t.infoBg, borderColor: t.primary }]}>
        <Ionicons name="business-outline" size={22} color={t.primary} />
        <View style={{ flex: 1, gap: 2 }}>
          <Text style={[s.requesterLabel, { color: t.primary }]}>REQUESTED BY</Text>
          <Text style={[s.requesterName, { color: t.text }]}>{requesterName}</Text>
          {request?.acceptedTypes.length ? (
            <Text style={[s.requesterMeta, { color: t.textMuted }]}>
              Accepts: {request.acceptedTypes.join(', ')}
            </Text>
          ) : null}
        </View>
      </View>

      <Text style={[s.sectionLabel, { color: t.textMuted }]}>CREDENTIAL TO SHARE</Text>
      {matchingCreds.map((c) => (
        <CredentialLibraryCard
          key={c.id}
          cred={c}
          selected={selectedId === c.id}
          onPress={() => setSelectedId(c.id)}
        />
      ))}

      {selectedExpired ? (
        <View style={[s.errorPanel, { backgroundColor: t.warningBg, borderColor: t.warning }]}>
          <Text style={[s.errorTitle, { color: t.warning }]}>Credential expired</Text>
          <Text style={[s.errorMessage, { color: t.text }]}>
            This credential has passed its expiration date. The requester may reject it until the issuer reissues a fresh copy.
          </Text>
        </View>
      ) : null}

      {selected && shareableClaims.length > 0 ? (
        <View style={[s.card, { backgroundColor: t.surface, borderColor: t.border }]}>
          <View style={s.shareHeader}>
            <View style={{ flex: 1 }}>
              <Text style={[s.sectionLabel, { color: t.textMuted, marginTop: 0 }]}>
                CHOOSE WHAT TO SHARE
              </Text>
              <Text style={[s.shareExplainer, { color: t.textMuted }]}>
                Everything switched off stays completely hidden from the requester.
              </Text>
            </View>
          </View>

          <View style={s.quickRow}>
            <View style={[s.summaryPill, { backgroundColor: t.surfaceElevated }]}>
              <Ionicons name="eye-off-outline" size={14} color={t.primary} />
              <Text style={[s.summaryPillText, { color: t.text }]}>
                Sharing {sharedCount} of {totalCount} · {hiddenCount} hidden
              </Text>
            </View>
            <View style={s.chipGroup}>
              <Pressable
                style={[s.chip, { borderColor: t.border, backgroundColor: t.surfaceElevated }]}
                onPress={() => {
                  const next: Record<string, boolean> = {}
                  for (const item of shareableClaims) next[item.name] = false
                  setDisclose(next)
                }}
                accessibilityRole="button"
              >
                <Text style={[s.chipText, { color: t.textMuted }]}>Minimal</Text>
              </Pressable>
              <Pressable
                style={[s.chip, { borderColor: t.primary, backgroundColor: t.infoBg }]}
                onPress={() => {
                  const next: Record<string, boolean> = {}
                  for (const item of shareableClaims) next[item.name] = true
                  setDisclose(next)
                }}
                accessibilityRole="button"
              >
                <Text style={[s.chipText, { color: t.primary }]}>All</Text>
              </Pressable>
            </View>
          </View>

          {shareableClaims.map((item, idx) => {
            const on = Boolean(disclose[item.name])
            return (
              <Pressable
                key={item.name}
                style={[
                  s.claimToggleRow,
                  idx < shareableClaims.length - 1 && {
                    borderBottomWidth: 1,
                    borderBottomColor: t.border,
                  },
                ]}
                onPress={() => setDisclose((prev) => ({ ...prev, [item.name]: !on }))}
                accessibilityRole="switch"
                accessibilityState={{ checked: on }}
                accessibilityLabel={`${item.label}${item.value ? `: ${item.value}` : ''}`}
              >
                <View style={{ flex: 1, gap: 2 }}>
                  <Text style={[s.claimLabel, { color: t.text }]}>{item.label}</Text>
                  <Text style={[s.claimValue, { color: on ? t.text : t.textMuted }]} numberOfLines={2}>
                    {item.value ?? 'Included in credential'}
                  </Text>
                  <Text style={[s.claimState, { color: on ? t.success : t.textMuted }]}>
                    {on ? 'Will be shared' : 'Stays hidden'}
                  </Text>
                </View>
                <Switch
                  value={on}
                  onValueChange={(v) => setDisclose((prev) => ({ ...prev, [item.name]: v }))}
                  trackColor={{ false: t.border, true: t.success }}
                />
              </Pressable>
            )
          })}
        </View>
      ) : null}

      {status.kind === 'error' ? (
        <View style={[s.errorPanel, { backgroundColor: t.dangerBg, borderColor: t.danger }]}>
          <Text style={[s.errorTitle, { color: t.danger }]}>Sharing failed</Text>
          <Text style={[s.errorMessage, { color: t.text }]}>{status.message}</Text>
        </View>
      ) : null}

      <TouchableOpacity
        style={[s.primaryBtn, { backgroundColor: t.primary }, (inFlight || !canShare) && s.disabled]}
        onPress={onShare}
        disabled={inFlight || !canShare}
        accessibilityRole="button"
      >
        {inFlight ? (
          <View style={s.loadingRow}>
            <ActivityIndicator color={t.onPrimary} />
            <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>{statusLabel(status)}</Text>
          </View>
        ) : (
          <>
            <Ionicons
              name={prefs?.biometricLockEnabled ? 'shield-checkmark-outline' : 'paper-plane-outline'}
              size={18}
              color={t.onPrimary}
            />
            <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>
              {prefs?.biometricLockEnabled
                ? totalCount > 0
                  ? sharedCount > 0
                    ? `Confirm & share (${sharedCount} ${sharedCount === 1 ? 'detail' : 'details'})`
                    : 'Confirm & share (proof only)'
                  : 'Confirm & share'
                : totalCount > 0
                  ? sharedCount > 0
                    ? `Share (${sharedCount} ${sharedCount === 1 ? 'detail' : 'details'})`
                    : 'Share proof only'
                  : 'Share with requester'}
            </Text>
          </>
        )}
      </TouchableOpacity>

      <TouchableOpacity
        style={[s.outlineBtn, { borderColor: t.border }]}
        onPress={() => {
          resetFlow()
          if (router.canDismiss()) router.dismissAll()
          router.replace('/')
        }}
        disabled={inFlight}
        accessibilityRole="button"
      >
        <Text style={[s.outlineBtnText, { color: t.textMuted }]}>Decline</Text>
      </TouchableOpacity>
    </ScrollView>
  )
}

function statusLabel(s: Status): string {
  switch (s.kind) {
    case 'loadingRequest':
      return 'Reading request…'
    case 'buildingVp':
      return 'Preparing proof…'
    case 'submitting':
      return 'Sharing…'
    default:
      return ''
  }
}

const s = StyleSheet.create({
  flex: { flex: 1 },
  container: { padding: 16, gap: 14, paddingBottom: 36 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24, gap: 12 },
  heroTitle: { fontSize: 28, fontWeight: '800', letterSpacing: -0.3 },
  heroSub: { fontSize: 14, lineHeight: 20, marginTop: -8 },
  emptyIconCircle: {
    width: 70,
    height: 70,
    borderRadius: 35,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyTitle: { fontSize: 19, fontWeight: '700' },
  scanPanel: {
    borderRadius: 16,
    padding: 22,
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
  },
  scanTitle: { fontSize: 17, fontWeight: '700' },
  muted: { fontSize: 14, textAlign: 'center', lineHeight: 20 },
  requesterCard: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    padding: 16,
    borderRadius: 14,
    borderWidth: 1,
  },
  requesterLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 0.6 },
  requesterName: { fontSize: 17, fontWeight: '700' },
  requesterMeta: { fontSize: 13 },
  sectionLabel: { fontSize: 11, fontWeight: '700', letterSpacing: 0.6, marginTop: 4 },
  card: { borderRadius: 14, padding: 16, gap: 10, borderWidth: 1 },
  shareHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  shareExplainer: { fontSize: 13, lineHeight: 18, marginTop: 2 },
  quickRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 2,
  },
  summaryPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
  },
  summaryPillText: { fontSize: 12, fontWeight: '700' },
  chipGroup: { flexDirection: 'row', gap: 6 },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    borderWidth: 1,
    minHeight: 32,
    justifyContent: 'center',
  },
  chipText: { fontSize: 12, fontWeight: '700' },
  claimToggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 12,
    minHeight: 60,
  },
  claimLabel: { fontSize: 15, fontWeight: '600' },
  claimValue: { fontSize: 14 },
  claimState: { fontSize: 11, fontWeight: '600', marginTop: 2 },
  primaryBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    minHeight: 52,
    borderRadius: 999,
    marginTop: 4,
  },
  primaryBtnText: { fontWeight: '700', fontSize: 15 },
  outlineBtn: {
    minHeight: 48,
    borderRadius: 999,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  outlineBtnText: { fontWeight: '700', fontSize: 15 },
  disabled: { opacity: 0.55 },
  loadingRow: { flexDirection: 'row', alignItems: 'center', gap: 10, justifyContent: 'center' },
  errorPanel: { padding: 14, borderRadius: 12, gap: 6, borderWidth: 1 },
  errorTitle: { fontWeight: '700', fontSize: 15 },
  errorMessage: { fontSize: 14, lineHeight: 20 },
  outcomeCard: {
    borderRadius: 18,
    padding: 24,
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
    marginTop: 8,
  },
  outcomeIcon: {
    width: 80,
    height: 80,
    borderRadius: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  outcomeTitle: { fontSize: 22, fontWeight: '800' },
  outcomeSub: { fontSize: 15, textAlign: 'center', lineHeight: 22 },
  collapseHead: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 36,
  },
  checkRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    padding: 10,
    borderRadius: 10,
  },
  checkStep: { fontSize: 13, flex: 1 },
})
