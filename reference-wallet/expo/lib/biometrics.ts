/**
 * Graceful biometric / device confirmation helper for the Expo mobile wallet.
 *
 * Uses `expo-local-authentication` automatically when installed in a native build,
 * and falls back cleanly to an explicit in-app confirmation dialog when running
 * without that native module (e.g., web preview or minimal Expo Go builds).
 */
import { Alert, Platform } from 'react-native'

interface LocalAuthModule {
  hasHardwareAsync?: () => Promise<boolean>
  isEnrolledAsync?: () => Promise<boolean>
  authenticateAsync?: (opts: {
    promptMessage: string
    cancelLabel?: string
    fallbackLabel?: string
    disableDeviceFallback?: boolean
  }) => Promise<{ success: boolean; error?: string }>
}

function loadOptionalLocalAuth(): LocalAuthModule | null {
  if (Platform.OS === 'web') return null
  try {
    // Dynamic require avoids hard crash when optional native module isn't linked.
    const moduleName = 'expo-local-authentication'
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    return require(moduleName) as LocalAuthModule
  } catch {
    return null
  }
}

export async function getBiometricAvailability(): Promise<{
  nativeBiometricsAvailable: boolean
  label: string
}> {
  const mod = loadOptionalLocalAuth()
  if (mod?.hasHardwareAsync && mod?.isEnrolledAsync) {
    try {
      const [hw, enrolled] = await Promise.all([mod.hasHardwareAsync(), mod.isEnrolledAsync()])
      if (hw && enrolled) {
        return {
          nativeBiometricsAvailable: true,
          label: Platform.OS === 'ios' ? 'Face ID / Touch ID' : 'Fingerprint / Biometrics',
        }
      }
    } catch {
      // fall through
    }
  }
  return {
    nativeBiometricsAvailable: false,
    label: Platform.OS === 'ios' ? 'Device confirmation' : 'Device confirmation',
  }
}

/**
 * Request explicit biometric or confirmation approval before sensitive actions
 * (sharing a credential or resetting the wallet).
 */
export async function confirmSensitiveAction(reason: string): Promise<boolean> {
  const mod = loadOptionalLocalAuth()
  if (mod?.hasHardwareAsync && mod?.isEnrolledAsync && mod?.authenticateAsync) {
    try {
      const [hw, enrolled] = await Promise.all([mod.hasHardwareAsync(), mod.isEnrolledAsync()])
      if (hw && enrolled) {
        const res = await mod.authenticateAsync({
          promptMessage: reason,
          cancelLabel: 'Cancel',
          fallbackLabel: 'Use passcode',
          disableDeviceFallback: false,
        })
        return Boolean(res.success)
      }
    } catch {
      return false
    }
  }

  // Fallback confirmation dialog when native LocalAuthentication isn't linked.
  return new Promise<boolean>((resolve) => {
    Alert.alert('Confirm action', reason, [
      { text: 'Cancel', style: 'cancel', onPress: () => resolve(false) },
      { text: 'Confirm', onPress: () => resolve(true) },
    ])
  })
}
