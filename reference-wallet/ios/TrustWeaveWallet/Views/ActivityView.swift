// ActivityView.swift — private local timeline with formatted relative timestamps.

import SwiftUI

struct ActivityView: View {
    @EnvironmentObject private var wallet: Wallet
    @Binding var showSettings: Bool
    @State private var confirmClear = false

    var body: some View {
        Group {
            if wallet.activity.isEmpty {
                ContentUnavailableView(
                    "No activity yet",
                    systemImage: "clock",
                    description: Text("Whenever you add a credential or share details with an organization, it will appear here.")
                )
            } else {
                List {
                    ForEach(wallet.activity) { entry in
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: symbol(for: entry.kind))
                                .font(.body)
                                .foregroundStyle(color(for: entry))
                                .frame(width: 34, height: 34)
                                .background(color(for: entry).opacity(0.14))
                                .clipShape(Circle())

                            VStack(alignment: .leading, spacing: 3) {
                                HStack(alignment: .firstTextBaseline) {
                                    Text("\(verb(for: entry.kind)): \(entry.title)")
                                        .font(.subheadline.bold())
                                        .lineLimit(1)
                                    Spacer(minLength: 6)
                                    Text(CredentialDisplay.formatRelativeTime(entry.timestamp))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                if let cp = entry.counterparty {
                                    let label = cp.hasPrefix("did:")
                                        ? "Issuer · \(IssuerTrust.shortDidFingerprint(cp))"
                                        : cp
                                    Text(entry.kind == "shared" ? "With \(label)" : label)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                } else if let sub = entry.subtitle {
                                    Text(sub)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                if let shared = entry.detailsShared, let total = entry.totalDetails {
                                    Text("Shared \(shared) of \(total) details")
                                        .font(.caption2.bold())
                                        .foregroundStyle(Color.trustWeaveAccent)
                                }
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
                .refreshable {
                    wallet.refreshPublishedState()
                }
            }
        }
        .navigationTitle("Activity")
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            if !wallet.activity.isEmpty {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Clear") { confirmClear = true }
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button { showSettings = true } label: { Image(systemName: "gearshape") }
                    .accessibilityLabel("Settings and security")
            }
        }
        .confirmationDialog("Clear activity history?", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Clear history", role: .destructive) { wallet.clearActivity() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This clears your local timeline only. Your credentials are not affected.")
        }
    }

    private func symbol(for kind: String) -> String {
        switch kind {
        case "added": return "plus.circle.fill"
        case "shared": return "paperplane.fill"
        case "removed": return "trash.fill"
        case "trusted_issuer": return "checkmark.shield.fill"
        case "backup": return "arrow.down.doc.fill"
        case "restored": return "arrow.up.doc.fill"
        default: return "clock.fill"
        }
    }

    private func verb(for kind: String) -> String {
        switch kind {
        case "added": return "Added"
        case "shared": return "Shared"
        case "removed": return "Removed"
        case "trusted_issuer": return "Trusted issuer"
        case "backup": return "Backup"
        case "restored": return "Restored"
        default: return "Event"
        }
    }

    private func color(for entry: ActivityEntry) -> Color {
        if !entry.success { return .red }
        return entry.kind == "shared" ? .trustWeaveAccent : .green
    }
}
