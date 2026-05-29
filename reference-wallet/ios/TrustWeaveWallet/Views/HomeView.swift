// HomeView.swift — wallet identity + credentials list.

import SwiftUI

struct HomeView: View {
    @StateObject private var wallet = Wallet()
    @State private var bootstrapError: String?
    @State private var showResetAlert = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                identityPanel
                credentialsPanel
                dangerZone
            }
            .padding(16)
        }
        .navigationTitle("Your wallet")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            do { try wallet.bootstrap() } catch { bootstrapError = "\(error)" }
        }
        .alert("Wallet bootstrap failed", isPresented: .constant(bootstrapError != nil), actions: {
            Button("OK") { bootstrapError = nil }
        }, message: {
            if let e = bootstrapError { Text(e) }
        })
        .alert("Reset wallet?", isPresented: $showResetAlert, actions: {
            Button("Reset", role: .destructive) { try? wallet.reset() }
            Button("Cancel", role: .cancel) {}
        }, message: {
            Text("Wipes your holder identity AND every stored credential. Cannot be undone.")
        })
    }

    private var identityPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Holder DID").font(.caption).foregroundStyle(.secondary)
            Text(wallet.holderDid ?? "—").font(.system(.caption, design: .monospaced))
            Text("🔒 Key in iOS Keychain (CryptoKit Ed25519)")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .padding(.top, 4)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemBackground))
        .cornerRadius(8)
    }

    private var credentialsPanel: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Credentials (\(wallet.credentials.count))").font(.headline)
            if wallet.credentials.isEmpty {
                emptyState
            } else {
                ForEach(wallet.credentials) { c in
                    credentialCard(c)
                }
            }
        }
    }

    private func credentialCard(_ c: StoredCredential) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "graduationcap.fill")
                .foregroundColor(.accentColor)
                .font(.title2)
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text(c.previewTitle).font(.headline)
                    Text("(\(c.format))").font(.caption2).foregroundStyle(.tertiary)
                }
                if let sub = c.previewSubtitle {
                    Text(sub).font(.subheadline).foregroundStyle(.secondary)
                }
                Text("issued by \(c.issuerDid.prefix(30))…")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
            Spacer()
            Button { wallet.deleteCredential(id: c.id) } label: {
                Image(systemName: "trash").foregroundStyle(.secondary)
            }
        }
        .padding(12)
        .background(Color(uiColor: .systemBackground))
        .cornerRadius(8)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(uiColor: .separator), lineWidth: 0.5))
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "tray").font(.largeTitle).foregroundStyle(.secondary)
            Text("No credentials yet.").foregroundStyle(.secondary)
            NavigationLink("Receive a demo credential", destination: ReceiveView())
                .buttonStyle(.borderedProminent)
                .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
    }

    private var dangerZone: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Danger zone").font(.headline)
            Text("Wipe the wallet — irrecoverable.").font(.caption).foregroundStyle(.secondary)
            Button("Reset wallet", role: .destructive) { showResetAlert = true }
                .buttonStyle(.bordered)
        }
        .padding(.top, 8)
    }
}
