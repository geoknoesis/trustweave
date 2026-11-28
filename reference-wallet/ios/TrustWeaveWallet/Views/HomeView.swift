// HomeView.swift — Apple Wallet-inspired pass cards with portraits, detail sheet, haptics, and safe removal.

import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var wallet: Wallet
    @Binding var selectedTab: WalletTab
    @Binding var activeSheet: WalletFlowSheet?
    @Binding var showSettings: Bool

    @State private var searchText = ""
    @State private var detailCred: StoredCredential?
    @State private var pendingDeleteCred: StoredCredential?

    private var filtered: [StoredCredential] {
        let q = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return wallet.credentials }
        return wallet.credentials.filter { c in
            c.previewTitle.lowercased().contains(q)
                || (c.previewSubtitle?.lowercased().contains(q) ?? false)
                || c.type.contains { $0.lowercased().contains(q) }
        }
    }

    private var expiredCount: Int {
        wallet.credentials.filter { CredentialDisplay.statusBadge(for: $0).label == "Expired" }.count
    }

    private var expiringSoonCount: Int {
        wallet.credentials.filter { CredentialDisplay.statusBadge(for: $0).label == "Expiring soon" }.count
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(
                    wallet.credentials.isEmpty
                        ? "Private IDs, diplomas, and passes on this device."
                        : "\(wallet.credentials.count) credential\(wallet.credentials.count == 1 ? "" : "s") ready to present."
                )
                .font(.subheadline)
                .foregroundStyle(.secondary)

                if expiredCount > 0 || expiringSoonCount > 0 {
                    let isExpired = expiredCount > 0
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: isExpired ? "exclamationmark.triangle.fill" : "clock.badge.exclamationmark")
                            .foregroundStyle(isExpired ? .red : .orange)
                            .font(.title3)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(
                                isExpired
                                    ? "\(expiredCount) credential\(expiredCount == 1 ? "" : "s") expired"
                                    : "\(expiringSoonCount) credential\(expiringSoonCount == 1 ? "" : "s") expiring soon"
                            )
                            .font(.subheadline.bold())
                            .foregroundStyle(isExpired ? .red : .orange)
                            Text(
                                isExpired
                                    ? "Expired credentials may be declined by verifiers. Request a renewal from the issuer."
                                    : "Check expiration dates below so you can renew before they lapse."
                            )
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(14)
                    .background((isExpired ? Color.red : Color.orange).opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .accessibilityElement(children: .combine)
                }

                if wallet.credentials.isEmpty {
                    emptyState
                } else if filtered.isEmpty {
                    ContentUnavailableView.search(text: searchText)
                } else {
                    ForEach(filtered) { cred in
                        Button {
                            Haptics.selection()
                            detailCred = cred
                        } label: {
                            CredentialPassCard(cred: cred)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
        }
        .refreshable {
            wallet.refreshPublishedState()
        }
        .navigationTitle("My credentials")
        .navigationBarTitleDisplayMode(.large)
        .modifier(ConditionalSearchable(enabled: wallet.credentials.count >= 4, text: $searchText))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showSettings = true
                } label: {
                    Image(systemName: "gearshape")
                }
                .accessibilityLabel("Settings and security")
            }
        }
        .sheet(item: $detailCred) { cred in
            CredentialDetailSheet(
                cred: cred,
                onShare: {
                    detailCred = nil
                    wallet.preselectedCredentialId = cred.id
                    activeSheet = .present
                },
                onRequestDelete: {
                    pendingDeleteCred = cred
                }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
        .confirmationDialog(
            "Remove \"\(pendingDeleteCred?.previewTitle ?? "credential")\"?",
            isPresented: Binding(
                get: { pendingDeleteCred != nil },
                set: { if !$0 { pendingDeleteCred = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Remove from wallet", role: .destructive) {
                if let target = pendingDeleteCred {
                    wallet.deleteCredential(id: target.id)
                    Haptics.warning()
                    if detailCred?.id == target.id { detailCred = nil }
                }
                pendingDeleteCred = nil
            }
            Button("Cancel", role: .cancel) { pendingDeleteCred = nil }
        } message: {
            Text("This removes the credential from this device. You will need the issuer to reissue it if you want it back.")
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "wallet.pass")
                .font(.system(size: 40))
                .foregroundStyle(Color.trustWeaveAccent)
                .padding(18)
                .background(Color.trustWeaveAccent.opacity(0.12))
                .clipShape(Circle())
            Text("Your wallet is ready")
                .font(.headline)
            Text("Keep your verified IDs, diplomas, and passes in one private place. Scan a QR code or tap an NFC reader to add your first card.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button {
                selectedTab = .scan
            } label: {
                Label("Scan QR or tap NFC", systemImage: "qrcode.viewfinder")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

private struct ConditionalSearchable: ViewModifier {
    let enabled: Bool
    @Binding var text: String

    func body(content: Content) -> some View {
        if enabled {
            content.searchable(text: $text, prompt: "Search credentials")
        } else {
            content
        }
    }
}

/// Apple Wallet-style Pass Card with tinted institution header band, portrait support, and honest trust pill.
struct CredentialPassCard: View {
    let cred: StoredCredential

    var body: some View {
        let badge = CredentialDisplay.statusBadge(for: cred)
        let issuer = CredentialDisplay.formatIssuerLabel(cred.issuerDid, issuerName: cred.issuerName)
        let symbol = CredentialDisplay.symbolName(types: cred.type, title: cred.previewTitle)
        let accent = CredentialDisplay.passAccent(for: cred.previewTitle)
        let portrait = CredentialDisplay.extractPortraitImage(cred)

        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Image(systemName: symbol)
                    .font(.subheadline.bold())
                    .foregroundStyle(.white)
                Text(issuer)
                    .font(.caption.bold())
                    .foregroundStyle(.white)
                    .lineLimit(1)
                Spacer(minLength: 4)
                StatusPill(badge: badge, onDarkHeader: true)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(accent)

            HStack(spacing: 12) {
                if let portrait {
                    Image(uiImage: portrait)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 46, height: 58)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .accessibilityLabel("Cardholder portrait")
                }
                VStack(alignment: .leading, spacing: 3) {
                    Text(cred.previewTitle)
                        .font(.headline)
                        .lineLimit(2)
                    if let sub = cred.previewSubtitle {
                        Text(sub)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    Text("Added \(CredentialDisplay.formatRelativeTime(cred.receivedAt))")
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.bold())
                    .foregroundStyle(.tertiary)
            }
            .padding(14)
            .background(Color(uiColor: .secondarySystemBackground))
        }
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(cred.previewTitle), \(issuer), \(badge.label)")
        .accessibilityHint("Opens credential details")
    }
}

struct StatusPill: View {
    let badge: StatusBadge
    var onDarkHeader: Bool = false

    private var color: Color {
        switch badge.tone {
        case .success: return .green
        case .info: return .trustWeaveAccent
        case .warning: return .orange
        case .danger: return .red
        case .neutral: return .secondary
        }
    }

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: badge.symbolName)
                .font(.caption2)
            Text(badge.label)
                .font(.caption2.bold())
        }
        .foregroundStyle(color)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(onDarkHeader ? Color(uiColor: .systemBackground).opacity(0.92) : color.opacity(0.14))
        .clipShape(Capsule())
    }
}

struct CredentialDetailSheet: View {
    let cred: StoredCredential
    let onShare: () -> Void
    let onRequestDelete: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var showTechnicalDetails = false

    var body: some View {
        let badge = CredentialDisplay.statusBadge(for: cred)
        let issuer = CredentialDisplay.formatIssuerLabel(cred.issuerDid, issuerName: cred.issuerName)
        let claims = CredentialDisplay.extractClaimsForDisplay(cred)
        let symbol = CredentialDisplay.symbolName(types: cred.type, title: cred.previewTitle)
        let portrait = CredentialDisplay.extractPortraitImage(cred)

        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(spacing: 14) {
                        if let portrait {
                            Image(uiImage: portrait)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 64, height: 80)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .accessibilityLabel("Cardholder portrait")
                        } else {
                            Image(systemName: symbol)
                                .font(.title)
                                .foregroundStyle(Color.trustWeaveAccent)
                                .frame(width: 56, height: 56)
                                .background(Color.trustWeaveAccent.opacity(0.12))
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            Text(cred.previewTitle).font(.title3.bold())
                            if let sub = cred.previewSubtitle {
                                Text(sub).font(.subheadline).foregroundStyle(.secondary)
                            }
                            StatusPill(badge: badge)
                        }
                    }

                    Button {
                        onShare()
                    } label: {
                        Label("Share this credential", systemImage: "qrcode.viewfinder")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .buttonBorderShape(.capsule)

                    VStack(alignment: .leading, spacing: 4) {
                        Text("ISSUED BY").font(.caption2.bold()).foregroundStyle(.secondary)
                        Text(issuer).font(.body)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    if !claims.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("DETAILS").font(.caption2.bold()).foregroundStyle(.secondary)
                            ForEach(claims) { claim in
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack(spacing: 6) {
                                        Text(claim.label)
                                            .font(.caption.bold())
                                            .foregroundStyle(.secondary)
                                        if claim.shareable {
                                            Text("Selectable")
                                                .font(.caption2.bold())
                                                .foregroundStyle(Color.trustWeaveAccent)
                                                .padding(.horizontal, 6)
                                                .padding(.vertical, 1)
                                                .background(Color.trustWeaveAccent.opacity(0.12))
                                                .clipShape(Capsule())
                                        }
                                    }
                                    Text(claim.value)
                                        .font(.body)
                                        .textSelection(.enabled)
                                }
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(uiColor: .secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }

                    DisclosureGroup("Technical details", isExpanded: $showTechnicalDetails) {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Format: \(cred.format)")
                            Text("Issuer ID: \(cred.issuerDid)").textSelection(.enabled)
                            Text("Holder ID: \(cred.subjectDid)").textSelection(.enabled)
                        }
                        .font(.system(.caption2, design: .monospaced))
                        .foregroundStyle(.secondary)
                        .padding(.top, 6)
                    }
                    .padding(14)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    Button(role: .destructive) {
                        onRequestDelete()
                    } label: {
                        Label("Remove from wallet", systemImage: "trash")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                    }
                    .buttonStyle(.bordered)
                    .buttonBorderShape(.capsule)
                }
                .padding(16)
            }
            .navigationTitle("Credential")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
