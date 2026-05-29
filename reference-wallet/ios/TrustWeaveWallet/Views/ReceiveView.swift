// ReceiveView.swift — fetch a credential from the demo issuer.

import SwiftUI

struct ReceiveView: View {
    @StateObject private var wallet = Wallet()
    private let backend = DemoBackend()
    @State private var status: Status = .idle

    enum Status {
        case idle
        case requesting
        case success(format: String, title: String, disclosable: [String])
        case error(String)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                explainerPanel
                if let did = wallet.holderDid {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Subject DID (you)").font(.caption).foregroundStyle(.secondary)
                        Text(did).font(.system(.caption, design: .monospaced))
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .cornerRadius(8)

                    Button(action: receive) {
                        if case .requesting = status {
                            ProgressView().tint(.white)
                            Text("  Requesting…").padding(.leading, 4)
                        } else {
                            Text("Receive demo credential")
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled({ if case .requesting = status { true } else { false } }())
                }
                statusPanel
            }
            .padding(16)
        }
        .navigationTitle("Receive")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            do { try wallet.bootstrap() } catch { status = .error("\(error)") }
        }
    }

    private var explainerPanel: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Receive a credential").font(.headline)
            Text("The demo issuer signs a Bachelor of Science credential as an SD-JWT VC. At presentation time you choose which claims to reveal.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private var statusPanel: some View {
        switch status {
        case .success(let format, let title, let disclosable):
            VStack(alignment: .leading, spacing: 6) {
                Text("Received and stored").font(.headline).foregroundColor(.green)
                Text("\(title) (\(format))").font(.subheadline)
                if !disclosable.isEmpty {
                    Text("Selectively-disclosable:").font(.caption).foregroundStyle(.secondary)
                    Text(disclosable.joined(separator: ", ")).font(.system(.caption, design: .monospaced))
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.green.opacity(0.1))
            .cornerRadius(8)
        case .error(let message):
            VStack(alignment: .leading, spacing: 6) {
                Text("Failed").font(.headline).foregroundColor(.red)
                Text(message).font(.caption).foregroundStyle(.secondary)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.red.opacity(0.1))
            .cornerRadius(8)
        default:
            EmptyView()
        }
    }

    private func receive() {
        guard let did = wallet.holderDid else { return }
        status = .requesting
        Task {
            do {
                let offer = try await backend.receiveCredential(subjectDid: did)
                let stored = try wallet.receive(
                    credential: offer.credential,
                    format: offer.format,
                    selectivelyDisclosable: offer.selectivelyDisclosable ?? [],
                )
                status = .success(format: stored.format, title: stored.previewTitle, disclosable: stored.selectivelyDisclosable)
            } catch {
                status = .error("\(error)")
            }
        }
    }
}
