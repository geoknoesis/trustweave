import { Pressable } from 'react-native'
import { Tabs, useRouter } from 'expo-router'
import { Ionicons } from '@expo/vector-icons'
import { useWalletTheme } from '@/lib/credentialDisplay'

export default function TabsLayout() {
  const t = useWalletTheme()
  const router = useRouter()

  const SettingsHeaderButton = () => (
    <Pressable
      onPress={() => router.push('/settings')}
      hitSlop={12}
      style={{
        marginRight: 16,
        width: 44,
        height: 44,
        alignItems: 'center',
        justifyContent: 'center',
      }}
      accessibilityRole="button"
      accessibilityLabel="Open settings and security"
    >
      <Ionicons name="settings-outline" size={22} color={t.text} />
    </Pressable>
  )

  return (
    <Tabs
      screenOptions={{
        tabBarActiveTintColor: t.primary,
        tabBarInactiveTintColor: t.textMuted,
        tabBarStyle: {
          backgroundColor: t.surface,
          borderTopColor: t.border,
          paddingTop: 4,
          height: 62,
        },
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '600',
          marginBottom: 4,
        },
        headerStyle: {
          backgroundColor: t.surface,
          borderBottomWidth: 1,
          borderBottomColor: t.border,
        },
        headerTintColor: t.text,
        headerTitleStyle: { fontWeight: '700', fontSize: 18 },
        headerShadowVisible: false,
        sceneStyle: { backgroundColor: t.bg },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Wallet',
          headerTitle: 'TrustWeave',
          tabBarLabel: 'Wallet',
          headerRight: SettingsHeaderButton,
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'wallet' : 'wallet-outline'} size={24} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="scan"
        options={{
          title: 'Scan',
          headerShown: false,
          tabBarLabel: 'Scan',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'qr-code' : 'qr-code-outline'} size={24} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="activity"
        options={{
          title: 'Activity',
          headerTitle: 'Activity',
          tabBarLabel: 'Activity',
          headerRight: SettingsHeaderButton,
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'time' : 'time-outline'} size={24} color={color} />
          ),
        }}
      />
    </Tabs>
  )
}
