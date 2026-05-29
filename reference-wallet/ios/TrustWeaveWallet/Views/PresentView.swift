// PresentView.swift — choose credential + disclosures, present to demo verifier.

import SwiftUI

struct PresentView: View {
    @StateObject private var wallet = Wallet()
    private let backend = DemoBackend()
    @State private var selectedId: String?
    @State private var disclose: [String: Bool] = [:]
    @State private var status: Status = .idle
    @State private var response: VerificationResponse?

    enum Status: Equatable {
        case idle, fetchingRequest, buildingVp, verifying, done, error(String)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if wallet.credentials.isEmpty {
                    emptyState
                } else {
                    chooseCard
                    if let selected = selectedCred, selected.format == "vc+sd-jwt" {
                        discloseCard(selected: selected)
                    }
                    actionRow
                    if let r = response { verificationResult(r) }
                    if case .error(let msg) = status {
                        errorCard(msg)
                    }
                }
            }
            .padding(16)
        }
        .navigationTitle("Present")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            do {
                try wallet.bootstrap()
                if selectedId == nil, let first = wallet.credentials.first { selectedId = first.id }
                resetDisclosures()
            } catch {
                status = .error("\(error)")
            }
        }
        .onChange(of: selectedId) { _ in resetDisclosures() }
    }

    private var selectedCred: StoredCredential? {
        wallet.credentials.first(where: { $0.id == selectedId })
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Image(systemName: "tray").font(.largeTitle).foregroundStyle(.secondary)
            Text("No credentials to present.").foregroundStyle(.secondary)
            NavigationLink("Receive one first", destination: ReceiveView())
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity)
        .padding(32)
    }

    private var chooseCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Present a credential").font(.headline)
            Text("The wallet builds a Verifiable Presentation containing the selected credential, signs it with your holder DID, and sends it to the demo verifier.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            ForEach(wallet.credentials) { c in
                HStack {
                    Image(systemName: selectedId == c.id ? "largecircle.fill.circle" : "circle")
                        .foregroundColor(.accentColor)
                    VStack(alignment: .leading) {
                        HStack {
                            Text(c.previewTitle).bold()
                            Text("(\(c.format))").font(.caption2).foregroundStyle(.tertiary)
                        }
                        if let sub = c.previewSubtitle {
                            Text(sub).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                }
                .contentShape(Rectangle())
                .onTapGesture { selectedId = c.id }
            }
        }
        .padding(12)
        .background(Color(uiColor: .secondarySystemBackground))
        .cornerRadius(8)
    }

    private func discloseCard(selected: StoredCredential) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Disclose to verifier").font(.headline)
            Text("Tick the claims you're willing to reveal. Unticked claims stay hashed inside the credential — the verifier sees only that the issuer signed something, not what.")
                .font(.caption)
                .foregroundStyle(.secondary)
            ForEach(selected.selectivelyDisclosable, id: \.self) { name in
                Toggle(isOn: bindingFor(name)) {
                    Text(name).font(.system(.subheadline, design: .monospaced))
                }
            }
        }
        .padding(12)
        .background(Color(uiColor: .secondarySystemBackground))
        .cornerRadius(8)
    }

    private var actionRow: some View {
        Button(action: present) {
            switch status {
            case .fetchingRequest: HStack { ProgressView(); Text("Fetching verifier request…").padding(.leading, 4) }
            case .buildingVp: HStack { ProgressView(); Text("Building presentation…").padding(.leading, 4) }
            case .verifying: HStack { ProgressView(); Text("Verifying…").padding(.leading, 4) }
            default: Text("Present to demo verifier")
            }
        }
        .buttonStyle(.borderedProminent)
        .disabled(selectedId == nil || isInProgress)
    }

    private var isInProgress: Bool {
        switch status {
        case .fetchingRequest, .buildingVp, .verifying: return true
        default: return false
        }
    }

    private func verificationResult(_ r: VerificationResponse) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Verification result").font(.headline)
            HStack {
                Image(systemName: r.valid ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .foregroundColor(r.valid ? .green : .red)
                Text(r.valid ? "Presentation verified." : "Verification failed.")
                    .bold()
                    .foregroundColor(r.valid ? .green : .red)
            }
            .padding(8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background((r.valid ? Color.green : Color.red).opacity(0.1))
            .cornerRadius(6)

            Text("Checklist").font(.subheadline).bold()
            ForEach(Array(r.checks.enumerated()), id: \.offset) { _, c in
                HStack(alignment: .top, spacing: 6) {
                    Text(c.passed ? "✓" : "✗").bold().foregroundColor(c.passed ? .green : .red)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(c.step).font(.subheadline)
                        if let detail = c.detail {
                            Text(detail).font(.system(.caption, design: .monospaced)).foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(6)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background((c.passed ? Color.green : Color.red).opacity(0.05))
                .cornerRadius(4)
            }

            if let creds = r.credentials, !creds.isEmpty {
                Text("What the verifier saw").font(.subheadline).bold()
                ForEach(Array(creds.enumerated()), id: \.offset) { _, cred in
                    VStack(alignment: .leading, spacing: 4) {
                        Text("type: \(cred.type.joined(separator: ", "))")
                            .font(.system(.caption, design: .monospaced))
                            .foregroundColor(.accentColor)
                        ForEach(cred.disclosedClaims.sorted(by: { $0.key < $1.key }), id: \.key) { k, v in
                            Text("\(k): \(v.prettyString)").font(.system(.caption, design: .monospaced))
                        }
                        if let withheld = cred.withheldClaimNames, !withheld.isEmpty {
                            Divider().padding(.vertical, 2)
                            Text("withheld:").font(.system(.caption2, design: .monospaced)).foregroundStyle(.secondary)
                            ForEach(withheld, id: \.self) { wn in
                                Text("  \(wn)").font(.system(.caption2, design: .monospaced)).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .cornerRadius(6)
                }
            }
        }
    }

    private func errorCard(_ msg: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Failed").bold().foregroundColor(.red)
            Text(msg).font(.caption)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.red.opacity(0.1))
        .cornerRadius(8)
    }

    private func bindingFor(_ name: String) -> Binding<Bool> {
        Binding(get: { disclose[name] ?? false }, set: { disclose[name] = $0 })
    }

    private func resetDisclosures() {
        disclose.removeAll()
        for name in selectedCred?.selectivelyDisclosable ?? [] { disclose[name] = true }
    }

    private func present() {
        guard let id = selectedId, let cred = selectedCred else { return }
        response = nil
        Task {
            do {
                status = .fetchingRequest
                let req = try await backend.fetchPresentationRequest()
                status = .buildingVp
                let discloseSet = Set(disclose.filter { $0.value }.map { $0.key })
                let vp = try wallet.createPresentation(credentialId: id, audience: req.audience, nonce: req.nonce, disclose: discloseSet)
                status = .verifying
                let res = try await backend.verify(presentation: vp, format: cred.format, expectedNonce: req.nonce)
                response = res
                status = .done
            } catch {
                status = .error("\(error)")
            }
        }
    }
}
