import { useState } from 'react'
import { Image, Modal, Pressable, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { Ionicons } from '@expo/vector-icons'
import type { StoredCredential } from '@/lib/storage'
import {
  credentialSummary,
  formatReceivedDate,
  humanClaimName,
  monoFont,
  useWalletTheme,
  type TrustBadgeTone,
  type WalletTheme,
} from '@/lib/credentialDisplay'
import { extractClaimsForDisplay } from '@/lib/credentialClaims'
import { extractPortrait } from '@/lib/credentialImage'

interface Props {
  cred: StoredCredential | null
  visible: boolean
  onClose: () => void
  onDelete: () => void
  onShare?: (cred: StoredCredential) => void
}

function badgeColors(tone: TrustBadgeTone, t: WalletTheme): { bg: string; fg: string } {
  switch (tone) {
    case 'success':
      return { bg: t.successBg, fg: t.success }
    case 'info':
      return { bg: t.infoBg, fg: t.info }
    case 'warning':
      return { bg: t.warningBg, fg: t.warning }
    case 'danger':
      return { bg: t.dangerBg, fg: t.danger }
    default:
      return { bg: t.surfaceElevated, fg: t.textMuted }
  }
}

export function CredentialDetailModal({ cred, visible, onClose, onDelete, onShare }: Props) {
  const t = useWalletTheme()
  const [showTech, setShowTech] = useState(false)

  if (!cred) return null
  const summary = credentialSummary(cred)
  const portrait = extractPortrait(cred)
  const claims = extractClaimsForDisplay(cred)
  const pill = badgeColors(summary.badge.tone, t)

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <SafeAreaView style={[styles.safe, { backgroundColor: t.bg }]} edges={['top', 'bottom']}>
        <View style={[styles.header, { backgroundColor: t.surface, borderBottomColor: t.border }]}>
          <Pressable
            onPress={onClose}
            hitSlop={12}
            style={styles.headerAction}
            accessibilityRole="button"
            accessibilityLabel="Close credential details"
          >
            <Text style={[styles.close, { color: t.primary }]}>Close</Text>
          </Pressable>
          <Text style={[styles.headerTitle, { color: t.text }]}>Credential</Text>
          <View style={styles.headerAction} />
        </View>

        <ScrollView contentContainerStyle={[styles.content, { backgroundColor: t.bg }]}>
          <View
            style={[
              styles.hero,
              {
                backgroundColor: t.surface,
                borderLeftColor: summary.accent,
                borderColor: t.border,
              },
              t.shadow,
            ]}
          >
            <View style={styles.heroRow}>
              {portrait ? (
                <Image
                  source={{ uri: portrait }}
                  style={[styles.portrait, { backgroundColor: t.bg, borderColor: t.border }]}
                  resizeMode="cover"
                  accessibilityLabel="Cardholder portrait"
                />
              ) : (
                <View style={[styles.heroIconBox, { backgroundColor: `${summary.accent}18` }]}>
                  <Ionicons name={summary.icon} size={32} color={summary.accent} />
                </View>
              )}
              <View style={styles.heroText}>
                <Text style={[styles.heroTitle, { color: t.text }]}>{summary.title}</Text>
                {summary.subtitle ? (
                  <Text style={[styles.heroSubtitle, { color: t.textMuted }]}>{summary.subtitle}</Text>
                ) : null}
                <View style={styles.badgeRow}>
                  <View style={[styles.badge, { backgroundColor: pill.bg }]}>
                    <Ionicons name={summary.badge.icon} size={12} color={pill.fg} />
                    <Text style={[styles.badgeText, { color: pill.fg }]}>{summary.badge.label}</Text>
                  </View>
                  <Text style={[styles.added, { color: t.textMuted }]}>
                    Added {formatReceivedDate(cred.receivedAt)}
                  </Text>
                </View>
              </View>
            </View>
          </View>

          {onShare && cred.format !== 'vc-ld' ? (
            <TouchableOpacity
              style={[styles.shareBtn, { backgroundColor: t.primary }]}
              onPress={() => onShare(cred)}
              accessibilityRole="button"
              accessibilityLabel={`Share ${summary.title}`}
            >
              <Ionicons name="qr-code-outline" size={18} color={t.onPrimary} />
              <Text style={[styles.shareBtnText, { color: t.onPrimary }]}>Share this credential</Text>
            </TouchableOpacity>
          ) : null}

          <View style={[styles.section, { backgroundColor: t.surface, borderColor: t.border }]}>
            <Text style={[styles.sectionLabel, { color: t.textMuted }]}>Issued by</Text>
            <Text style={[styles.sectionValue, { color: t.text }]}>{summary.issuer}</Text>
          </View>

          {claims.length > 0 ? (
            <View style={[styles.section, { backgroundColor: t.surface, borderColor: t.border }]}>
              <Text style={[styles.sectionLabel, { color: t.textMuted }]}>Details</Text>
              {claims.map((r) => (
                <View
                  key={r.key}
                  style={[
                    styles.fieldRow,
                    r.depth > 0
                      ? { marginLeft: r.depth * 12, borderLeftWidth: 2, borderLeftColor: t.border, paddingLeft: 8 }
                      : null,
                  ]}
                >
                  <View style={styles.fieldHead}>
                    <Text style={[styles.fieldName, { color: t.textMuted }]}>{r.label}</Text>
                    {r.shareable ? (
                      <View style={[styles.fieldTagWrap, { backgroundColor: t.infoBg }]}>
                        <Text style={[styles.fieldTag, { color: t.info }]}>Selectable</Text>
                      </View>
                    ) : null}
                  </View>
                  {r.value !== null ? (
                    <Text style={[styles.fieldValue, { color: t.text }]} selectable>
                      {r.value}
                    </Text>
                  ) : null}
                </View>
              ))}
            </View>
          ) : cred.selectivelyDisclosable.length > 0 ? (
            <View style={[styles.section, { backgroundColor: t.surface, borderColor: t.border }]}>
              <Text style={[styles.sectionLabel, { color: t.textMuted }]}>
                Information you can choose to share
              </Text>
              {cred.selectivelyDisclosable.map((name) => (
                <View key={name} style={styles.claimRow}>
                  <Ionicons name="checkmark-circle-outline" size={16} color={t.primary} />
                  <Text style={[styles.claimName, { color: t.text }]}>{humanClaimName(name)}</Text>
                </View>
              ))}
            </View>
          ) : null}

          <View style={[styles.section, { backgroundColor: t.surface, borderColor: t.border }]}>
            <Pressable
              style={styles.techToggle}
              onPress={() => setShowTech((v) => !v)}
              accessibilityRole="button"
              accessibilityState={{ expanded: showTech }}
            >
              <Text style={[styles.sectionLabel, { color: t.textMuted }]}>Technical details</Text>
              <Ionicons
                name={showTech ? 'chevron-up' : 'chevron-down'}
                size={16}
                color={t.textMuted}
              />
            </Pressable>
            {showTech ? (
              <View style={styles.techBody}>
                <Text style={[styles.techKey, { color: t.textMuted }]}>Format</Text>
                <Text style={[styles.mono, { color: t.text }]}>{cred.format}</Text>
                <Text style={[styles.techKey, { color: t.textMuted }]}>Issuer identifier</Text>
                <Text style={[styles.mono, { color: t.text }]} selectable>
                  {cred.issuerDid}
                </Text>
                <Text style={[styles.techKey, { color: t.textMuted }]}>Holder identifier</Text>
                <Text style={[styles.mono, { color: t.text }]} selectable>
                  {cred.subjectDid}
                </Text>
              </View>
            ) : null}
          </View>

          <TouchableOpacity
            style={[styles.deleteBtn, { borderColor: t.dangerBg }]}
            onPress={onDelete}
            accessibilityRole="button"
            accessibilityLabel={`Remove ${summary.title} from wallet`}
          >
            <Ionicons name="trash-outline" size={16} color={t.danger} />
            <Text style={[styles.deleteText, { color: t.danger }]}>Remove from wallet</Text>
          </TouchableOpacity>
        </ScrollView>
      </SafeAreaView>
    </Modal>
  )
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: 1,
    minHeight: 52,
  },
  headerAction: { minWidth: 56 },
  close: { fontSize: 16, fontWeight: '600' },
  headerTitle: { fontSize: 17, fontWeight: '700' },
  content: { padding: 16, gap: 14, paddingBottom: 32 },
  hero: {
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderLeftWidth: 5,
  },
  heroRow: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  heroIconBox: {
    width: 60,
    height: 60,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroText: { flex: 1, gap: 4 },
  portrait: {
    width: 68,
    height: 86,
    borderRadius: 10,
    borderWidth: 1,
  },
  heroTitle: { fontSize: 20, fontWeight: '700' },
  heroSubtitle: { fontSize: 15 },
  badgeRow: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginTop: 6 },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 999,
  },
  badgeText: { fontSize: 11, fontWeight: '700' },
  added: { fontSize: 12 },
  shareBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 14,
    borderRadius: 999,
    minHeight: 48,
  },
  shareBtnText: { fontSize: 15, fontWeight: '700' },
  section: {
    borderRadius: 14,
    padding: 16,
    gap: 10,
    borderWidth: 1,
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  sectionValue: { fontSize: 15, fontWeight: '500' },
  claimRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  claimName: { fontSize: 15 },
  fieldRow: { gap: 2, paddingVertical: 2 },
  fieldHead: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  fieldName: { fontSize: 11, fontWeight: '600', textTransform: 'uppercase', letterSpacing: 0.4 },
  fieldTagWrap: { paddingHorizontal: 7, paddingVertical: 2, borderRadius: 999 },
  fieldTag: { fontSize: 10, fontWeight: '700' },
  fieldValue: { fontSize: 15, fontWeight: '500' },
  techToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 32,
  },
  techBody: { gap: 4, marginTop: 4 },
  techKey: { fontSize: 11, fontWeight: '600', marginTop: 4 },
  mono: { fontFamily: monoFont, fontSize: 11 },
  deleteBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    alignSelf: 'center',
    paddingVertical: 12,
    paddingHorizontal: 20,
    minHeight: 48,
    marginTop: 4,
  },
  deleteText: { fontSize: 15, fontWeight: '600' },
})
