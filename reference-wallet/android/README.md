# TrustWeave Reference Wallet — Android

Android reference wallet. See the [custody contract](../CUSTODY.md) for shared
integration boundaries and validation limits.
Native Android (Kotlin + Compose) wallet that mirrors the [Phase 1 web wallet](../README.md)
and talks to the same in-repo demo backend.

## Current implementation and validation

The implementation now routes signing through `lib/HolderKey.kt`, with an Android
Keystore path and a software fallback. `lib/Wallet.kt` also has SD-JWT handling.
Check the selected backend on the actual device; API level or Keystore membership
alone does not establish hardware provenance. On 2026-09-06, the Android app compiled
and its two Base58 unit tests passed with JDK 17 and the Gradle 8.9 wrapper. The
shared module compiled; it currently has no test source. No emulator, physical-device
or hardware-custody validation was performed. CI runs the same app tests and shared
compilation. The phase notes below describe the original baseline and are not a
current feature-completeness or production-custody guarantee.

Run the automated checks from this directory:

```sh
./gradlew :app:testDebugUnitTest :shared:compileKotlinJvm --max-workers=2
```

## Original Phase 2 baseline

- **First-run bootstrap.** Generates a `did:key` Ed25519 holder identity; stored in
  EncryptedSharedPreferences.
- **Browse credentials** with delete + reset actions.
- **Receive** a Bachelor of Science VC from the demo issuer (HTTP call to the Next.js
  server in `../`).
- **Present** a credential to the demo verifier, sign a Verifiable Presentation, and
  see the verifier's 8-step checklist + claim-by-claim disclosure breakdown.

## Original Phase 2 follow-up plan (historical)

- **Holder key in Android Keystore.** Phase 2 baseline uses EncryptedSharedPreferences
  (wrapping key is Keystore-bound, but the Ed25519 seed itself lives in encrypted prefs).
  Phase 2.5 binds the holder signing key directly to Keystore (Ed25519 on API 33+, or
  wrapped-seed on older devices).
- **iOS.** Phase 2.5. Requires Xcode + Swift crypto decisions (CryptoKit Ed25519 is
  iOS 17+ only, otherwise swift-crypto).
- **KMM shared code.** Currently Android-only. Migration to a shared `wallet-core-mp`
  consumer is the natural follow-up once iOS arrives.
- **QR / NFC / deep links.** Phase 2 stays HTTP-driven for parity with the web wallet.
- **Real OID4VCI / OID4VP wire-format compliance.** Same simplification as web wallet.
- **Selective disclosure (SD-JWT VC).** Phase 2.5.

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK Platform 34 (target) and at least Platform 29 (min)
- JDK 17 (Android Studio bundles one)
- The demo backend running: `cd ../ && npm install && npm run dev`

## Run it

### Emulator

The default `DEMO_BACKEND_BASE_URL` is `http://10.0.2.2:3000` (the Android-emulator
alias for the host machine). Just:

```bash
cd reference-wallet/android
./gradlew :app:installDebug
adb shell am start -n org.trustweave.referencewallet/.ui.MainActivity
```

…or open the project in Android Studio and hit Run.

### Real device

Set the backend URL to your machine's LAN IP:

```bash
./gradlew :app:installDebug -PDEMO_BACKEND_BASE_URL=http://192.168.1.42:3000
```

Make sure the dev machine and device are on the same network and the Next.js server is
listening on `0.0.0.0` (Next.js does by default on `npm run dev`).

## Architecture

```
app/
├── WalletApp.kt              Application entry — registers BouncyCastle for Ed25519
└── lib/                      Mirror of reference-wallet/lib (web TypeScript)
│   ├── Crypto.kt             Ed25519 + did:key + JWS sign/verify (via BC)
│   ├── Base58.kt             Standalone bs58 codec
│   ├── Storage.kt            EncryptedSharedPreferences adapter
│   ├── Wallet.kt             Facade: bootstrap, store, list, createPresentation
│   └── DemoBackend.kt        OkHttp client for issuer + verifier endpoints
└── ui/                       Compose UI
    ├── Theme.kt              Material 3 colours (matches web wallet palette)
    ├── MainActivity.kt       NavHost + bottom-bar tabs
    ├── HomeScreen.kt
    ├── ReceiveScreen.kt
    └── PresentScreen.kt
```

Same conceptual layout as the web wallet's `app/` and `lib/`. When the Kotlin
`wallet-core-mp` SDK adds the holder-side capability surface, the `lib/Wallet.kt` here
becomes a thin wrapper rather than an implementation.

## Crypto notes

- Ed25519 keypair generation, signing, and verification use **Bouncy Castle 1.78.1**
  (registered as the highest-priority JCE provider in `WalletApp.onCreate()`). The
  Android-bundled BouncyCastle is a stripped fork that lacks Ed25519 on older API levels.
- did:key encoding uses the standalone `Base58.kt` (Bitcoin alphabet) — kept small to
  avoid dragging in a wallet-grade library.
- VP-JWT signing produces a compact JWS with `alg=EdDSA`, mirroring the web wallet's
  `signJws()` so VPs round-trip cleanly to the demo verifier.

## Known limitations

- **First-run race**: the Application class registers BouncyCastle synchronously on
  `onCreate()`, so the first call to `Crypto.generateEd25519()` from a Compose
  `LaunchedEffect` is safe. If you add background services that touch crypto before
  Application init, that ordering needs revisiting.
- **No background sync** — credentials only update when you open the app.
- **Cleartext HTTP**: `android:usesCleartextTraffic="true"` is on for the demo so HTTP
  calls to `10.0.2.2` work. A production build would remove this and require HTTPS.

## License

See the repository [LICENSE](../../LICENSE) and [commercial license terms](../../LICENSE-COMMERCIAL.md).
