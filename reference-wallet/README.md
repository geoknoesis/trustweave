# TrustWeave reference wallets

This directory contains a web demo and Android, iOS and Expo implementations.
The web app includes the holder wallet and the demo issuer/verifier backend.
These examples demonstrate selected flows; they are not production custody products.

## Run the web wallet

Use Node.js 24 or newer. From the repository root:

```bash
cd reference-wallet
npm ci
npm run dev
```

Open http://localhost:3000. WebCrypto, IndexedDB and camera access need a secure
browser context. For another device, configure HTTPS and an appropriate reachable
backend URL; plain HTTP on a LAN address is not equivalent to localhost.

For a production-mode local check:

```bash
npm run build
npm run start
```

Run automated checks from `reference-wallet`:

```bash
npm test
npm run build
npx playwright install chromium
npx playwright test
```

Playwright starts the test harness on port 4174 and the built Next.js app on 4175.
Those ports must be free. The browser suite includes a virtual WebAuthn authenticator;
it does not attest a physical security device.

## What the web wallet supports

- Ed25519 did:key issuer credentials in the bounded `vc+jwt` and `vc+sd-jwt` profiles.
- Top-level object disclosures, with one SD-JWT credential per presentation.
- Signature, issuer/holder binding, validity and disclosure checks during import.
- Browser-managed non-extractable Ed25519 signing and X25519 agreement keys in
  IndexedDB. Public identity metadata and credentials remain in localStorage.
- Credential backup export/restore. Backups contain credential data, **not private
  keys**. Restore requires the matching holder identity and an available device key.
- Explicit lost-key replacement that retains old credentials for issuer reissuance.
  Replacement creates a new DID; it does not reconstruct the old key or automatically
  revoke/rebind credentials. Corrupt key records do not trigger silent rotation.

The simplified demo HTTP protocol is not a claim of OID4VCI/OID4VP wire compliance.
Nested/array selective disclosures, arbitrary issuer DID methods and general SD-JWT
interoperability are outside the implemented import profile.

## Custody and recovery

The current web enrollment/presentation UI uses browser software custody. Malicious
same-origin code or a compromised browser can still invoke signing. Non-extractability
is not proof of hardware protection.

Both passkey and managed-KMS adapters exist under `lib/custody`, but are experimental
and **not wired into the existing wallet UI**. See [CUSTODY.md](CUSTODY.md) for their
API contracts, proof differences and missing production integration. Neither adapter
provides an automatic fallback to the browser key.

Use the wallet's **Back up and restore credentials** section to export credentials.
After device-key loss, use the explicit replacement flow and contact issuers to
verify identity, revoke old credentials where appropriate and issue replacements.

## Demo routes

Open `/demos` for the available scenarios.

| Route | Purpose |
|---|---|
| `/issuer/degrees` | University graduate roster |
| `/issuer/degree/STU-001` | Degree offer for the sample student |
| `/issuer/faa` | Drone registration roster |
| `/issuer/airspace` | Airspace authorization roster |
| `/airspace/gate` | Geographic/activity verification demo |
| `/receive` | Credential import |
| `/present` | Holder presentation and disclosure selection |
| `/verifier` | Verifier challenge and outcome |
| `/verifier/test` | Demo end-to-end verifier checks |

The web app must be running when a native sibling uses its `/api/` issuer/verifier
endpoints. Do not assume that every sibling supports every current web flow.

## Native and cross-platform implementations

| Implementation | Source and validation boundary |
|---|---|
| [Android](android/README.md) | Compose wallet with a Keystore signing path and software fallback. Check the actual selected backend/device; API level alone does not establish hardware custody. |
| [iOS](ios/README.md) | SwiftUI/CryptoKit/Keychain scaffold; requires macOS/Xcode validation. |
| [Expo](expo/README.md) | React Native demo; stored seed material is loaded into the app for signing. Check its own package/runtime requirements. |

Native build/device execution is not covered by the web test results. The [custody contract](CUSTODY.md)
describes the implemented adapters and their validation limits.

## License

See the repository [LICENSE](../LICENSE) and
[commercial license terms](../LICENSE-COMMERCIAL.md). This README does not grant a
separate license for the wallet implementations.
