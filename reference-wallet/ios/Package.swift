// swift-tools-version: 5.9
// Package.swift — Swift Package manifest for running TrustWeaveWallet core unit tests via `swift test`.
// For building the full SwiftUI iOS app with Camera, CoreNFC, and Face ID, run `xcodegen generate` using `project.yml`.

import PackageDescription

let package = Package(
    name: "TrustWeaveWallet",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(name: "TrustWeaveWalletCore", targets: ["TrustWeaveWalletCore"]),
    ],
    targets: [
        .target(
            name: "TrustWeaveWalletCore",
            path: "TrustWeaveWallet/Lib",
            exclude: [
                "BiometricAuth.swift",
                "Haptics.swift",
                "NfcReader.swift",
                "QrCodeGenerator.swift",
            ]
        ),
        .testTarget(
            name: "TrustWeaveWalletTests",
            dependencies: ["TrustWeaveWalletCore"],
            path: "Tests/TrustWeaveWalletTests"
        ),
    ]
)
