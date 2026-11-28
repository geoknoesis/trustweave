// OnboardingView.swift — calm 2-step first-run onboarding with Face ID / Touch ID setup.

import SwiftUI

struct OnboardingView: View {
    let onComplete: (Bool) -> Void

    @State private var step = 0
    @State private var biometricEnabled = true

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            if step == 0 {
                VStack(spacing: 16) {
                    Image(systemName: "wallet.pass.fill")
                        .font(.system(size: 52))
                        .foregroundStyle(Color.trustWeaveAccent)
                        .padding(22)
                        .background(Color.trustWeaveAccent.opacity(0.12))
                        .clipShape(Circle())

                    Text("Your private credential wallet")
                        .font(.title2.bold())
                        .multilineTextAlignment(.center)

                    Text("Keep verified diplomas, IDs, and passes safely on this iPhone — and choose exactly which details you share.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)

                    VStack(alignment: .leading, spacing: 12) {
                        featureRow(symbol: "checkmark.shield", text: "Verifies issuer signatures before saving any credential.")
                        featureRow(symbol: "eye.slash", text: "Share only what is needed. Everything switched off stays hidden.")
                        featureRow(symbol: "iphone", text: "Your credentials and signing key stay private on this device.")
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
            } else {
                VStack(spacing: 16) {
                    Image(systemName: "faceid")
                        .font(.system(size: 52))
                        .foregroundStyle(.green)
                        .padding(22)
                        .background(Color.green.opacity(0.12))
                        .clipShape(Circle())

                    Text("Protect your wallet")
                        .font(.title2.bold())
                        .multilineTextAlignment(.center)

                    Text("Require \(BiometricAuth.biometryLabel) before sharing credentials or resetting your wallet.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)

                    Toggle(isOn: $biometricEnabled) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Require \(BiometricAuth.biometryLabel)").font(.headline)
                            Text("Recommended when sharing personal details.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .padding(16)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    Text("Your signing key is stored encrypted in iOS Keychain on this device and does not sync to other devices.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
            }

            Spacer()

            VStack(spacing: 8) {
                Button {
                    if step == 0 { step = 1 } else { onComplete(biometricEnabled) }
                } label: {
                    Text(step == 0 ? "Continue" : "Start using wallet")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)

                Button(step == 0 ? "Skip setup" : "Back") {
                    if step == 0 { onComplete(true) } else { step = 0 }
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .padding(.vertical, 6)
            }
        }
        .padding(24)
    }

    private func featureRow(symbol: String, text: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: symbol)
                .foregroundStyle(Color.trustWeaveAccent)
                .frame(width: 24)
            Text(text).font(.subheadline)
        }
    }
}
