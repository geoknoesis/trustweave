// ScanView.swift — 1-tap live AVFoundation QR camera + CoreNFC tap + manual entry sheet.

import AVFoundation
import SwiftUI
import UIKit

struct ScanView: View {
    @EnvironmentObject private var wallet: Wallet
    @Environment(\.openURL) private var openURL
    @Binding var showSettings: Bool
    @Binding var activeSheet: WalletFlowSheet?

    @State private var cameraAuthorized = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
    @State private var torchEnabled = false
    @State private var decodeSuccess = false
    @State private var manualSheetOpen = false
    @State private var manualText = ""
    @State private var notice: String?
    @State private var publicVerifyUrl: URL?
    @State private var isReadingNfc = false

    private let nfcReader = NfcReader()

    var body: some View {
        ZStack {
            Color(red: 0.043, green: 0.067, blue: 0.125).ignoresSafeArea()

            VStack(spacing: 12) {
                HStack {
                    Text("Scan or tap")
                        .font(.title2.bold())
                        .foregroundStyle(.white)
                    Spacer()
                    if cameraAuthorized {
                        Button {
                            Haptics.selection()
                            torchEnabled.toggle()
                        } label: {
                            Image(systemName: torchEnabled ? "flashlight.on.fill" : "flashlight.off.fill")
                                .font(.title3)
                                .foregroundStyle(torchEnabled ? .yellow : .white)
                                .frame(width: 44, height: 44)
                        }
                        .accessibilityLabel(torchEnabled ? "Turn off flashlight" : "Turn on flashlight")
                    }
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                            .font(.title3)
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Settings and security")
                }
                .padding(.horizontal, 16)

                ZStack {
                    if cameraAuthorized {
                        QrCameraView(
                            torchEnabled: torchEnabled,
                            isActive: !manualSheetOpen && activeSheet == nil
                        ) { decoded in
                            handlePayload(decoded, flashCamera: true)
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 22))
                        .overlay {
                            VStack {
                                Spacer()
                                RoundedRectangle(cornerRadius: 18)
                                    .stroke(decodeSuccess ? Color.green : Color.white.opacity(0.9), lineWidth: decodeSuccess ? 4 : 3)
                                    .frame(width: 230, height: 230)
                                    .animation(.easeOut(duration: 0.15), value: decodeSuccess)
                                Text(decodeSuccess ? "Code recognized!" : "Point at an issuer offer or verification QR")
                                    .font(.caption.bold())
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(decodeSuccess ? Color.green.opacity(0.9) : Color.black.opacity(0.65))
                                    .clipShape(Capsule())
                                    .padding(.top, 18)
                                Spacer()
                            }
                            .allowsHitTesting(false)
                        }
                    } else {
                        VStack(spacing: 14) {
                            Image(systemName: "camera.viewfinder")
                                .font(.system(size: 44))
                                .foregroundStyle(Color.trustWeaveAccent)
                            Text("Allow camera to scan")
                                .font(.headline)
                            Text("Point your camera at any issuer offer or verification QR code — the wallet routes it automatically.")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.center)
                            Button {
                                AVCaptureDevice.requestAccess(for: .video) { granted in
                                    DispatchQueue.main.async { cameraAuthorized = granted }
                                }
                            } label: {
                                Text("Allow camera")
                                    .font(.headline)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 6)
                            }
                            .buttonStyle(.borderedProminent)
                            .buttonBorderShape(.capsule)
                        }
                        .padding(24)
                        .background(Color(uiColor: .secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 18))
                        .padding(16)
                    }
                }
                .padding(.horizontal, 12)

