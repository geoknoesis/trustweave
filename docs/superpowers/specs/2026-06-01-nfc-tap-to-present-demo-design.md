# NFC Tap-to-Present Demo — Design

**Date:** 2026-06-01
**Status:** Draft for review
**Owner:** Stephane Fellah

## Goal

Demonstrate presenting a TrustWeave credential by **tapping a phone to an NFC
target**. The tap carries only the *engagement* — an OpenID4VP request — and the
actual credential exchange completes over HTTPS/HTTP against the existing demo
backend. The viewer should walk away convinced that "tap → present credential →
verifier shows result" works end to end on a real phone.

This is explicitly **not** full ISO/IEC 18013-5 NFC proximity (no NFC data
retrieval, no device-signed mdoc over NFC). NFC is the trigger only.

## Scope decisions (locked during brainstorming)

| Decision | Choice |
|---|---|
| Demo goal | Tap-to-present (engagement over NFC, exchange over HTTP) |
| Tap direction | **Phone reads** a verifier tag/terminal (phone is the NFC reader) |
| Holder app | The **Expo** reference wallet (`reference-wallet/expo`) |
| Verifier "terminal" | A passive **NTAG** sticker carrying an NDEF URI record |
| Primary platform | **Android first**; iOS is a documented stretch goal |
| Credential exchange | Reuse the wallet's existing OID4VP/present flow to the `:3000` backend |

## Why the Expo wallet

The Expo wallet is already structured for this:

- It runs as a **dev/EAS build, not Expo Go** (it uses the `expo-camera` and
  `expo-secure-store` config plugins). NFC is a native module that also cannot run
  in Expo Go — but since the camera already forces a dev build, NFC adds **no new
  workflow constraint**.
- It already has a **QR "scan to receive" flow** and a **present flow** against the
  `:3000` Next.js demo backend (`extra.demoBackendBaseUrl` in `app.json`), with
  cleartext HTTP whitelisted for both iOS and Android. NFC tap-to-present is the
  sibling of the QR trigger — same backend, same presentation logic, new entry point.
- One codebase reaches **both Android and iOS** NFC tag reading, which the native
  Kotlin wallet could not (iOS NFC is too restricted for a third-party native build).

## Architecture

```
┌─────────────────┐   1. tap (NFC)    ┌──────────────────────────┐
│ NTAG sticker     │ ◄──────────────── │ Expo wallet (holder)      │
│ NDEF URI record: │                   │  react-native-nfc-manager │
│  openid4vp://...  │ ──── URI ───────► │  → parse request          │
│  request_uri=     │                   │  → pick & present cred    │
│  https://:3000/.. │                   └────────────┬─────────────┘
└─────────────────┘                                  │ 2. OID4VP over HTTP
                                                      ▼
                                       ┌──────────────────────────┐
                                       │ Demo backend (Next.js :3000)│
                                       │  verifier endpoint:         │
                                       │  - mints fresh nonce        │
                                       │  - receives VP, verifies    │
                                       │  - shows result             │
                                       └──────────────────────────┘
```

**NFC carries one thing only:** the OID4VP request URI (or its `request_uri`
pointer). Everything after the tap is the existing exchange.

## Components

1. **NTAG sticker (verifier terminal).**
   A passive NTAG215 (or similar) written once with a single **NDEF URI record**.
   The URI is either a full OID4VP authorization request or a short
   `request_uri=https://<backend>/verify/request` pointer that the wallet
   dereferences. Using `request_uri` is preferred so the backend can **mint a fresh
   nonce per tap** — keeping presentations replay-resistant without reprogramming
   the sticker. A one-time CLI/web helper (or a phone "write tag" utility) programs
   the sticker; not part of the shipped app.

2. **NFC read module in the Expo wallet.**
   Add `react-native-nfc-manager` and its **Expo config plugin** to `app.json`
   `plugins` (wires the Android `NFC` permission and the iOS NFC entitlement +
   usage string). A thin TypeScript wrapper exposes
   `scanForVerifierRequest(): Promise<Oid4vpRequestUri>` that:
   - starts a foreground NFC session,
   - reads the first NDEF URI record,
   - validates the scheme/host against an allowlist,
   - cancels/cleans up the session on success, cancel, or timeout.
   The module must be **gated to `Platform.OS !== 'web'`**; web keeps QR/manual.

3. **"Scan to verify" entry point (UI).**
   A button on the present/home screen that calls `scanForVerifierRequest()` and
   then hands the parsed request to the **existing present flow** (the same code the
   QR path already uses). On Android the read is silent in-app; on iOS the system
   NFC sheet appears. No new presentation logic — only a new trigger.

4. **Verifier backend endpoint (likely already exists for QR).**
   `GET /verify/request` returns a fresh OID4VP request (with nonce);
   `POST /verify/response` receives and verifies the VP and renders the result.
   If the QR demo already exposes these, NFC reuses them unchanged. Confirm during
   the plan phase.

## Data flow

1. Verifier shows the NTAG sticker (on a placard/"terminal").
2. Holder taps **Scan to verify**, then taps the phone to the sticker.
3. Wallet reads the NDEF URI → resolves `request_uri` → gets a fresh OID4VP request.
4. Wallet selects a matching credential, builds the VP, signs it (existing holder
   key in `expo-secure-store`), POSTs to the verifier.
5. Verifier verifies and displays the checklist/result; wallet shows success.

## Error handling

- **No NDEF / wrong record type:** surface "Not a TrustWeave verifier tag," cancel session.
- **Disallowed host in URI:** reject before any network call (allowlist check).
- **NFC unavailable/disabled:** detect via `NfcManager.isSupported()/isEnabled()`;
  fall back to the existing QR path with a clear message.
- **Session timeout / user cancel (esp. iOS sheet):** clean up the session, no-op.
- **Backend/exchange errors:** reuse the present flow's existing error UI.
- **Web platform:** NFC button hidden; QR/manual only.

## Testing

- **Unit:** NDEF-URI parser and host allowlist (pure TS, platform-independent).
- **Manual device matrix:** Android phone reading a real NTAG sticker end to end;
  (stretch) iPhone reading the same sticker via the system sheet.
- **Negative manual:** blank tag, non-URI tag, disallowed host, NFC turned off.
- Existing present-flow tests stay as-is (NFC only adds a trigger).

## Constraints & risks (honest)

1. **Not Expo Go** — requires a dev/EAS build. Already true for this wallet (camera).
2. **iOS overhead:** paid Apple Developer account + physical iPhone (7+) +
   NFC entitlement; no simulator NFC. **Android has none of this.** This is the
   reason for the Android-first call.
3. **New Architecture:** `newArchEnabled` is on — pin a `react-native-nfc-manager`
   version that supports the new arch.
4. **Web build:** library is native-only; NFC button must be platform-gated.
5. **Sticker freshness:** rely on `request_uri` → backend-minted nonce so a static
   sticker still yields replay-resistant presentations.

## Out of scope

- ISO 18013-5 NFC data retrieval / device-signed mdoc over NFC.
- Host Card Emulation (phone-as-card) and verifier reader apps.
- Google/Apple first-party Wallet provisioning.
- Production HTTPS hardening of the demo backend.

## Open assumption to confirm on review

- **Android-first** is assumed; iOS is a stretch. Flip this if iOS must be in the
  demo on day one (adds the Apple provisioning track to the plan).
- The `:3000` verifier already exposes a request/response endpoint pair usable by
  both QR and NFC (to be confirmed when the plan inspects the backend).
