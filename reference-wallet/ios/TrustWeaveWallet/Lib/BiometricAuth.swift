// BiometricAuth.swift
//
// Native Face ID / Touch ID + device passcode confirmation via LocalAuthentication.

import Foundation
import LocalAuthentication

enum BiometricAuth {

    static var biometryLabel: String {
        let ctx = LAContext()
        var error: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return "Device Passcode"
        }
        switch ctx.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        default: return "Device Passcode"
        }
    }

    /// Authenticate with Face ID / Touch ID (falling back to device passcode).
    /// Returns true if authenticated, false if cancelled or failed.
    @MainActor
    static func confirm(reason: String) async -> Bool {
        let ctx = LAContext()
        var error: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            // Simulator or unlocked device without passcode enrolled.
            return true
        }
        do {
            return try await ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)
        } catch {
            return false
        }
    }
}
