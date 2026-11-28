// PresentView.swift — privacy-first selective disclosure with real claim values, Face ID, haptics, and dynamic verifier targets.

import SwiftUI

struct PresentView: View {
    @EnvironmentObject private var wallet: Wallet
    let onOpenScan: () -> Void
    let onDone: () -> Void

    private let backend = DemoBackend()
    @State private var activeTarget: PresentationRequestPayload?
    @State private var selectedId: String?
    @State private var disclose: [String: Bool] = [:]
    @State private var status: Status = .idle
    @State private var showTechChecks = false

    enum Status {
        case idle
        case fetchingRequest
        case buildingVp
        case verifying
        case done(response: VerificationResponse, title: String, verifierName: String, shared: Int, total: Int)
        case error(String)
    }

    private var selectedCred: StoredCredential? {
        wallet.credentials.first(where: { $0.id == selectedId })
    }

    private var isSelectedExpired: Bool {
        guard let cred = selectedCred else { return false }
        return CredentialDisplay.statusBadge(for: cred).label == "Expired"
    }

    private var shareableDetails: [ShareableClaimDetail] {
        guard let cred = selectedCred, cred.format == "vc+sd-jwt" else { return [] }
        return CredentialDisplay.extractShareableClaimDetails(cred)
    }

    private var sharedCount: Int {
        shareableDetails.filter { disclose[$0.name] == true }.count
    }

    private var shareButtonTitle: String {
        let total = shareableDetails.count
        let count = sharedCount
        let unit = count == 1 ? "detail" : "details"
        if wallet.preferences.biometricLockEnabled {
            if total > 0 {
                return count > 0 ? "Confirm & share (\(count) \(unit))" : "Confirm & share (proof only)"
            }
            return "Confirm & share"
        }
        if total > 0 {
            return count > 0 ? "Share (\(count) \(unit))" : "Share proof only"
        }
        return "Share with requester"
    }

    private var requesterHost: String? {
        guard let urlStr = activeTarget?.verifierUrl else { return nil }
        return URL(string: urlStr)?.host ?? urlStr
    }

    private var isInProgress: Bool {
        switch status {
        case .fetchingRequest, .buildingVp, .verifying: return true
        default: return false
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if case .done(let response, let title, let verifierName, let shared, let total) = status {
                    outcomeView(response: response, title: title, verifierName: verifierName, shared: shared, total: total)
                } else if wallet.credentials.isEmpty {
                    emptyState
                } else {
                    if let host = requesterHost {
                        HStack(spacing: 12) {
                            Image(systemName: "building.2.fill")
                                .font(.title3)
                                .foregroundStyle(Color.trustWeaveAccent)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("REQUESTED BY").font(.caption2.bold()).foregroundStyle(Color.trustWeaveAccent)
                                Text(host).font(.headline)
                            }
                            Spacer()
                        }
                        .padding(14)
                        .background(Color.trustWeaveAccent.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    } else {
                        Text("Choose a credential and decide which personal details to reveal.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    chooseCard
                    if !shareableDetails.isEmpty {
                        discloseCard
                    }
                    if isSelectedExpired {
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(.red)
                                .font(.title3)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("This credential has expired")
                                    .font(.subheadline.bold())
                                    .foregroundStyle(.red)
                                Text("The requester may reject it. You can still try sharing or request a renewal from the issuer.")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(14)
                        .background(Color.red.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .accessibilityElement(children: .combine)
                    }
                    if case .error(let msg) = status {
                        errorCard(msg)
                    }
                    actionRow
                }
            }
            .padding(16)
        }
        .navigationTitle("Review & share")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button("Close") { onDone() }
            }
        }
        .onAppear {
            if let pending = wallet.pendingPresentation {
                activeTarget = pending
                wallet.pendingPresentation = nil
            }
            syncSelection()
        }
        .onChange(of: wallet.preselectedCredentialId) { _, _ in syncSelection() }
        .onChange(of: selectedId) { _, _ in resetDisclosuresPrivacyFirst() }
    }

