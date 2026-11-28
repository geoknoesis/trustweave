import { Image, Pressable, StyleSheet, Text, View } from 'react-native'
import { Ionicons } from '@expo/vector-icons'
import type { StoredCredential } from '@/lib/storage'
import {
  credentialSummary,
  useWalletTheme,
  type TrustBadgeTone,
  type WalletTheme,
} from '@/lib/credentialDisplay'
import { extractPortrait } from '@/lib/credentialImage'

interface Props {
  cred: StoredCredential
  onPress: () => void
  selected?: boolean
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

/**
 * Wallet Pass Card styled with a tinted institution header strip, status pill,
 * optional portrait, and clean scannable hierarchy (Apple/Google Wallet aesthetic).
 */
export function CredentialLibraryCard({ cred, onPress, selected = false }: Props) {
  const t = useWalletTheme()
  const s = credentialSummary(cred)
  const portrait = extractPortrait(cred)
  const pill = badgeColors(s.badge.tone, t)

  return (
    <Pressable
      style={({ pressed }) => [
        styles.card,
        {
          backgroundColor: t.surface,
          borderColor: selected ? t.primary : t.border,
          borderWidth: selected ? 2 : 1,
        },
        t.shadow,
        pressed && styles.cardPressed,
      ]}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      accessibilityLabel={`${s.title}${s.subtitle ? `, ${s.subtitle}` : ''}, ${s.issuer}, ${s.badge.label}`}
      accessibilityHint="Opens credential details"
    >
      <View style={[styles.headerBand, { backgroundColor: s.accent }]}>
        <View style={styles.headerLeft}>
          <Ionicons name={s.icon} size={16} color="#FFFFFF" />
          <Text style={styles.issuerHeader} numberOfLines={1}>
            {s.issuer}
          </Text>
        </View>
        <View style={[styles.badge, { backgroundColor: pill.bg }]}>
          <Ionicons name={s.badge.icon} size={11} color={pill.fg} />
          <Text style={[styles.badgeText, { color: pill.fg }]}>{s.badge.label}</Text>
        </View>
      </View>

      <View style={styles.bodyRow}>
        {portrait ? (
          <Image
            source={{ uri: portrait }}
            style={[styles.thumb, { backgroundColor: t.bg, borderColor: t.border }]}
            resizeMode="cover"
            accessibilityLabel="Cardholder portrait"
          />
        ) : null}
        <View style={styles.body}>
          <Text style={[styles.title, { color: t.text }]} numberOfLines={2}>
            {s.title}
          </Text>
          {s.subtitle ? (
            <Text style={[styles.subtitle, { color: t.textMuted }]} numberOfLines={1}>
              {s.subtitle}
            </Text>
          ) : null}
          <Text style={[styles.meta, { color: t.textMuted }]}>Added {s.added}</Text>
        </View>
        <Ionicons
          name={selected ? 'checkmark-circle' : 'chevron-forward'}
          size={22}
          color={selected ? t.primary : t.textMuted}
        />
      </View>
    </Pressable>
  )
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 16,
    marginBottom: 14,
    overflow: 'hidden',
  },
  cardPressed: { opacity: 0.92, transform: [{ scale: 0.99 }] },
  headerBand: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  headerLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    flex: 1,
  },
  issuerHeader: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '700',
    flex: 1,
  },
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 999,
  },
  badgeText: { fontSize: 10, fontWeight: '700' },
  bodyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 14,
    gap: 12,
    minHeight: 74,
  },
  thumb: {
    width: 46,
    height: 58,
    borderRadius: 8,
    borderWidth: 1,
  },
  body: { flex: 1, gap: 3 },
  title: { fontSize: 17, fontWeight: '700', lineHeight: 22 },
  subtitle: { fontSize: 14, fontWeight: '500' },
  meta: { fontSize: 12, marginTop: 2 },
})
