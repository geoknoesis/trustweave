# NFC Tap-to-Present Demo

NFC carries only the verifier link; the credential exchange runs over HTTP to the
demo backend (same as the QR flow). The phone is the NFC **reader**; the verifier
is a passive NTAG sticker.

## 1. Program the tag

Use any NFC writer app (e.g. "NFC Tools") to write **one NDEF URI record**:

```
http://<your-LAN-ip>:3000/verifier
```

Use the same host/port as `extra.demoBackendBaseUrl` in `app.json` — that value is
currently hardcoded to a sample LAN IP, so **edit it to your machine's IP** and write
the tag with the matching URL. This is the exact string the verifier QR encodes, so
the wallet treats a tap identically to a scan — including fetching a fresh nonce from
the backend per presentation.

NTAG213/215/216 stickers all work. A URI record is preferred; a Text record
containing the same URL also works (the reader handles both record types).

## 2. Build a dev client (NFC needs native code — not Expo Go)

```bash
cd reference-wallet/expo
eas build --profile preview --platform android   # produces an installable APK
# or, for a local run:
npx expo prebuild && npx expo run:android
```

## 3. Run the demo

1. Start the backend: `cd reference-wallet && npm run dev` (listens on :3000).
2. Make sure the phone and dev machine share a LAN and the IP matches step 1.
3. Open the wallet → **Share** tab → tap **"📲 Tap an NFC verifier tag"**.
4. Tap the phone to the sticker → review & share → see the verification result.

## iOS (stretch)

Requires a paid Apple Developer account (for the NFC entitlement), a physical
iPhone 7+, and an EAS iOS build. iOS shows a system NFC sheet during the read;
the rest of the flow is identical.

## Troubleshooting

- **Button missing:** device has no NFC, NFC is off (Android: enable in settings),
  or you are on web. The QR flow still works.
- **"no verifier link on it":** the tag wasn't written with the URL — rewrite it.
- **"not a TrustWeave verifier link":** the URL path must be `/verifier`.
- **Cannot reach backend:** wrong LAN IP, or backend not on `0.0.0.0:3000`.
