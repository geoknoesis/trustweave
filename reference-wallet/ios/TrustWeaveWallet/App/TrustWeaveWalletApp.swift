// TrustWeaveWalletApp.swift
//
// Unified 3-tab Apple HIG wallet (Wallet / Scan / Activity) with:
//  - Shared `@StateObject` Wallet injected via `.environmentObject`
//  - Real `.onOpenURL` PayloadRouter handling (never drops incoming offer/verifier URLs)
//  - Adaptive WCAG-safe dark/light accent color
//  - App Switcher privacy shield + Face ID / Touch ID resume lock when enabled

import SwiftUI

enum WalletTab: Hashable {
    case wallet, scan, activity
}

enum WalletFlowSheet: Identifiable {
    case receive, present

    var id: Int { hashValue }
}

@main
struct TrustWeaveWalletApp: App {
    @StateObject private var wallet = Wallet()
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    @State private var selectedTab: WalletTab = .wallet
    @State private var activeSheet: WalletFlowSheet?
    @State private var showSettings = false
    @State private var bootstrapError: String?
    @State private var publicVerifyUrl: URL?
    @State private var isAppLocked = false
    @State private var wasBackgrounded = false

    var body: some Scene {
        WindowGroup {
            ZStack {
                if !wallet.preferences.onboardingComplete {
                    OnboardingView { biometricEnabled in
                        wallet.updatePreferences {
                            $0.onboardingComplete = true
                            $0.biometricLockEnabled = biometricEnabled
                        }
                        Haptics.success()
                    }
                    .environmentObject(wallet)
                } else {
                    TabView(selection: $selectedTab) {
                        NavigationStack {
                            HomeView(
                                selectedTab: $selectedTab,
                                activeSheet: $activeSheet,
                                showSettings: $showSettings
                            )
                        }
                        .tabItem { Label("Wallet", systemImage: "wallet.pass") }
                        .tag(WalletTab.wallet)

                        ScanView(showSettings: $showSettings, activeSheet: $activeSheet)
                            .tabItem { Label("Scan", systemImage: "qrcode.viewfinder") }
                            .tag(WalletTab.scan)

                        NavigationStack {
                            ActivityView(showSettings: $showSettings)
                        }
                        .tabItem { Label("Activity", systemImage: "clock") }
                        .tag(WalletTab.activity)
                    }
                    .environmentObject(wallet)
                    .sheet(item: $activeSheet) { sheet in
                        NavigationStack {
                            switch sheet {
                            case .receive:
                                ReceiveView(
                                    onDone: {
                                        activeSheet = nil
                                        selectedTab = .wallet
                                    },
                                    onShareCredential: { credId in
                                        wallet.preselectedCredentialId = credId
                                        activeSheet = .present
                                    }
                                )
                            case .present:
                                PresentView(
                                    onOpenScan: {
                                        activeSheet = nil
                                        selectedTab = .scan
                                    },
                                    onDone: {
                                        activeSheet = nil
                                        selectedTab = .wallet
                                    }
                                )
                            }
                        }
                        .environmentObject(wallet)
                    }
                    .sheet(isPresented: $showSettings) {
                        NavigationStack {
                            SettingsView(selectedTab: $selectedTab)
                        }
                        .environmentObject(wallet)
                    }
                }

                // App Switcher privacy shield + Face ID / Touch ID resume lock.
                if scenePhase != .active || isAppLocked {
                    Rectangle()
                        .fill(.ultraThinMaterial)
                        .ignoresSafeArea()
                        .overlay {
                            VStack(spacing: 14) {
                                Image(systemName: "lock.shield.fill")
                                    .font(.system(size: 46))
                                    .foregroundStyle(Color.trustWeaveAccent)
                                Text("TrustWeave")
                                    .font(.title3.bold())
                                if isAppLocked && scenePhase == .active {
                                    Button {
                                        unlockWithBiometrics()
                                    } label: {
                                        Label("Unlock with \(BiometricAuth.biometryLabel)", systemImage: "faceid")
                                            .font(.headline)
                                            .padding(.horizontal, 20)
                                            .padding(.vertical, 8)
                                    }
                                    .buttonStyle(.borderedProminent)
                                    .buttonBorderShape(.capsule)
                                }
                            }
                        }
                }
            }
            .tint(.trustWeaveAccent)
            .task {
                do {
                    try wallet.bootstrap()
                } catch {
                    bootstrapError = error.localizedDescription
                }
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .background {
                    wasBackgrounded = true
                    if wallet.preferences.onboardingComplete && wallet.preferences.biometricLockEnabled {
                        isAppLocked = true
                    }
                } else if newPhase == .active && wasBackgrounded {
                    wasBackgrounded = false
                    if isAppLocked { unlockWithBiometrics() }
                }
            }
            .onOpenURL { url in
                switch PayloadRouter.classify(url.absoluteString) {
                case .offer(let offer, _):
                    wallet.pendingOffer = offer
                    activeSheet = .receive
                case .presentation(let request, _):
                    wallet.pendingPresentation = request
                    activeSheet = .present
                case .publicVerify(let verifyUrl, _):
                    publicVerifyUrl = verifyUrl
                default:
                    selectedTab = .scan
                }
            }
            .alert(
                "Could not open wallet",
                isPresented: Binding(get: { bootstrapError != nil }, set: { if !$0 { bootstrapError = nil } }),
                actions: { Button("OK") { bootstrapError = nil } },
                message: { Text(bootstrapError ?? "") }
            )
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
    }

    private func unlockWithBiometrics() {
        Task {
            let ok = await BiometricAuth.confirm(reason: "Unlock your TrustWeave wallet")
            if ok { isAppLocked = false }
        }
    }
}
