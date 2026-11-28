// SettingsView.swift — security, honest key custody, trusted issuers, backup/restore, CIQRCodeGenerator Wallet ID QR, copy, and safe reset.

import SwiftUI
import UIKit

struct SettingsView: View {
    @EnvironmentObject private var wallet: Wallet
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedTab: WalletTab

    private let backend = DemoBackend()

    @State private var showWalletId = false
    @State private var copiedWalletId = false
    @State private var showRestoreBox = false
    @State private var restoreText = ""
    @State private var exportedBackup: String?
    @State private var notice: String?
    @State private var pendingRestoreIssuer: String?
    @State private var resetConfirmStep = 0

    var body: some View {
        Form {
            if let notice {
                Section {
                    Label(notice, systemImage: "info.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(Color.trustWeaveAccent)
                }
            }

            Section("Security & Privacy") {
                Toggle(
                    "Require \(BiometricAuth.biometryLabel)",
                    isOn: Binding(
                        get: { wallet.preferences.biometricLockEnabled },
                        set: { value in wallet.updatePreferences { $0.biometricLockEnabled = value } }
                    )
                )

                VStack(alignment: .leading, spacing: 4) {
                    Label("Encrypted on-device key (Keychain)", systemImage: "key.fill")
                        .font(.subheadline.bold())
                    Text("Your Ed25519 seed is stored encrypted in iOS Keychain on this device only and loaded into memory to sign. Hardware Secure Enclave isolation is not active for Ed25519.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }

            Section {
                if wallet.configuredIssuers.isEmpty && wallet.acceptedIssuers.isEmpty {
                    Text("No custom issuers confirmed yet.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                ForEach(Array(wallet.configuredIssuers).sorted(), id: \.self) { did in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Issuer · \(IssuerTrust.shortDidFingerprint(did))").font(.subheadline.bold())
                            Text(did).font(.system(.caption2, design: .monospaced)).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer()
                        Text("Built-in")
                            .font(.caption2.bold())
                            .foregroundStyle(.green)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.green.opacity(0.14))
                            .clipShape(Capsule())
                    }
                }
                ForEach(wallet.acceptedIssuers, id: \.self) { did in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Issuer · \(IssuerTrust.shortDidFingerprint(did))").font(.subheadline.bold())
                            Text(did).font(.system(.caption2, design: .monospaced)).foregroundStyle(.secondary).lineLimit(1)
                        }
                        Spacer()
                        Button("Remove", role: .destructive) {
                            wallet.removeAcceptedIssuer(did)
                        }
                        .font(.caption.bold())
                    }
                }
            } header: {
                Text("Trusted Issuers")
            } footer: {
                Text("Credentials are accepted only from built-in issuers, this wallet's configured backend, or issuers you explicitly confirmed.")
            }

            Section {
                if let backup = exportedBackup {
                    ShareLink(item: backup, subject: Text("TrustWeave Credential Backup")) {
                        Label("Share exported backup", systemImage: "square.and.arrow.up")
                    }
                } else {
                    Button {
                        do {
                            exportedBackup = try wallet.exportCredentialBackup()
                            Haptics.success()
                            notice = "Backup prepared. Remember: private keys are never included."
                        } catch {
                            notice = error.localizedDescription
                        }
                    } label: {
                        Label("Prepare credential backup", systemImage: "arrow.down.doc")
                    }
                }

                Button {
                    showRestoreBox.toggle()
                } label: {
                    Label(showRestoreBox ? "Hide restore field" : "Restore from backup", systemImage: "arrow.up.doc")
                }

                if showRestoreBox {
                    TextEditor(text: $restoreText)
                        .font(.system(.caption, design: .monospaced))
                        .frame(minHeight: 88)
                    Button("Verify & restore") {
                        runRestore(confirmedIssuer: nil)
                    }
                    .disabled(restoreText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            } header: {
                Text("Backup & Recovery")
            } footer: {
                Text("Exports credential records only — your private signing key stays on this device. If you lose this phone, create a new wallet and ask issuers to reissue your credentials.")
            }

            Section("My Wallet ID") {
                DisclosureGroup("Show Wallet ID QR", isExpanded: $showWalletId) {
                    if let did = wallet.holderDid {
                        VStack(spacing: 10) {
                            if let qr = QrCodeGenerator.image(from: "{\"v\":1,\"type\":\"trustweave-holder\",\"did\":\"\(did)\"}") {
                                Image(uiImage: qr)
                                    .interpolation(.none)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 180, height: 180)
                                    .padding(10)
                                    .background(Color.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                    .accessibilityLabel("Your Wallet ID QR code")
                            }
                            Text("Show this QR only if an issuer or verifier asks to identify your wallet.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                            Text(did)
                                .font(.system(.caption2, design: .monospaced))
                                .foregroundStyle(.secondary)
                                .textSelection(.enabled)
                                .multilineTextAlignment(.center)
                            Button {
                                UIPasteboard.general.string = did
                                Haptics.selection()
                                copiedWalletId = true
                                DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                                    copiedWalletId = false
                                }
                            } label: {
                                Label(copiedWalletId ? "Copied!" : "Copy Wallet ID", systemImage: copiedWalletId ? "checkmark" : "doc.on.doc")
                                    .font(.subheadline.bold())
                            }
                            .buttonStyle(.bordered)
                            .buttonBorderShape(.capsule)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                    }
                }
            }

            Section("Advanced") {
                Toggle(
                    "Developer mode",
                    isOn: Binding(
                        get: { wallet.preferences.developerMode },
                        set: { value in wallet.updatePreferences { $0.developerMode = value } }
                    )
                )
                if wallet.preferences.developerMode {
                    LabeledContent("Backend", value: backend.baseURL)
                        .font(.system(.caption, design: .monospaced))
                }
            }

            Section {
                Button(role: .destructive) {
                    resetConfirmStep = 1
                } label: {
                    Label("Reset wallet", systemImage: "trash")
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            }
        }
        .navigationTitle("Settings & security")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Done") { dismiss() }
            }
        }
        .sheet(
            isPresented: Binding(
                get: { pendingRestoreIssuer != nil },
                set: { if !$0 { pendingRestoreIssuer = nil } }
            )
        ) {
            if let did = pendingRestoreIssuer {
                UnrecognizedIssuerSheet(
                    issuerDid: did,
                    credentialTitle: nil,
                    onCancel: { pendingRestoreIssuer = nil },
                    onTrustAndAdd: {
                        pendingRestoreIssuer = nil
                        runRestore(confirmedIssuer: did)
                    }
                )
            }
        }
        .alert(
            "Reset wallet?",
            isPresented: Binding(get: { resetConfirmStep == 1 }, set: { if !$0 { resetConfirmStep = 0 } })
        ) {
            Button("Continue", role: .destructive) { resetConfirmStep = 2 }
            Button("Cancel", role: .cancel) { resetConfirmStep = 0 }
        } message: {
            Text("This permanently removes all stored credentials, trusted issuers, and your on-device signing key.")
        }
        .alert(
            "Final confirmation",
            isPresented: Binding(get: { resetConfirmStep == 2 }, set: { if !$0 { resetConfirmStep = 0 } })
        ) {
            Button("Reset wallet", role: .destructive) {
                resetConfirmStep = 0
                Task {
                    if wallet.preferences.biometricLockEnabled {
                        let ok = await BiometricAuth.confirm(reason: "Authenticate to permanently reset your wallet")
                        guard ok else { return }
                    }
                    try? wallet.reset()
                    Haptics.warning()
                    selectedTab = .wallet
                    dismiss()
                }
            }
            Button("Cancel", role: .cancel) { resetConfirmStep = 0 }
        } message: {
            Text("This cannot be undone. You will need issuers to reissue your credentials after resetting.")
        }
    }

    private func runRestore(confirmedIssuer: String?) {
        Task {
            let backendIssuers = await backend.backendIssuers()
            do {
                let res = try wallet.restoreCredentialBackup(
                    rawJson: restoreText,
                    backendIssuers: backendIssuers,
                    confirmedIssuer: confirmedIssuer
                )
                if let firstUntrusted = res.untrustedIssuers.first, confirmedIssuer == nil {
                    pendingRestoreIssuer = firstUntrusted
                    return
                }
                restoreText = ""
                showRestoreBox = false
                Haptics.success()
                notice = "Restored \(res.restored) credential(s)\(res.skipped > 0 ? " (\(res.skipped) skipped)" : "")."
            } catch {
                Haptics.error()
                notice = error.localizedDescription
            }
        }
    }
}
