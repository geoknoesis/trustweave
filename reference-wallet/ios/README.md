# TrustWeave Reference Wallet — iOS (SwiftUI)

Native iOS 17+ SwiftUI wallet with feature parity to the [Android Compose wallet](../android/README.md)
and [Expo wallet](../expo/README.md), talking to the same in-repo demo backend and dynamic
OID4VCI / OID4VP endpoints.

## Current status & validation limits

The SwiftUI app, core libraries, `Info.plist`, NFC entitlements, XcodeGen project spec (`project.yml`),
SwiftPM manifest (`Package.swift`), and XCTest suite (`Tests/TrustWeaveWalletTests/WalletCoreTests.swift`)
are complete and statically audited for Swift 5.9 / iOS 17+ APIs.

**Important:** This codebase was authored in a Windows environment without macOS/Xcode access, so
Xcode compilation, SwiftPM `swift test`, iOS Simulator runs, and physical iPhone Face ID / CoreNFC
verification have **not yet been executed on macOS**. Run the Quickstart commands below on a Mac to
compile and run the XCTest suite.

## What's included

- **3-tab Apple HIG navigation** (`Wallet` / `Scan` / `Activity`) + modal Settings sheet
- **1-tap live QR scanner** (`AVFoundation`) with flashlight toggle, green decode flash, VoiceOver
  announcements, CoreNFC NDEF tag reading (`NfcReader.swift`), and manual link entry
- **Apple Wallet-style Pass Cards** with tinted institution headers, cardholder portrait rendering,
  proactive expiration banners, pull-to-refresh, and selectable claim values
- **Privacy-first selective disclosure** showing actual claim values, live shared/hidden count,
  Face ID / Touch ID (`LocalAuthentication`) confirmation, haptic feedback, and outcome verification sheet
- **Fail-closed issuer trust & verification** (`IssuerTrust.swift`, `CredentialVerification.swift`) —
  unknown issuers never auto-trust; trust persists only after explicit confirmation and cryptographic verification
- **Keychain custody transparency** (`WhenUnlockedThisDeviceOnly` seed storage in Keychain; explicitly
  discloses that Secure Enclave hardware isolation is not active for Ed25519)
- **Credential-only backup & restore** (`containsPrivateKeys: false`) + CoreImage Wallet ID QR (`CIQRCodeGenerator`)
- **Ready-to-generate Xcode project** (`project.yml` via XcodeGen) + **SwiftPM core test suite** (`Package.swift`)

## Quickstart (on macOS with Xcode 15.4+)

### Option A — Generate the Xcode project in 1 command (Recommended)

```bash
cd reference-wallet/ios
brew install xcodegen   # if not already installed
xcodegen generate
open TrustWeaveWallet.xcodeproj
```

Then select an iOS 17+ Simulator (or connected iPhone with your Development Team set under
**Signing & Capabilities**) and press **Run (`⌘R`)** or **Test (`⌘U`)**.

### Option B — Run the pure-Swift core unit tests via SwiftPM

```bash
cd reference-wallet/ios
swift test
```

### Connect to the demo backend

1. Start the demo backend on your Mac: `cd reference-wallet && npm install && npm run dev`
2. In iOS Simulator, `http://localhost:3000` works out of the box (`Info.plist` enables
   `NSAllowsLocalNetworking` for local development only — use HTTPS in production).
3. For a physical iPhone on LAN, configure `DemoBackend` base URL to your Mac's LAN IP (or HTTPS tunnel).

## File layout

```
ios/
├── project.yml                         XcodeGen specification (App + XCTest target, iOS 17.0+)
├── Package.swift                       SwiftPM manifest for `TrustWeaveWalletCore` + `swift test`
├── Tests/
│   └── TrustWeaveWalletTests/
│       └── WalletCoreTests.swift       XCTest suite (Crypto, SD-JWT, IssuerTrust, Verification, Router, Display)
└── TrustWeaveWallet/
    ├── Info.plist                      Camera, NFC, Face ID usage strings & OID4VC/VP URL schemes
    ├── TrustWeaveWallet.entitlements   CoreNFC NDEF reader entitlement
    ├── App/
    │   └── TrustWeaveWalletApp.swift   3-tab root, `.onOpenURL` routing, privacy curtain & resume lock
    ├── Views/
    │   ├── OnboardingView.swift        2-step first-run onboarding
    │   ├── HomeView.swift              Pass cards, expiry warnings, pull-to-refresh & detail sheet
    │   ├── ScanView.swift              1-tap live camera + CoreNFC + manual entry sheet
    │   ├── QrCameraView.swift          AVFoundation live QR viewfinder
    │   ├── ActivityView.swift          Private local timeline
    │   ├── ReceiveView.swift           Offer claiming & fail-closed UnrecognizedIssuerSheet
    │   ├── PresentView.swift           Selective disclosure, Face ID & verification outcome
    │   └── SettingsView.swift          Security, custody info, Trusted Issuers, Backup, QR & Reset
    └── Lib/
        ├── Wallet.swift                Shared @MainActor facade
        ├── HolderKey.swift             CryptoKit Ed25519 + Keychain (`WhenUnlockedThisDeviceOnly`)
        ├── Storage.swift               Keychain credentials + UserDefaults metadata/activity/prefs
        ├── Crypto.swift                Base58btc, base64url, did:key, SHA-256 & JWS sign/verify
        ├── SdJwt.swift                 SD-JWT VC decode & selective disclosure presentation
        ├── IssuerTrust.swift           Fail-closed issuer trust policy & DID fingerprinting
        ├── CredentialVerification.swift Signature, holder-binding, expiry & disclosure checks
        ├── CredentialDisplay.swift     Pass accents, portraits, relative times & honest badges
        ├── PayloadRouter.swift         Unified QR / CoreNFC / `.onOpenURL` router
        ├── DemoBackend.swift           Dynamic OID4VCI / OID4VP & demo backend HTTP client
        ├── BiometricAuth.swift         LocalAuthentication Face ID / Touch ID helper
        ├── Haptics.swift               Native UIKit haptic feedback
        ├── NfcReader.swift             CoreNFC NDEF reader session
        └── QrCodeGenerator.swift       CoreImage QR bitmap generator
```

## License

See the repository [LICENSE](../../LICENSE) and [commercial license terms](../../LICENSE-COMMERCIAL.md).
