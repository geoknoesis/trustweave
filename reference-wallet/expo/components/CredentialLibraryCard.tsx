import { Image, Pressable, StyleSheet, Text, View } from 'react-native'
import type { StoredCredential } from '@/lib/storage'
import { credentialSummary } from '@/lib/credentialDisplay'
import { theme } from '@/lib/credentialDisplay'
import { extractPortrait } from '@/lib/credentialImage'

interface Props {
  cred: StoredCredential
  onPress: () => void
}

export function CredentialLibraryCard({ cred, onPress }: Props) {
  const s = credentialSummary(cred)
  const portrait = extractPortrait(cred)
  return (
    <Pressable
      style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${s.title}, ${s.issuer}`}
    >
      <View style={[styles.stripe, { backgroundColor: s.accent }]} />
      {portrait ? (
        <Image source={{ uri: portrait }} style={styles.thumb} resizeMode="cover" accessibilityLabel="Cardholder portrait" />
      ) : null}
      <View style={styles.body}>
        <View style={styles.topRow}>
          <Text style={styles.title} numberOfLines={2}>{s.title}</Text>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>Stored</Text>
          </View>
        </View>
        {s.subtitle ? <Text style={styles.subtitle} numberOfLines={2}>{s.subtitle}</Text> : null}
        <Text style={styles.meta}>{s.issuer} · Added {s.added}</Text>
      </View>
      <Text style={styles.chevron}>›</Text>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: theme.surface,
    borderRadius: theme.radius,
    marginBottom: 12,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: theme.border,
    ...theme.shadow,
  },
  cardPressed: { opacity: 0.92 },
  stripe: { width: 5, alignSelf: 'stretch' },
  thumb: {
    width: 44,
    height: 56,
    borderRadius: 6,
    marginLeft: 12,
    backgroundColor: theme.bg,
    borderWidth: 1,
    borderColor: theme.border,
  },
  body: { flex: 1, paddingVertical: 14, paddingHorizontal: 14, gap: 4 },
  topRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 8 },
  title: { flex: 1, fontSize: 16, fontWeight: '600', color: theme.primary },
  badge: {
    backgroundColor: theme.successBg,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 999,
  },
  badgeText: { fontSize: 10, fontWeight: '600', color: theme.success, textTransform: 'uppercase' },
  subtitle: { fontSize: 14, color: theme.text },
  meta: { fontSize: 12, color: theme.textMuted, marginTop: 4 },
  chevron: { fontSize: 22, color: theme.textMuted, paddingRight: 12 },
})
