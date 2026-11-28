import React, { Component, type ReactNode } from 'react'
import { Appearance, Pressable, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { Ionicons } from '@expo/vector-icons'
import { darkTheme, lightTheme, monoFont } from '@/lib/credentialDisplay'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
  showDetails: boolean
}

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { error: null, showDetails: false }

  static getDerivedStateFromError(error: Error): Partial<State> {
    return { error }
  }

  private handleReload = () => {
    this.setState({ error: null, showDetails: false })
  }

  render() {
    if (this.state.error) {
      const t = Appearance.getColorScheme() === 'dark' ? darkTheme : lightTheme
      return (
        <View style={[s.root, { backgroundColor: t.bg }]}>
          <View style={[s.card, { backgroundColor: t.surface, borderColor: t.border }]}>
            <View style={[s.iconWrap, { backgroundColor: t.dangerBg }]}>
              <Ionicons name="alert-circle-outline" size={40} color={t.danger} />
            </View>
            <Text style={[s.title, { color: t.text }]}>Something went wrong</Text>
            <Text style={[s.body, { color: t.textMuted }]}>
              The wallet ran into an unexpected problem. Your stored credentials are safe — try reloading.
            </Text>
            <TouchableOpacity
              style={[s.primaryBtn, { backgroundColor: t.primary }]}
              onPress={this.handleReload}
              accessibilityRole="button"
              accessibilityLabel="Reload wallet"
            >
              <Text style={[s.primaryBtnText, { color: t.onPrimary }]}>Reload wallet</Text>
            </TouchableOpacity>
            <Pressable
              onPress={() => this.setState((prev) => ({ showDetails: !prev.showDetails }))}
              style={s.detailsToggle}
              accessibilityRole="button"
              accessibilityState={{ expanded: this.state.showDetails }}
            >
              <Text style={[s.detailsToggleText, { color: t.primary }]}>
                {this.state.showDetails ? 'Hide technical details' : 'Show technical details'}
              </Text>
            </Pressable>
            {this.state.showDetails && (
              <ScrollView style={[s.scroll, { backgroundColor: t.surfaceElevated }]}>
                <Text style={[s.message, { color: t.textMuted }]}>{this.state.error.message}</Text>
              </ScrollView>
            )}
          </View>
        </View>
      )
    }
    return this.props.children
  }
}

const s = StyleSheet.create({
  root: { flex: 1, padding: 24, justifyContent: 'center' },
  card: {
    borderRadius: 16,
    padding: 24,
    alignItems: 'center',
    gap: 12,
    borderWidth: 1,
  },
  iconWrap: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: { fontSize: 20, fontWeight: '700', textAlign: 'center' },
  body: { fontSize: 14, textAlign: 'center', lineHeight: 20 },
  primaryBtn: {
    paddingVertical: 14,
    paddingHorizontal: 28,
    borderRadius: 999,
    minHeight: 48,
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 4,
    width: '100%',
  },
  primaryBtnText: { fontWeight: '700', fontSize: 15 },
  detailsToggle: { paddingVertical: 8 },
  detailsToggleText: { fontSize: 13, fontWeight: '600' },
  scroll: { maxHeight: 160, width: '100%', borderRadius: 8, padding: 10 },
  message: { fontFamily: monoFont, fontSize: 11 },
})