    private func syncSelection() {
        if let preferred = wallet.preselectedCredentialId,
           wallet.credentials.contains(where: { $0.id == preferred }) {
            selectedId = preferred
            wallet.preselectedCredentialId = nil
        } else if selectedId == nil || !wallet.credentials.contains(where: { $0.id == selectedId }) {
            selectedId = wallet.credentials.first?.id
        }
        resetDisclosuresPrivacyFirst()
    }

    /// Privacy-first default: start all optional claims OFF so users consciously choose what to share.
    private func resetDisclosuresPrivacyFirst() {
        disclose.removeAll()
        for name in selectedCred?.selectivelyDisclosable ?? [] {
            disclose[name] = false
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
            Text("Nothing to share yet").font(.headline)
            Text("Add a credential to your wallet first, then scan a requester's QR code or tap NFC.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button {
                onOpenScan()
            } label: {
                Text("Scan QR or tap NFC")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.capsule)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var chooseCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("CREDENTIAL TO SHARE")
                .font(.caption2.bold())
                .foregroundStyle(.secondary)

            ForEach(wallet.credentials) { c in
                Button {
                    Haptics.selection()
                    selectedId = c.id
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: selectedId == c.id ? "checkmark.circle.fill" : "circle")
                            .font(.title3)
                            .foregroundStyle(selectedId == c.id ? Color.trustWeaveAccent : .secondary)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(c.previewTitle).font(.headline)
                            if let sub = c.previewSubtitle {
                                Text(sub).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                    }
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var discloseCard: some View {
        let total = shareableDetails.count
        let hidden = total - sharedCount

        return VStack(alignment: .leading, spacing: 12) {
            Text("CHOOSE WHAT TO SHARE")
                .font(.caption2.bold())
                .foregroundStyle(.secondary)
            Text("Everything switched off stays completely hidden from the requester.")
                .font(.caption)
                .foregroundStyle(.secondary)

            HStack {
                Label("Sharing \(sharedCount) of \(total) · \(hidden) hidden", systemImage: "eye.slash")
                    .font(.caption.bold())
                    .foregroundStyle(Color.trustWeaveAccent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.trustWeaveAccent.opacity(0.12))
                    .clipShape(Capsule())
                Spacer()
                Button("Minimal") {
                    Haptics.selection()
                    for item in shareableDetails { disclose[item.name] = false }
                }
                .font(.caption.bold())
                Button("All") {
                    Haptics.selection()
                    for item in shareableDetails { disclose[item.name] = true }
                }
                .font(.caption.bold())
            }

            ForEach(shareableDetails) { item in
                let isOn = disclose[item.name] ?? false
                Toggle(isOn: bindingFor(item.name)) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.label).font(.subheadline.bold())
                        Text(item.value ?? "Included in credential")
                            .font(.subheadline)
                            .foregroundStyle(isOn ? .primary : .secondary)
                        Text(isOn ? "Will be shared" : "Stays hidden")
                            .font(.caption2.bold())
                            .foregroundStyle(isOn ? .green : .secondary)
                    }
                }
                .tint(.green)
            }
        }
        .padding(16)
        .background(Color(uiColor: .secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var actionRow: some View {
        Button(action: initiateShare) {
            Group {
                switch status {
                case .fetchingRequest:
                    HStack(spacing: 8) { ProgressView().tint(.white); Text("Reading request…") }
                case .buildingVp:
                    HStack(spacing: 8) { ProgressView().tint(.white); Text("Preparing proof…") }
                case .verifying:
                    HStack(spacing: 8) { ProgressView().tint(.white); Text("Sharing…") }
                default:
                    Label(
                        shareButtonTitle,
                        systemImage: wallet.preferences.biometricLockEnabled ? "faceid" : "paperplane.fill"
                    )
                    .font(.headline)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
        }
        .buttonStyle(.borderedProminent)
        .buttonBorderShape(.capsule)
        .disabled(selectedId == nil || isInProgress)
    }

    private func outcomeView(
        response: VerificationResponse,
        title: String,
        verifierName: String,
        shared: Int,
        total: Int
    ) -> some View {
        VStack(spacing: 16) {
            VStack(spacing: 12) {
                Image(systemName: response.valid ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.system(size: 56))
                    .foregroundStyle(response.valid ? .green : .red)
                Text(response.valid ? "Shared & verified" : "Verification failed")
                    .font(.title2.bold())
                Text(
                    response.valid
                        ? "\(title) was verified by \(verifierName)."
                        : "\(verifierName) could not verify this presentation."
                )
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

                if total > 0 {
                    Label("Shared \(shared) of \(total) details · \(total - shared) hidden", systemImage: "eye.slash")
                        .font(.caption.bold())
                        .foregroundStyle(Color.trustWeaveAccent)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.trustWeaveAccent.opacity(0.12))
                        .clipShape(Capsule())
                }

                Button {
                    status = .idle
                    activeTarget = nil
                    showTechChecks = false
                    onDone()
                } label: {
                    Text("Done")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.capsule)
                .padding(.top, 6)
            }
            .frame(maxWidth: .infinity)
            .padding(24)
            .background(Color(uiColor: .secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 18))

            DisclosureGroup("Technical verification checks (\(response.checks.count))", isExpanded: $showTechChecks) {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(response.checks.enumerated()), id: \.offset) { _, c in
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: c.passed ? "checkmark.circle.fill" : "xmark.circle.fill")
                                .foregroundStyle(c.passed ? .green : .red)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(c.step).font(.caption)
                                if let detail = c.detail {
                                    Text(detail)
                                        .font(.system(.caption2, design: .monospaced))
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .padding(8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background((c.passed ? Color.green : Color.red).opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }
                .padding(.top, 8)
            }
            .padding(14)
            .background(Color(uiColor: .secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 14))
        }
    }

    private func errorCard(_ msg: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Label("Sharing failed", systemImage: "exclamationmark.circle.fill")
                .font(.headline)
                .foregroundStyle(.red)
            Text(msg).font(.subheadline)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.red.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func bindingFor(_ name: String) -> Binding<Bool> {
        Binding(get: { disclose[name] ?? false }, set: { disclose[name] = $0 })
    }

    private func initiateShare() {
        guard let id = selectedId, let cred = selectedCred else { return }
        let total = shareableDetails.count
        let count = sharedCount
        let target = activeTarget
        Task {
            if wallet.preferences.biometricLockEnabled {
                let reason = total > 0
                    ? "Confirm sharing \(cred.previewTitle) (\(count) of \(total) details)"
                    : "Confirm sharing \(cred.previewTitle)"
                let approved = await BiometricAuth.confirm(reason: reason)
                guard approved else { return }
            }
            do {
                status = .fetchingRequest
                async let reqTask = backend.fetchPresentationRequest(target: target)
                async let backendIssuersTask = backend.backendIssuers()
                let req = try await reqTask
                let backendIssuers = await backendIssuersTask

                status = .buildingVp
                let discloseSet = Set(disclose.filter { $0.value }.map { $0.key })
                let vp = try wallet.createPresentation(
                    credentialId: id,
                    audience: req.audience,
                    nonce: req.nonce,
                    disclose: discloseSet,
                    backendIssuers: backendIssuers
                )
                status = .verifying
                let res = try await backend.verify(
                    presentation: vp,
                    format: cred.format,
                    expectedNonce: req.nonce,
                    target: target
                )
                let verifierLabel = requesterHost ?? (req.verifier.hasPrefix("did:") ? "Requesting verifier" : req.verifier)
                wallet.recordActivity(
                    kind: "shared",
                    title: cred.previewTitle,
                    counterparty: verifierLabel,
                    detailsShared: total > 0 ? discloseSet.count : nil,
                    totalDetails: total > 0 ? total : nil,
                    success: res.valid
                )
                if res.valid { Haptics.success() } else { Haptics.error() }
                status = .done(
                    response: res,
                    title: cred.previewTitle,
                    verifierName: verifierLabel,
                    shared: discloseSet.count,
                    total: total
                )
            } catch {
                Haptics.error()
                status = .error(error.localizedDescription)
            }
        }
    }
}