                VStack(spacing: 10) {
                    if let notice {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.circle.fill").foregroundStyle(.red)
                            Text(notice).font(.caption).foregroundStyle(.white)
                            Spacer()
                        }
                        .padding(12)
                        .background(Color.red.opacity(0.25))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    if NfcReader.isSupported {
                        Button {
                            startNfcRead()
                        } label: {
                            HStack(spacing: 8) {
                                if isReadingNfc { ProgressView().tint(.white) }
                                Label("Tap an NFC reader", systemImage: "wave.3.right")
                                    .font(.headline)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                        }
                        .buttonStyle(.borderedProminent)
                        .buttonBorderShape(.capsule)
                        .disabled(isReadingNfc)
                    }

                    HStack(spacing: 10) {
                        Button {
                            notice = nil
                            manualSheetOpen = true
                        } label: {
                            Label("Enter link", systemImage: "keyboard")
                                .font(.subheadline.bold())
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.bordered)
                        .tint(.white)
                        .buttonBorderShape(.capsule)

                        Button("Demo issuer") {
                            activeSheet = .receive
                        }
                        .font(.subheadline.bold())
                        .foregroundStyle(Color(red: 0.576, green: 0.773, blue: 0.992))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
            }
        }
        .preferredColorScheme(.dark)
        .sheet(isPresented: $manualSheetOpen) {
            NavigationStack {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Paste a credential offer link, OpenID4VCI URI, or verification request.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    TextEditor(text: $manualText)
                        .font(.system(.caption, design: .monospaced))
                        .frame(minHeight: 110)
                        .padding(8)
                        .background(Color(uiColor: .secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    Button {
                        if handlePayload(manualText) {
                            manualText = ""
                            manualSheetOpen = false
                        }
                    } label: {
                        Text("Continue")
                            .font(.headline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .buttonBorderShape(.capsule)
                    .disabled(manualText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    Spacer()
                }
                .padding(16)
                .navigationTitle("Enter link or code")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Close") { manualSheetOpen = false }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .alert(
            "Web verification link",
            isPresented: Binding(get: { publicVerifyUrl != nil }, set: { if !$0 { publicVerifyUrl = nil } })
        ) {
            Button("Open in Safari") {
                if let url = publicVerifyUrl { openURL(url) }
                publicVerifyUrl = nil
            }
            Button("Cancel", role: .cancel) { publicVerifyUrl = nil }
        } message: {
            Text("This link lets someone view verification details in a web browser. Open it now?")
        }
    }

    @discardableResult
    private func handlePayload(_ raw: String, flashCamera: Bool = false) -> Bool {
        switch PayloadRouter.classify(raw) {
        case .offer(let offer, _):
            Haptics.success()
            notice = nil
            UIAccessibility.post(notification: .announcement, argument: "Credential offer detected")
            if flashCamera {
                decodeSuccess = true
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 200_000_000)
                    decodeSuccess = false
                    wallet.pendingOffer = offer
                    activeSheet = .receive
                }
            } else {
                wallet.pendingOffer = offer
                activeSheet = .receive
            }
            return true
        case .presentation(let request, _):
            Haptics.success()
            notice = nil
            UIAccessibility.post(notification: .announcement, argument: "Verification request detected")
            if flashCamera {
                decodeSuccess = true
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 200_000_000)
                    decodeSuccess = false
                    wallet.pendingPresentation = request
                    activeSheet = .present
                }
            } else {
                wallet.pendingPresentation = request
                activeSheet = .present
            }
            return true
        case .publicVerify(let url, _):
            Haptics.selection()
            publicVerifyUrl = url
            return true
        case .holderDid:
            Haptics.warning()
            let msg = "This is a Wallet ID QR. Scan a credential offer or a requester's verification QR instead."
            notice = msg
            UIAccessibility.post(notification: .announcement, argument: msg)
            return false
        case .unknown(let reason, _):
            Haptics.warning()
            notice = reason
            UIAccessibility.post(notification: .announcement, argument: reason)
            return false
        }
    }

    private func startNfcRead() {
        isReadingNfc = true
        notice = nil
        Task {
            do {
                let raw = try await nfcReader.readTag()
                handlePayload(raw)
            } catch {
                let msg = error.localizedDescription
                if !msg.localizedCaseInsensitiveContains("cancel") {
                    Haptics.warning()
                    notice = msg
                }
            }
            isReadingNfc = false
        }
    }
}
