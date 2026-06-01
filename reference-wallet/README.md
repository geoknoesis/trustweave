# TrustWeave Reference Wallets

Four wallet implementations of the same conceptual surface — receive a credential,
hold it, present it (with selective disclosure), get the verifier's verdict back.
Each fork-able as a starter kit for a different audience.

**This is not a product.** It's the working demo for the TrustWeave Wallet SDK and the
canonical reference for partners building on top of it. See
[`docs/.internal/reference-wallet-design.md`](../docs/.internal/reference-wallet-design.md)
for the full design intent.

## Which one to fork?

| Audience                                            | Fork                       |
| --------------------------------------------------- | -------------------------- |
| TypeScript / Next.js web app embedding a wallet     | [Web (Next.js)](#web)      |
| Native Android wallet, especially regulated /       | [Android (Compose)](#android) |
| eIDAS scenarios needing Keystore-bound holder keys  |                            |
| Native iOS wallet, CryptoKit + Keychain             | [iOS (SwiftUI)](#ios)      |
| Quickest on-device demo / customer evaluation       | [Expo (React Native)](#expo) |
| Cross-platform iOS + Android from a single codebase | [Expo (React Native)](#expo) |

If your audience is "all of the above," start with **Expo** for the demo and point
real customers at whichever native build matches their stack.

## Feature comparison

| Concern                              | Web (Next.js)          | Android (Compose)            | iOS (SwiftUI)        | Expo (RN)              |
| ------------------------------------ | ---------------------- | ---------------------------- | -------------------- | ---------------------- |
| **Status**                           | working                | working                      | scaffold (unvalidated)| working               |
| Phase                                | 1 + 2.5a               | 2 + 2.5a + 2.5b              | 2.5d                 | 2.5e                   |
| Single codebase covers               | web                    | Android only                 | iOS only             | iOS + Android (+ web)  |
| Dev install friction                 | low (npm + Node)       | medium (AGP + Android SDK)   | high (Xcode, Mac)    | **lowest** (Expo Go)   |
| Holder key location                  | localStorage           | **Keystore-bound Ed25519** (API 33+) or EncryptedSharedPrefs | Keychain (seed) | expo-secure-store (seed) |
| Hardware-bound private key           | no                     | **yes** (API 33+)            | no (Phase 2.5)       | no                     |
| Native UI feel                       | n/a                    | yes (Compose)                | yes (SwiftUI)        | RN approximation       |
| OTA updates                          | redeploy               | Play Store                   | App Store            | Expo Updates           |
| SD-JWT VC end-to-end                 | yes                    | yes                          | yes                  | yes                    |
| KMM shared logic (scaffold)          | n/a                    | yes (`android/shared/`)      | planned (2.5e)       | n/a (JS, not Kotlin)   |
| Demo deploy target                   | localhost              | phone via ADB                | simulator / TestFlight | **phone via Expo Go QR** |

## Layout

```
reference-wallet/
├── README.md           ← you are here
├── package.json        ← web wallet (Next.js) — historically at root
├── app/                ← web wallet routes + API endpoints (demo issuer + verifier)
├── lib/                ← web wallet TypeScript lib
├── android/            ← native Android wallet (Compose + KMM shared module)
├── ios/                ← native iOS wallet (SwiftUI, unvalidated)
└── expo/               ← Expo / React Native wallet
```

The web wallet is structurally at the root for historical reasons (it was Phase 1
and predates the other siblings). All other implementations live in their own
subdirectory.

## Each sibling

### Web

> [Quick start](./#run-the-web-wallet) · Full README: this file (web is the
> historical root)

```bash
cd reference-wallet
npm install
npm run dev -- -H 0.0.0.0
# open http://localhost:3000
```

The web wallet contains BOTH the holder app AND the demo backend API routes
(issuer + verifier). The other three wallets all talk to *these same* `/api/`
endpoints — so the web wallet must be running for the others to do anything.

See [`docs/.internal/reference-wallet-design.md`](../docs/.internal/reference-wallet-design.md)
for the original Phase 1 design.

### Android

> [README](./android/README.md)

Native Compose + Material 3. Phase 2.5b adds Keystore-bound Ed25519 (API 33+
hardware-bound, otherwise EncryptedSharedPreferences fallback). Phase 2.5c scaffolds
a KMM shared module ready to grow iOS targets.

The only sibling that hits *tier 1* on the holder-key tier list — fork this for
production wallets that need PID/QEAA-grade key protection.

### iOS

> [README](./ios/README.md)

SwiftUI + CryptoKit Ed25519 + Keychain. **Unvalidated** — written without macOS/Xcode
access; expect a small number of Swift syntax fixes when first opened in Xcode.
Phase 2.5e ports the KMM shared module to iOS targets and replaces the duplicated
Swift `SdJwt.swift` + `Crypto.swift` with `import Shared`.

### Expo

> [README](./expo/README.md)

React Native via Expo SDK 51. Lowest install friction — scan a QR code in Expo Go
on your phone, the app runs instantly. EAS Build for standalone APK/IPA when ready.

Holder key sits at *tier 2* (Keychain/EncryptedSharedPreferences-rooted but the
seed is loaded into JS for signing). Acceptable for most demos; not for PID/QEAA.

## Demo flow (works identically across all four)

Open **`/demos`** for a hub linking all runnable scenarios.

### Education — demo-university

The web wallet includes a **demo-university** trust domain (`data/trust-domains/demo-university/`) with five CSV-backed degrees.

| Web route | Purpose |
|-----------|---------|
| `/issuer/degrees` | Graduate roster |
| `/issuer/degree/[studentId]` | Degree detail + offer QR (graduate portal) |
| `/issuer/offer` | Combined issuer console |
| `/verifier` | Live verifier QR + status |
| `/verifier/test` | Automated E2E test (no phone) |

1. **Receive** — scan an offer QR from `/issuer/degree/STU-001` (or `/issuer/offer`).
   Wallet calls `GET /api/demo-issuer/credential?subject=<DID>&studentId=…` and stores the SD-JWT VC.
2. **Present** — scan the verifier QR from `/verifier`, choose disclosures, tap Share.
   Wallet signs a key-binding JWT and posts to `/api/demo-verifier/verify`.

### Spatial Web — drone airspace

Two trust domains: **FAA drone registry** (`demo-faa-drone-registry/`) and **SF Bay airspace authority** (`demo-sf-airspace/`). Five drones with registered aircraft photos in `public/drones/` (480×360 JPEG, ≤50 KiB — regenerate from SVG sources with `npm run resize-demo-photos`).

| Web route | Purpose |
|-----------|---------|
| `/issuer/faa` | FAA UAS registration roster |
| `/issuer/faa/[droneId]` | Drone ID credential + aircraft photo + offer QR |
| `/issuer/airspace` | Airspace activity authorization roster |
| `/issuer/airspace/[droneId]` | Activity authorization + offer QR |
| `/airspace/gate` | Gatekeeper — location/activity session + presentation QR |

1. **Receive FAA ID** — scan offer QR from `/issuer/faa/DRONE-001` → `DroneIdentificationCredential` (photo URL + digest claims).
2. **Receive airspace auth** — scan offer QR from `/issuer/airspace/DRONE-001` → `ActivityAuthorizationCredential`.
3. **Present at gate** — on `/airspace/gate`, set activity `data-collection` and SF coordinates, open gate QR, scan from **Share** tab. Gate verifies crypto + geographic policy (try LA coords to see denial).

API discovery: `GET /api/demo-issuer/faa/trust-domain`, `GET /api/demo-issuer/spatial/trust-domain`.

**Kotlin equivalent:** `./gradlew :distribution:examples:runSpatialWeb`

### Shared steps (all demos)

1. **Bootstrap** — wallet generates a `did:key:z…` Ed25519 holder identity on first
   run. Stored per the wallet's storage tier.
2. **Verify outcome** — verifier or gate returns a structured checklist (issuer signature, disclosure
   hashes, KB-JWT signature, audience binding, nonce binding, sd_hash binding,
   temporal validity) plus the disclosed-vs-withheld breakdown (gate also checks location policy).

## Shared design decisions

These choices are identical across all four siblings on purpose:

- **did:key with Ed25519** (multicodec `0xED 0x01`, base58btc-encoded). Smallest
  reasonable DID method, no resolution infrastructure required.
- **SD-JWT VC** as the credential format (IETF draft-ietf-oauth-sd-jwt-vc). Phase
  2.5a swap from plain VC-JWT.
- **EdDSA / Ed25519** for all JWS signatures (issuer JWT, holder VP JWT, KB-JWT).
- **`~`-separated SD-JWT VC compact form**: `<jwt>~<d1>~<d2>~…~<kb-jwt>`.
- **No OID4VCI / OID4VP wire compliance** in the demo flow — the simplified direct
  HTTP shape preserves the conceptual model without the full spec dance. Real-world
  customers swap this for proper OID4VCI/OID4VP at integration time.

## License

Apache 2.0 across all four. Fork freely.
