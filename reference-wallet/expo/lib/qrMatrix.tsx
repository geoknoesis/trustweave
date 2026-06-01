import { StyleSheet, View } from 'react-native'
import type { QrModules } from '@/lib/qrCreate'

const DARK = '#003399'
const LIGHT = '#ffffff'

/** Render a QR code in React Native without Canvas. */
export function QrMatrix({ modules, size }: { modules: QrModules; size: number }) {
  const moduleCount = modules.size
  const cellSize = size / moduleCount

  return (
    <View style={[s.root, { width: size, height: size }]}>
      {Array.from({ length: moduleCount }, (_, row) => (
        <View key={row} style={[s.row, { height: cellSize }]}>
          {Array.from({ length: moduleCount }, (_, col) => (
            <View
              key={col}
              style={{
                width: cellSize,
                height: cellSize,
                backgroundColor: modules.get(row, col) ? DARK : LIGHT,
              }}
            />
          ))}
        </View>
      ))}
    </View>
  )
}

const s = StyleSheet.create({
  root: { backgroundColor: LIGHT },
  row: { flexDirection: 'row' },
})
