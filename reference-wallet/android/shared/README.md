# Reference Wallet Shared (KMM)

Multiplatform code shared between the Android wallet (and, in Phase 2.5d, the iOS
wallet). Phase 2.5c scope:

- `commonMain`: SD-JWT VC encode/decode + present, base64url, `expect` declarations
  for SHA-256, secure random, and Ed25519 sign/verify/keygen.
- `jvmMain`: JVM/Android actuals using `java.security.MessageDigest` + `SecureRandom`
  and Bouncy Castle's low-level Ed25519 API.

Pure-Kotlin code, no platform-specific imports in `commonMain`. Compiles in any KMM
target as soon as actuals for the four crypto primitives exist.

## Build it

```bash
cd reference-wallet/android
./gradlew :shared:build
```

## Why this isn't in `wallet-core-mp` yet

The trustweave main repo has [`wallet-core-mp`](../../../wallet/wallet-core-mp) and
[`credential-models-mp`](../../../credentials/credential-models-mp) as broader SDK
modules. They contain credential and wallet models for issuer + verifier + holder.
This module is a narrower _holder_ surface: just the SD-JWT VC + base64url + crypto
primitives a holder wallet needs.

A future refactor could collapse this into `wallet-core-mp` once that module gains
the SD-JWT VC + selective-disclosure capability surface. For now they're separate so
the reference wallet's Phase 2.5c work doesn't block on broader SDK changes.

## What's deliberately NOT here

- **Android Keystore integration.** That lives in
  `app/src/main/kotlin/.../lib/HolderKey.kt` because it's Android-specific JCA work.
  Common code consumes it through the `(ByteArray) -> ByteArray` signer closure
  argument on `SdJwtVc.present` and `SdJwtVc.signCompactJws`.
- **Credential storage.** Platform-specific: EncryptedSharedPreferences on Android,
  Keychain on iOS. Stays in each platform's app module.
- **iOS targets.** Phase 2.5d. The Gradle config has the commented-out target block
  and uses `applyDefaultHierarchyTemplate()` so adding them is a one-line change.
- **Network / OkHttp / URLSession.** Network is per-platform; the shared module
  doesn't make HTTP calls.

## Adding the iOS target (Phase 2.5d)

In `build.gradle.kts`, uncomment:

```kotlin
iosX64()
iosArm64()
iosSimulatorArm64()
```

Add a sibling `iosMain` source set with actuals for the four primitives in
`CryptoPrimitives.kt`. Suggested implementations:

- `sha256` → CommonCrypto's `CC_SHA256`
- `secureRandomBytes` → `SecRandomCopyBytes`
- `ed25519GenerateKeyPair` / `ed25519Sign` / `ed25519Verify` → CryptoKit's
  `Curve25519.Signing.PrivateKey` (iOS 17+) or `swift-crypto` (back-port)

The Android `app` module would then continue to depend on `:shared` via JVM target;
the iOS app would consume the framework via XCFramework.
