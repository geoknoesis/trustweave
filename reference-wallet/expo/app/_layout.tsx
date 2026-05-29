import '../polyfills'

import { Tabs } from 'expo-router'
import { StatusBar } from 'expo-status-bar'

const PRIMARY = '#1e3a8a'

export default function RootLayout() {
  return (
    <>
      <StatusBar style="light" />
      <Tabs
        screenOptions={{
          tabBarActiveTintColor: PRIMARY,
          headerStyle: { backgroundColor: PRIMARY },
          headerTintColor: '#ffffff',
          headerTitleStyle: { fontWeight: '600' },
        }}
      >
        <Tabs.Screen
          name="index"
          options={{
            title: 'Wallet',
            tabBarIcon: ({ color }) => <TabIcon color={color}>💳</TabIcon>,
          }}
        />
        <Tabs.Screen
          name="receive"
          options={{
            title: 'Receive',
            tabBarIcon: ({ color }) => <TabIcon color={color}>⬇️</TabIcon>,
          }}
        />
        <Tabs.Screen
          name="present"
          options={{
            title: 'Present',
            tabBarIcon: ({ color }) => <TabIcon color={color}>⬆️</TabIcon>,
          }}
        />
      </Tabs>
    </>
  )
}

import { Text } from 'react-native'

function TabIcon({ children, color }: { children: string; color: string }) {
  // Emoji rendered as text tinted via opacity — Expo Go ships no icon font by default,
  // and adding @expo/vector-icons just for tab icons would bloat the demo.
  return <Text style={{ fontSize: 18, opacity: color === '#1e3a8a' ? 1 : 0.6 }}>{children}</Text>
}
