# TrustWeave Reference Wallet — Expo (React Native)

The fourth reference wallet alongside [web](../README.md), [native Android](../android/README.md),
and [native iOS](../ios/README.md). Single TypeScript codebase for iOS + Android (+ web)
via Expo SDK 51.

**Best for:** quick on-device demos, customer evaluations where the partner's team is
already TypeScript-first, and anything where install-friction matters more than
hardware-bound holder keys.

**Not for:** wallets that require Secure Enclave / Android Keystore-bound Ed25519
signing. For that, the [native Android wallet](../android/) is the reference (and
its `HolderKey.Keystore` backend is the pattern to study).

## Quickstart — Expo Go (~2 min, no Xcode/Android Studio)

```bash
cd reference-wallet/expo
npm install
npx expo start
```

Press `i` for iOS simulator (requires Xcode), `a` for Android emulator (requires
Android Studio), or — the magic step — **scan the QR with the Expo Go app** on your
phone. The wallet runs on-device with hot reload, no platform IDE required.

### Configure the demo backend URL

Default in `app.json` → `expo.extra.demoBackendBaseUrl` is `http://10.0.2.2:3000`
(Android emulator alias). For Expo Go on a real phone, change it to your host
machine's LAN IP:

```bash
# Find your LAN IP (Windows PowerShell)
(Get-NetIPAddress -AddressFamily IPv4 |
  Where-Object { $_.InterfaceAlias -notmatch 'Loopback|vEthernet|WSL' -and $_.IPAddress -notmatch '^169\.' }
).IPAddress
```

Edit `app.json`:

```json
"extra": {
  "demoBackendBaseUrl": "http://192.168.1.42:3000"
}
```

Then `npx expo start` again. (iOS simulator can use `http://localhost:3000`
directly because it shares the host's network.)

### Start the demo backend

In another terminal:

```bash
cd reference-wallet
npm install
npm run dev -- -H 0.0.0.0   # bind to LAN, not just localhost
```

## What's in here

```
expo/
├── app/                          expo-router file-based screens
│   ├── _layout.tsx               Tabs root + polyfill import
│   ├── index.tsx                 Home (default route)
│   ├── receive.tsx
│   └── present.tsx
├── lib/
│   ├── crypto.ts                 Ed25519 (@noble) + did:key + base64url + JWS
│   ├── sdjwt.ts                  SD-JWT VC encode/decode/present + expo-crypto random
│   ├── storage.ts                expo-secure-store wrapper
│   ├── wallet.ts                 Facade (async — SecureStore is async)
│   └── demoBackend.ts            fetch() client; URL from app.json extra
├── polyfills.ts                  imports react-native-get-random-values
├── app.json                      Expo config (demo URL, scheme, platforms)
├── package.json
├── tsconfig.json
└── babel.config.js
```

## Tradeoff: the holder key

The reference wallet's design doc calls out a tier list for holder-key protection:

1. **Hardware-bound** — Secure Enclave / Android Keystore. Private key never enters
   userspace memory. The [native Android wallet](../android/) Phase 2.5b implements
   this with Keystore-bound Ed25519 on API 33+.
2. **Encrypted-at-rest** — software seed wrapped by a hardware-backed master key.
   This is where **the Expo wallet sits**: `expo-secure-store` uses Keychain on iOS
   and EncryptedSharedPreferences on Android, both Keystore/Secure Enclave-rooted,
   but the Ed25519 seed itself is loaded into JS for signing.
3. **Software-only** — the seed sits in plaintext storage. The Phase 1 web wallet
   uses `localStorage` for demo simplicity.

For most demo flows tier 2 is acceptable. For a production wallet handling PID or
QEAA (eIDAS qualified attestations), tier 1 is the bar — fork the native Android
wallet and write the equivalent Swift `HolderKey.Keystore` using CryptoKit + Secure
Enclave (P-256 only) or a custom Expo native module.

## EAS Build for standalone APK / IPA

```bash
npm install -g eas-cli
eas login
eas build:configure
eas build --platform android --profile preview   # installable APK
eas build --platform ios --profile preview       # IPA (requires Apple Developer Program for sign)
```

Expo's cloud build service does the heavy lifting — no local Android SDK or Xcode
needed for the build itself, though iOS distribution still needs an Apple Developer
account for code signing.

## Known places that may need attention

- **TextEncoder / TextDecoder in older RN.** Used in `lib/crypto.ts`. React Native
  0.74+ ships them; older RN needs `text-encoding-polyfill`. If you see runtime
  errors mentioning `TextEncoder is not defined`, add that polyfill to
  `polyfills.ts`.
- **`crypto.getRandomValues` on Hermes.** Polyfilled via `react-native-get-random-values`
  in `polyfills.ts` which loads first from `app/_layout.tsx`. If you skip the
  polyfill, `@noble/ed25519`'s `randomPrivateKey()` throws.
- **expo-secure-store 2KB limit on Android.** Single credentials are ~1.5KB which
  fits; we store one credential per item to stay under the limit even for very
  large ones.
- **Web target.** `expo start --web` runs in the browser but `expo-secure-store`
  doesn't work there. The web target is a stub for layout preview only — for an
  actual browser wallet use the [Next.js build](../) instead.

## Comparison to the other wallets

| Concern              | Web (Next.js) | Android (Compose)        | iOS (SwiftUI)         | **Expo (RN)**           |
| -------------------- | ------------- | ------------------------ | --------------------- | ----------------------- |
| Holder key           | localStorage  | Keystore (API 33+) or ES | Keychain (seed)       | expo-secure-store (seed)|
| Single codebase      | n/a           | Android only             | iOS only              | iOS + Android (+ web)   |
| Dev install friction | low           | medium (AGP + SDK)       | high (Xcode, Mac)     | **lowest** (Expo Go)    |
| Native UI feel       | n/a           | yes (Compose)            | yes (SwiftUI)         | RN approximation        |
| Hardware key story   | weakest       | **strongest**            | strong                | weak                    |
| OTA updates          | n/a           | no                       | no                    | yes (Expo Updates)      |

Pick the right one for the audience.

## License

Apache 2.0.
