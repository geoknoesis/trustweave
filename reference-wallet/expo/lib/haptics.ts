/**
 * Tactile haptic feedback helper.
 * Uses `expo-haptics` automatically when installed in a native build,
 * and falls back cleanly to crisp platform-tuned Vibration patterns otherwise.
 */
import { Platform, Vibration } from 'react-native'

interface ExpoHapticsModule {
  notificationAsync?: (type: string) => Promise<void>
  selectionAsync?: () => Promise<void>
  NotificationFeedbackType?: { Success: string; Warning: string; Error: string }
}

function loadOptionalHaptics(): ExpoHapticsModule | null {
  if (Platform.OS === 'web') return null
  try {
    const modName = 'expo-haptics'
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    return require(modName) as ExpoHapticsModule
  } catch {
    return null
  }
}

export const haptics = {
  selection() {
    if (Platform.OS === 'web') return
    const mod = loadOptionalHaptics()
    if (mod?.selectionAsync) {
      void mod.selectionAsync().catch(() => {})
      return
    }
    Vibration.vibrate(10)
  },
  success() {
    if (Platform.OS === 'web') return
    const mod = loadOptionalHaptics()
    if (mod?.notificationAsync && mod.NotificationFeedbackType?.Success) {
      void mod.notificationAsync(mod.NotificationFeedbackType.Success).catch(() => {})
      return
    }
    Vibration.vibrate(25)
  },
  warning() {
    if (Platform.OS === 'web') return
    const mod = loadOptionalHaptics()
    if (mod?.notificationAsync && mod.NotificationFeedbackType?.Warning) {
      void mod.notificationAsync(mod.NotificationFeedbackType.Warning).catch(() => {})
      return
    }
    Vibration.vibrate([0, 20, 35, 20])
  },
  error() {
    if (Platform.OS === 'web') return
    const mod = loadOptionalHaptics()
    if (mod?.notificationAsync && mod.NotificationFeedbackType?.Error) {
      void mod.notificationAsync(mod.NotificationFeedbackType.Error).catch(() => {})
      return
    }
    Vibration.vibrate([0, 35, 45, 35])
  },
}
