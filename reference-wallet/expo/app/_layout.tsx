import '../polyfills'

import { Tabs } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import { Text } from 'react-native'
import { SafeAreaProvider } from 'react-native-safe-area-context'
import { AppErrorBoundary } from '@/components/AppErrorBoundary'
import { theme } from '@/lib/credentialDisplay'

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <AppErrorBoundary>
        <StatusBar style="light" />
        <Tabs
          screenOptions={{
            tabBarActiveTintColor: theme.primary,
            tabBarInactiveTintColor: theme.textMuted,
            tabBarStyle: {
              backgroundColor: theme.surface,
              borderTopColor: theme.border,
              paddingTop: 4,
            },
            headerStyle: { backgroundColor: theme.primary },
            headerTintColor: '#ffffff',
            headerTitleStyle: { fontWeight: '600' },
            sceneStyle: { backgroundColor: theme.bg },
          }}
        >
          <Tabs.Screen
            name="index"
            options={{
              title: 'My credentials',
              headerTitle: 'My credentials',
              tabBarLabel: 'Library',
              tabBarIcon: ({ color }) => <TabIcon color={color}>📚</TabIcon>,
            }}
          />
          <Tabs.Screen
            name="receive"
            options={{
              title: 'Add credential',
              headerTitle: 'Add credential',
              tabBarLabel: 'Add',
              tabBarIcon: ({ color }) => <TabIcon color={color}>➕</TabIcon>,
            }}
          />
          <Tabs.Screen
            name="present"
            options={{
              title: 'Share credential',
              headerTitle: 'Share credential',
              tabBarLabel: 'Share',
              tabBarIcon: ({ color }) => <TabIcon color={color}>📤</TabIcon>,
            }}
          />
        </Tabs>
      </AppErrorBoundary>
    </SafeAreaProvider>
  )
}

function TabIcon({ children, color }: { children: string; color: string }) {
  return <Text style={{ fontSize: 20, opacity: color === theme.primary ? 1 : 0.55 }}>{children}</Text>
}
