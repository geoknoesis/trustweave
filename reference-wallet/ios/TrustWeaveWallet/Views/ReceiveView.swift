// ReceiveView.swift — fail-closed credential receive flow supporting QR / CoreNFC / deep-link offers and haptics.

import SwiftUI

struct ReceiveView: View {
    @EnvironmentObject private var wallet: Wallet
    let onDone: () -> Void
    let onShareCredential: (String) -> Void

    private let backend = DemoBackend()
    @State private var status: Status = .idle

    enum Status {
        case idle
        case requesting
        case confirmIssuer(issuerDid: String, title: String?, offer: CredentialOffer, backendIssuers: Set<String>)
        case success(StoredCredential)
        case error(String)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if case .success(let cred) = status {
                    successCard(cred)
                } else {
                    Text("Verify and save a signed credential privately on this iPhone.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    VStack(spacing: 14) {
                        Image(systemName: "plus.rectangle.on.rectangle")
                            .font(.system(size: 36))
                            .foregroundStyle(Color.trustWeaveAccent)
                            .padding(18)
                            .background(Color.trustWeaveAccent.opacity(0.12))
                            .clipShape(Circle())

                        Text("Receive from issuer")
                            .font(.headline)

                        Text("Your wallet checks the issuer signature before saving anything. When sharing later, you decide which details to reveal.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)

                        Button {
                            receive(incoming: nil)
                        } label: {
                            if case .requesting = status {
                                HStack(spacing: 8) {
                                    ProgressView().tint(.white)
                                    Text("Verifying credential…")
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                            } else {
                                Text("Receive sample credential")
                                    .font(.headline)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 6)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .buttonBorderShape(.capsule)
                        .disabled(wallet.holderDid == nil || isRequesting)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(22)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                    if case .error(let message) = status {
                        VStack(alignment: .leading, spacing: 6) {
                            Label("Could not add credential", systemImage: "exclamationmark.circle.fill")
                                .font(.headline)
                                .foregroundStyle(.red)
                            Text(message)
                                .font(.subheadline)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.red.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                }
            }
            .padding(16)
        }
        .navigationTitle("Add credential")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Close") { onDone() }
            }
        }
        .task {
            if let pending = wallet.pendingOffer {
                wallet.pendingOffer = nil
                receive(incoming: pending)
            }
        }
        .sheet(
            isPresented: Binding(
                get: {
                    if case .confirmIssuer = status { return true }
                    return false
                },
                set: { if !$0, case .confirmIssuer = status { status = .idle } }
            )
        ) {
            if case .confirmIssuer(let issuerDid, let title, let offer, let backendIssuers) = status {
                UnrecognizedIssuerSheet(
                    issuerDid: issuerDid,
                    credentialTitle: title,
                    onCancel: { status = .idle },
                    onTrustAndAdd: {
                        do {
                            let stored = try wallet.receive(
                                credential: offer.credential,
                                format: offer.format,
                                selectivelyDisclosable: offer.selectivelyDisclosable ?? [],
                                backendIssuers: backendIssuers,
                                confirmedIssuer: issuerDid
                            )
                            Haptics.success()
                            status = .success(stored)
                        } catch {
                            Haptics.error()
                            status = .error(error.localizedDescription)
                        }
                    }
                )
            }
        }
    }

    private var isRequesting: Bool {
        if case .requesting = status { return true }
        return false
    }

    private func successCard(_ cred: StoredCredential) -> some View {
        VStack(spacing: 14) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 52))
                .foregroundStyle(.green)
            Text("Credential added")
                .font(.title2.bold())
            Text(cred.previewTitle)
                .font(.headline)
            if let sub = cred.previewSubtitle {
                Text(sub)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            Button {
                status = .idle
                onDone()
            } label: {
                Text("View in wallet")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)

            Button {
                let id = cred.id
                status = .idle
                onShareCredential(id)
            } label: {
                Text("Share now")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.bordered)
            .buttonBorderShape(.capsule)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    private func receive(incoming: IncomingOffer?) {
        guard let did = wallet.holderDid else { return }
        status = .requesting
        Task {
            do {
                let offer: CredentialOffer
                if let incoming {
                    offer = try await backend.claimIncomingOffer(incoming, subjectDid: did)
                } else {
                    offer = try await backend.receiveCredential(subjectDid: did)
                }
                let backendIssuers = await backend.backendIssuers()
                do {
                    let stored = try wallet.receive(
                        credential: offer.credential,
                        format: offer.format,
                        selectivelyDisclosable: offer.selectivelyDisclosable ?? [],
                        backendIssuers: backendIssuers,
                        confirmedIssuer: nil
                    )
                    Haptics.success()
                    status = .success(stored)
                } catch let untrusted as UntrustedIssuerError {
                    Haptics.warning()
                    status = .confirmIssuer(
                        issuerDid: untrusted.issuerDid,
                        title: untrusted.credentialTitle,
                        offer: offer,
                        backendIssuers: backendIssuers
                    )
                }
            } catch {
                Haptics.error()
                status = .error(error.localizedDescription)
            }
        }
    }
}

/// Flow 4: Human-Readable Fail-Closed Issuer Trust Confirmation Sheet for iOS.
struct UnrecognizedIssuerSheet: View {
    let issuerDid: String
    let credentialTitle: String?
    let onCancel: () -> Void
    let onTrustAndAdd: () -> Void

    @State private var showFullDid = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "exclamationmark.shield.fill")
                            .font(.title2)
                            .foregroundStyle(.orange)
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Unrecognized issuer")
                                .font(.headline)
                                .foregroundStyle(.orange)
                            Text("This wallet does not automatically recognize the organization that signed this credential.")
                                .font(.subheadline)
                        }
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.orange.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    VStack(alignment: .leading, spacing: 12) {
                        if let title = credentialTitle {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("CREDENTIAL TYPE (UNVERIFIED)")
                                    .font(.caption2.bold())
                                    .foregroundStyle(.secondary)
                                Text(title).font(.headline)
                            }
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text("ISSUER KEY FINGERPRINT")
                                .font(.caption2.bold())
                                .foregroundStyle(.secondary)
                            Text(IssuerTrust.shortDidFingerprint(issuerDid))
                                .font(.system(.title3, design: .monospaced).bold())
                        }

                        DisclosureGroup("Inspect full cryptographic ID (DID)", isExpanded: $showFullDid) {
                            Text(issuerDid)
                                .font(.system(.caption2, design: .monospaced))
                                .foregroundStyle(.secondary)
                                .textSelection(.enabled)
                                .padding(.top, 4)
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    Text("Only continue if you are directly expecting this credential from this organization. You can remove trusted issuers anytime in Settings.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(16)
            }
            .navigationTitle("Trust this issuer?")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) {
                HStack(spacing: 12) {
                    Button("Cancel", role: .cancel, action: onCancel)
                        .buttonStyle(.bordered)
                        .buttonBorderShape(.capsule)
                        .frame(maxWidth: .infinity)
                    Button("Trust issuer & add", action: onTrustAndAdd)
                        .buttonStyle(.borderedProminent)
                        .buttonBorderShape(.capsule)
                        .frame(maxWidth: .infinity)
                }
                .padding(16)
                .background(.bar)
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}
