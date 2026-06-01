import React, { Component, type ReactNode } from 'react'
import { ScrollView, StyleSheet, Text, View } from 'react-native'
import { theme } from '@/lib/credentialDisplay'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  render() {
    if (this.state.error) {
      return (
        <View style={s.root}>
          <Text style={s.title}>Something went wrong</Text>
          <ScrollView style={s.scroll}>
            <Text style={s.message}>{this.state.error.message}</Text>
          </ScrollView>
        </View>
      )
    }
    return this.props.children
  }
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: theme.bg, padding: 24, paddingTop: 48 },
  title: { fontSize: 18, fontWeight: '700', color: '#dc2626', marginBottom: 12 },
  scroll: { flex: 1 },
  message: { fontFamily: 'Menlo', fontSize: 12, color: theme.text },
})
