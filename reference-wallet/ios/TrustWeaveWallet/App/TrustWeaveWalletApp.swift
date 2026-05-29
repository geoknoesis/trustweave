// TrustWeaveWalletApp.swift
//
// SwiftUI App entry. Mirrors android/.../ui/MainActivity.kt at a high level —
// a TopAppBar-equivalent at the top, three tabs (Wallet, Receive, Present) in
// a TabView.

import SwiftUI

@main
struct TrustWeaveWalletApp: App {
    var body: some Scene {
        WindowGroup {
            RootTabView()
        }
    }
}

struct RootTabView: View {
    var body: some View {
        TabView {
            NavigationStack { HomeView() }
                .tabItem { Label("Wallet", systemImage: "wallet.pass") }
            NavigationStack { ReceiveView() }
                .tabItem { Label("Receive", systemImage: "arrow.down.circle") }
            NavigationStack { PresentView() }
                .tabItem { Label("Present", systemImage: "arrow.up.circle") }
        }
        .tint(Color(red: 0.118, green: 0.227, blue: 0.541))   // matches TrustWeave primary
    }
}
