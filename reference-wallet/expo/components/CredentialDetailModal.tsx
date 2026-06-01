import { Image, Modal, Pressable, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import type { StoredCredential } from '@/lib/storage'
import { credentialSummary, formatReceivedDate, humanClaimName, theme } from '@/lib/credentialDisplay'
import { extractPortrait } from '@/lib/credentialImage'

interface Props {
  cred: StoredCredential | null
  visible: boolean
  onClose: () => void
  onDelete: () => void
}

export function CredentialDetailModal({ cred, visible, onClose, onDelete }: Props) {
  if (!cred) return null
  const summary = credentialSummary(cred)
  const portrait = extractPortrait(cred)

  return (
    <Modal visible={visible} animationType="slide" presentationStyle="pageSheet" onRequestClose={onClose}>
      <View style={styles.header}>
        <Pressable onPress={onClose} hitSlop={12}>
          <Text style={styles.close}>Close</Text>
        </Pressable>
        <Text style={styles.headerTitle}>Credential details</Text>
        <View style={{ width: 48 }} />
      </View>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={[styles.hero, { borderLeftColor: summary.accent }]}>
          <View style={styles.heroRow}>
            {portrait ? (
              <Image source={{ uri: portrait }} style={styles.portrait} resizeMode="cover" accessibilityLabel="Cardholder portrait" />
            ) : null}
            <View style={styles.heroText}>
              <Text style={styles.heroTitle}>{summary.title}</Text>
              {summary.subtitle ? <Text style={styles.heroSubtitle}>{summary.subtitle}</Text> : null}
              <View style={styles.badgeRow}>
                <View style={styles.badge}>
                  <Text style={styles.badgeText}>Authentic</Text>
                </View>
                <Text style={styles.added}>Added {formatReceivedDate(cred.receivedAt)}</Text>
              </View>
            </View>
          </View>
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionLabel}>Issuer</Text>
          <Text style={styles.sectionValue}>{summary.issuer}</Text>
        </View>

        {cred.selectivelyDisclosable.length > 0 && (
          <View style={styles.section}>
            <Text style={styles.sectionLabel}>Information you can choose to share</Text>
            {cred.selectivelyDisclosable.map((name) => (
              <View key={name} style={styles.claimRow}>
                <Text style={styles.claimDot}>•</Text>
                <Text style={styles.claimName}>{humanClaimName(name)}</Text>
              </View>
            ))}
          </View>
        )}

        <TouchableOpacity style={styles.deleteBtn} onPress={onDelete}>
          <Text style={styles.deleteText}>Remove from wallet</Text>
        </TouchableOpacity>
      </ScrollView>
    </Modal>
  )
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: theme.border,
    backgroundColor: theme.surface,
  },
  close: { color: theme.primary, fontSize: 16, fontWeight: '500' },
  headerTitle: { fontSize: 16, fontWeight: '600', color: theme.text },
  content: { padding: 16, gap: 16, backgroundColor: theme.bg },
  hero: {
    backgroundColor: theme.surface,
    borderRadius: theme.radius,
    padding: 16,
    borderLeftWidth: 4,
    ...theme.shadow,
  },
  heroRow: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  heroText: { flex: 1, gap: 6 },
  portrait: {
    width: 72,
    height: 90,
    borderRadius: 8,
    backgroundColor: theme.bg,
    borderWidth: 1,
    borderColor: theme.border,
  },
  heroTitle: { fontSize: 20, fontWeight: '700', color: theme.primary },
  heroSubtitle: { fontSize: 16, color: theme.text },
  badgeRow: { flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 8 },
  badge: { backgroundColor: theme.successBg, paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },
  badgeText: { fontSize: 11, fontWeight: '600', color: theme.success },
  added: { fontSize: 13, color: theme.textMuted },
  section: {
    backgroundColor: theme.surface,
    borderRadius: theme.radius,
    padding: 16,
    gap: 8,
    borderWidth: 1,
    borderColor: theme.border,
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: theme.textMuted,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  sectionValue: { fontSize: 15, color: theme.text },
  claimRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  claimDot: { color: theme.primary, fontSize: 16 },
  claimName: { fontSize: 15, color: theme.text },
  deleteBtn: {
    alignSelf: 'center',
    paddingVertical: 12,
    paddingHorizontal: 20,
    marginTop: 8,
  },
  deleteText: { color: '#dc2626', fontSize: 15, fontWeight: '500' },
})
