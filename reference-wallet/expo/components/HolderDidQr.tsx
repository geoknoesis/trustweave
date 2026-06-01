import { useMemo } from 'react'
import { StyleSheet, Text, View } from 'react-native'
import { holderQrPayload } from '@/lib/holderQr'
import { createQrModules } from '@/lib/qrCreate'
import { QrMatrix } from '@/lib/qrMatrix'

interface HolderDidQrProps {
  did: string
  size?: number
}

export function HolderDidQr({ did, size = 168 }: HolderDidQrProps) {
  const payload = holderQrPayload(did)
  const modules = useMemo(() => createQrModules(payload), [payload])

  return (
    <View style={s.wrap}>
      <View style={s.frame}>
        {modules ? (
          <QrMatrix modules={modules} size={size} />
        ) : (
          <View style={[s.errorBox, { width: size, height: size }]}>
            <Text style={s.errorText}>Unable to generate QR</Text>
          </View>
        )}
      </View>
      <Text style={s.label}>SHARE YOUR IDENTITY</Text>
      <Text style={s.caption}>Let a verifier scan this before you share a credential</Text>
    </View>
  )
}

const s = StyleSheet.create({
  wrap: { alignItems: 'center', gap: 8, paddingVertical: 8 },
  frame: {
    padding: 12,
    backgroundColor: '#ffffff',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e2e8f0',
  },
  errorBox: { alignItems: 'center', justifyContent: 'center' },
  errorText: { color: '#dc2626', fontSize: 12, textAlign: 'center' },
  label: { color: '#64748b', fontSize: 11, letterSpacing: 0.5, textTransform: 'uppercase' },
  caption: { color: '#64748b', fontSize: 13, textAlign: 'center' },
})
