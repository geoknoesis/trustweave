/**
 * Cross-platform clipboard copy helper (uses expo-clipboard when linked,
 * navigator.clipboard on web, or Share sheet fallback on bare native builds).
 */
import { Platform, Share } from 'react-native'

interface ExpoClipboardModule {
  setStringAsync?: (text: string) => Promise<boolean>
}

function loadOptionalClipboard(): ExpoClipboardModule | null {
  if (Platform.OS === 'web') return null
  try {
    const modName = 'expo-clipboard'
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    return require(modName) as ExpoClipboardModule
  } catch {
    return null
  }
}

export async function copyText(text: string, shareTitle = 'Copy value'): Promise<'copied' | 'shared'> {
  if (Platform.OS === 'web' && typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text)
    return 'copied'
  }
  const mod = loadOptionalClipboard()
  if (mod?.setStringAsync) {
    await mod.setStringAsync(text)
    return 'copied'
  }
  await Share.share({ title: shareTitle, message: text })
  return 'shared'
}
