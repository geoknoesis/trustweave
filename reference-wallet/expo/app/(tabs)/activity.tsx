import { useCallback, useState } from 'react'
import { Alert, RefreshControl, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { useFocusEffect } from 'expo-router'
import { Ionicons } from '@expo/vector-icons'
import {
  clearActivityLog,
  getActivityLog,
  type ActivityEntry,
  type ActivityKind,
} from '@/lib/wallet'
import { formatRelativeTime, useWalletTheme } from '@/lib/credentialDisplay'
import { shortDidFingerprint } from '@/lib/issuerTrust'

function activityMeta(kind: ActivityKind): {
  icon: keyof typeof Ionicons.glyphMap
  verb: string
} {
  switch (kind) {
    case 'added':
      return { icon: 'add-circle-outline', verb: 'Added' }
    case 'updated':
      return { icon: 'refresh-circle-outline', verb: 'Updated' }
    case 'shared':
      return { icon: 'paper-plane-outline', verb: 'Shared' }
    case 'removed':
      return { icon: 'trash-outline', verb: 'Removed' }
    case 'trusted_issuer':
      return { icon: 'shield-checkmark-outline', verb: 'Trusted issuer' }
    case 'backup':
      return { icon: 'cloud-download-outline', verb: 'Backup' }
    case 'restored':
      return { icon: 'cloud-upload-outline', verb: 'Restored' }
  }
}

function formatCounterparty(cp?: string): string | null {
  if (!cp) return null
  if (cp.startsWith('did:')) return `Issuer · ${shortDidFingerprint(cp)}`
  return cp
}

export default function ActivityScreen() {
  const t = useWalletTheme()
  const [entries, setEntries] = useState<ActivityEntry[]>([])
  const [refreshing, setRefreshing] = useState(false)

  const refresh = useCallback(async () => {
    setEntries(await getActivityLog())
  }, [])

  const onPullRefresh = useCallback(async () => {
    setRefreshing(true)
    try {
      await refresh()
    } finally {
      setRefreshing(false)
    }
  }, [refresh])

  useFocusEffect(
    useCallback(() => {
      void refresh()
    }, [refresh]),
  )

  const handleClear = () =>
    Alert.alert(
      'Clear activity history?',
      'This clears your local timeline only. Your credentials are not affected.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Clear history',
          style: 'destructive',
          onPress: async () => {
            await clearActivityLog()
            await refresh()
          },
        },
      ],
    )

  return (
    <ScrollView
      style={[s.flex, { backgroundColor: t.bg }]}
      contentContainerStyle={s.container}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onPullRefresh} tintColor={t.primary} />
      }
    >
      <View style={s.headerRow}>
        <Text style={[s.heroSub, { color: t.textMuted, flex: 1 }]}>
          A private record of credentials added and shared from this phone.
        </Text>
        {entries.length > 0 ? (
          <TouchableOpacity onPress={handleClear} hitSlop={10} accessibilityRole="button">
            <Text style={[s.clearText, { color: t.primary }]}>Clear</Text>
          </TouchableOpacity>
        ) : null}
      </View>

      {entries.length === 0 ? (
        <View style={[s.emptyCard, { backgroundColor: t.surface, borderColor: t.border }]}>
          <View style={[s.emptyIcon, { backgroundColor: t.surfaceElevated }]}>
            <Ionicons name="time-outline" size={32} color={t.textMuted} />
          </View>
          <Text style={[s.emptyTitle, { color: t.text }]}>No activity yet</Text>
          <Text style={[s.emptyBody, { color: t.textMuted }]}>
            Whenever you add a credential or share details with an organization, it will appear here.
          </Text>
        </View>
      ) : (
        <View style={[s.listCard, { backgroundColor: t.surface, borderColor: t.border }]}>
          {entries.map((entry, idx) => {
            const meta = activityMeta(entry.kind)
            const cp = formatCounterparty(entry.counterparty)
            const toneBg =
              entry.status === 'failed'
                ? t.dangerBg
                : entry.kind === 'shared'
                  ? t.infoBg
                  : t.successBg
            const toneFg =
              entry.status === 'failed'
                ? t.danger
                : entry.kind === 'shared'
                  ? t.info
                  : t.success
            return (
              <View
                key={entry.id}
                style={[
                  s.row,
                  idx < entries.length - 1 && { borderBottomWidth: 1, borderBottomColor: t.border },
                ]}
              >
                <View style={[s.iconBox, { backgroundColor: toneBg }]}>
                  <Ionicons name={meta.icon} size={20} color={toneFg} />
                </View>
                <View style={s.rowBody}>
                  <View style={s.rowTop}>
                    <Text style={[s.rowTitle, { color: t.text }]} numberOfLines={1}>
                      {meta.verb}: {entry.title}
                    </Text>
                    <Text style={[s.rowTime, { color: t.textMuted }]}>
                      {formatRelativeTime(entry.timestamp)}
                    </Text>
                  </View>
                  {cp ? (
                    <Text style={[s.rowSub, { color: t.textMuted }]} numberOfLines={1}>
                      {entry.kind === 'shared' ? `With ${cp}` : cp}
                    </Text>
                  ) : entry.subtitle ? (
                    <Text style={[s.rowSub, { color: t.textMuted }]} numberOfLines={1}>
                      {entry.subtitle}
                    </Text>
                  ) : null}
                  {typeof entry.detailsShared === 'number' && typeof entry.totalDetails === 'number' ? (
                    <Text style={[s.rowMeta, { color: t.primary }]}>
                      Shared {entry.detailsShared} of {entry.totalDetails} details
                    </Text>
                  ) : null}
                </View>
              </View>
            )
          })}
        </View>
      )}
    </ScrollView>
  )
}

const s = StyleSheet.create({
  flex: { flex: 1 },
  container: { padding: 16, gap: 14, paddingBottom: 32 },
  headerRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12 },
  heroSub: { fontSize: 14, lineHeight: 20 },
  clearText: { fontSize: 14, fontWeight: '700' },
  emptyCard: {
    borderRadius: 16,
    padding: 28,
    alignItems: 'center',
    gap: 10,
    borderWidth: 1,
  },
  emptyIcon: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyTitle: { fontSize: 18, fontWeight: '700' },
  emptyBody: { fontSize: 14, textAlign: 'center', lineHeight: 20 },
  listCard: { borderRadius: 14, borderWidth: 1, overflow: 'hidden' },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: 12, padding: 14 },
  iconBox: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 2,
  },
  rowBody: { flex: 1, gap: 2 },
  rowTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  rowTitle: { flex: 1, fontSize: 15, fontWeight: '600' },
  rowTime: { fontSize: 12 },
  rowSub: { fontSize: 13 },
  rowMeta: { fontSize: 12, fontWeight: '600', marginTop: 2 },
})
