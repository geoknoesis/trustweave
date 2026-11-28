import { useMemo, useState } from 'react'
import { StyleSheet, Text, TouchableOpacity, View } from 'react-native'
import { Ionicons } from '@expo/vector-icons'
import { copyText } from '@/lib/clipboard'
import { haptics } from '@/lib/haptics'
import { holderQrPayload } from '@/lib/holderQr'
import { createQrModules } from '@/lib/qrCreate'
import { QrMatrix } from '@/lib/qrMatrix'
import { monoFont, useWalletTheme } from '@/lib/credentialDisplay'

interface HolderDidQrProps {
  did: string
  size?: number
}

export function HolderDidQr({ did, size = 168 }: HolderDidQrProps) {
  const t = useWalletTheme()
  const [copied, setCopied] = useState(false)
  const payload = holderQrPayload(did)
  const modules = useMemo(() => createQrModules(payload), [payload])

  const handleCopy = async () => {
    try {
      const outcome = await copyText(did, 'My Wallet ID')
      haptics.selection()
      if (outcome === 'copied') {
        setCopied(true)
        setTimeout(() => setCopied(false), 2000)
      }
    } catch {
      // ignore user share dismiss
    }
  }

  return (
    <View style={s.wrap}>
      <View
        style={[s.frame, { borderColor: t.border }]}
        accessibilityLabel="Your Wallet ID QR code"
        accessibilityRole="image"
      >
        {modules ? (
          <QrMatrix modules={modules} size={size} />
        ) : (
          <View style={[s.errorBox, { width: size, height: size }]}>
            <Text style={[s.errorText, { color: t.danger }]}>Unable to generate QR</Text>
          </View>
        )}
      </View>
      <Text style={[s.label, { color: t.textMuted }]}>YOUR WALLET ID</Text>
      <Text style={[s.caption, { color: t.textMuted }]}>
        Show this QR only if an issuer or verifier asks to identify your wallet.
      </Text>
      <Text style={[s.mono, { color: t.textMuted }]} selectable numberOfLines={2}>
        {did}
      </Text>
      <TouchableOpacity
        style={[s.copyBtn, { borderColor: t.primary, backgroundColor: t.infoBg }]}
        onPress={handleCopy}
        accessibilityRole="button"
        accessibilityLabel="Copy Wallet ID"
      >
        <Ionicons
          name={copied ? 'checkmark-outline' : 'copy-outline'}
          size={16}
          color={t.primary}
        />
        <Text style={[s.copyText, { color: t.primary }]}>
          {copied ? 'Copied!' : 'Copy Wallet ID'}
        </Text>
      </TouchableOpacity>
    </View>
  )
}

const s = StyleSheet.create({
  wrap: { alignItems: 'center', gap: 8, paddingVertical: 8 },
  frame: {
    padding: 12,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    borderWidth: 1,
  },
  errorBox: { alignItems: 'center', justifyContent: 'center' },
  errorText: { fontSize: 12, textAlign: 'center' },
  label: { fontSize: 11, fontWeight: '700', letterSpacing: 0.6, textTransform: 'uppercase' },
  caption: { fontSize: 13, textAlign: 'center', lineHeight: 18 },
  mono: { fontFamily: monoFont, fontSize: 11, textAlign: 'center', paddingHorizontal: 8 },
  copyBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 16,
    paddingVertical: 10,
    minHeight: 44,
    borderRadius: 999,
    borderWidth: 1,
    marginTop: 4,
  },
  copyText: { fontSize: 14, fontWeight: '700' },
})
