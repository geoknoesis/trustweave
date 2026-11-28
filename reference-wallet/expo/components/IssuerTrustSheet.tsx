import { useState } from 'react'
import { Modal, Pressable, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { Ionicons } from '@expo/vector-icons'
import { monoFont, useWalletTheme } from '@/lib/credentialDisplay'
import { shortDidFingerprint } from '@/lib/issuerTrust'

interface IssuerTrustSheetProps {
  visible: boolean
  issuerDid: string
  credentialTitle?: string
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * Flow 4: Human-Readable Fail-Closed Issuer Trust Confirmation.
 *
 * Preserves the fail-closed invariant: unknown issuers are NEVER auto-trusted.
 * Presents plain risk framing, a scannable key fingerprint, and an expandable
 * full cryptographic DID so users make an informed decision without alert fatigue.
 */
export function IssuerTrustSheet({
  visible,
  issuerDid,
  credentialTitle,
  busy = false,
  onConfirm,
  onCancel,
}: IssuerTrustSheetProps) {
  const t = useWalletTheme()
  const [showFullDid, setShowFullDid] = useState(false)

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onCancel}>
      <SafeAreaView style={[s.safe, { backgroundColor: t.bg }]} edges={['top', 'bottom']}>
        <ScrollView contentContainerStyle={s.container}>
          <View style={[s.warningBanner, { backgroundColor: t.warningBg, borderColor: t.warning }]}>
            <Ionicons name="shield-outline" size={26} color={t.warning} />
            <View style={s.bannerText}>
              <Text style={[s.bannerTitle, { color: t.warning }]}>Unrecognized issuer</Text>
              <Text style={[s.bannerBody, { color: t.text }]}>
                This wallet does not automatically recognize the organization that signed this credential.
              </Text>
            </View>
          </View>

          <View style={[s.card, { backgroundColor: t.surface, borderColor: t.border }]}>
            {credentialTitle ? (
              <View style={s.row}>
                <Text style={[s.label, { color: t.textMuted }]}>CREDENTIAL TYPE (UNVERIFIED)</Text>
                <Text style={[s.value, { color: t.text }]}>{credentialTitle}</Text>
              </View>
            ) : null}

            <View style={s.row}>
              <Text style={[s.label, { color: t.textMuted }]}>ISSUER KEY FINGERPRINT</Text>
              <Text style={[s.fingerprint, { color: t.text }]}>{shortDidFingerprint(issuerDid)}</Text>
            </View>

            <Pressable
              style={s.expandRow}
              onPress={() => setShowFullDid((v) => !v)}
              accessibilityRole="button"
              accessibilityState={{ expanded: showFullDid }}
            >
              <Text style={[s.expandText, { color: t.primary }]}>
                {showFullDid ? 'Hide full cryptographic ID (DID)' : 'Inspect full cryptographic ID (DID)'}
              </Text>
              <Ionicons
                name={showFullDid ? 'chevron-up' : 'chevron-down'}
                size={16}
                color={t.primary}
              />
            </Pressable>

            {showFullDid ? (
              <View style={[s.didBox, { backgroundColor: t.surfaceElevated, borderColor: t.border }]}>
                <Text style={[s.mono, { color: t.text }]} selectable>
                  {issuerDid}
                </Text>
              </View>
            ) : null}
          </View>

          <View style={[s.noteBox, { backgroundColor: t.surface, borderColor: t.border }]}>
            <Ionicons name="information-circle-outline" size={20} color={t.primary} />
            <Text style={[s.noteText, { color: t.textMuted }]}>
              Only continue if you are directly expecting this credential from this organization. You can
              remove trusted issuers anytime in Settings.
            </Text>
          </View>
        </ScrollView>

        <View style={[s.footer, { backgroundColor: t.surface, borderTopColor: t.border }]}>
          <TouchableOpacity
            style={[s.cancelBtn, { borderColor: t.border }]}
            onPress={onCancel}
            disabled={busy}
            accessibilityRole="button"
            accessibilityLabel="Cancel adding credential"
          >
            <Text style={[s.cancelText, { color: t.text }]} numberOfLines={2}>
              Cancel
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[s.confirmBtn, { backgroundColor: t.primary }, busy && s.disabled]}
            onPress={onConfirm}
            disabled={busy}
            accessibilityRole="button"
            accessibilityLabel="Trust issuer and add credential"
          >
            <Text style={[s.confirmText, { color: t.onPrimary }]} numberOfLines={2}>
              {busy ? 'Verifying…' : 'Trust issuer & add'}
            </Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    </Modal>
  )
}

const s = StyleSheet.create({
  safe: { flex: 1 },
  container: { padding: 20, gap: 16 },
  warningBanner: {
    flexDirection: 'row',
    gap: 12,
    padding: 16,
    borderRadius: 14,
    borderWidth: 1,
    alignItems: 'flex-start',
  },
  bannerText: { flex: 1, gap: 4 },
  bannerTitle: { fontSize: 17, fontWeight: '700' },
  bannerBody: { fontSize: 14, lineHeight: 20 },
  card: {
    borderRadius: 14,
    padding: 16,
    gap: 14,
    borderWidth: 1,
  },
  row: { gap: 4 },
  label: { fontSize: 11, fontWeight: '700', letterSpacing: 0.6 },
  value: { fontSize: 16, fontWeight: '600' },
  fingerprint: { fontFamily: monoFont, fontSize: 18, fontWeight: '700', letterSpacing: 0.5 },
  expandRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 6,
    minHeight: 40,
  },
  expandText: { fontSize: 14, fontWeight: '600' },
  didBox: {
    padding: 12,
    borderRadius: 10,
    borderWidth: 1,
  },
  mono: { fontFamily: monoFont, fontSize: 11, lineHeight: 16 },
  noteBox: {
    flexDirection: 'row',
    gap: 10,
    padding: 14,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'flex-start',
  },
  noteText: { flex: 1, fontSize: 13, lineHeight: 19 },
  footer: {
    flexDirection: 'row',
    gap: 12,
    padding: 16,
    borderTopWidth: 1,
  },
  cancelBtn: {
    flex: 1,
    minHeight: 48,
    paddingHorizontal: 10,
    paddingVertical: 10,
    borderRadius: 999,
    borderWidth: 1.5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  cancelText: { fontSize: 15, fontWeight: '600', textAlign: 'center' },
  confirmBtn: {
    flex: 1.4,
    minHeight: 48,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
  },
  confirmText: { fontSize: 15, fontWeight: '700', textAlign: 'center' },
  disabled: { opacity: 0.6 },
})
