# NFC Tap-to-Present Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the Expo reference wallet start a credential presentation by tapping the phone to an NFC tag that carries the verifier's URL, reusing the existing QR present flow for everything after the tap.

**Architecture:** An NFC tag (NTAG) holds an NDEF URI record containing the same string the verifier QR encodes (e.g. `http://<host>:3000/verifier`). A new platform-gated NFC module reads that record, decodes it to a raw string, and feeds it to the **existing** `parsePresentationRequestQr()` → `onVerifierScanned()` pipeline in `present.tsx`. No change to credential selection, VP building, disclosure, or backend submission. NFC is the trigger only.

**Tech Stack:** Expo SDK 54, React Native 0.81 (new architecture), TypeScript, expo-router, `react-native-nfc-manager` (+ its Expo config plugin), `jest-expo` for the one pure unit (added by this plan, since the wallet has no test runner yet).

---

## Why this is small

`present.tsx` already turns a scanned string into a `PresentationRequestQrPayload` and runs the entire exchange from there. The QR scanner ([VerifierQrScanner.tsx](../../../reference-wallet/expo/components/VerifierQrScanner.tsx)) is just one producer of that payload. NFC becomes a *second producer of the same payload*. The only genuinely new code is: read one NDEF record and decode its URI payload to a string.

## File structure

| File | Responsibility | Action |
|---|---|---|
| `reference-wallet/expo/lib/ndefUri.ts` | Pure decode of an NDEF URI/Text record payload (`number[]`) → string. No native imports, so it is unit-testable. | Create |
| `reference-wallet/expo/lib/__tests__/ndefUri.test.ts` | Unit tests for the decoder. | Create |
| `reference-wallet/expo/lib/nfcVerifierTag.ts` | Native wrapper over `react-native-nfc-manager`: `isNfcAvailable()` and `readVerifierTagRaw()`. Platform-gated. Uses `ndefUri.ts`. | Create |
| `reference-wallet/expo/components/NfcVerifierScanButton.tsx` | A button that runs `readVerifierTagRaw()`, parses with the existing `parsePresentationRequestQr`, and calls back with the payload. Renders nothing on web / when NFC unavailable. | Create |
| `reference-wallet/expo/app/present.tsx` | Render the NFC button in the `scan` phase next to the QR button; reuse `onVerifierScanned`. | Modify (`scan` phase block, ~L223-233) |
| `reference-wallet/expo/app.json` | Add the `react-native-nfc-manager` config plugin (Android NFC permission + iOS NFC usage/entitlement). | Modify (`plugins` array, L27-36) |
| `reference-wallet/expo/package.json` | Add `react-native-nfc-manager` dep; add `jest-expo`/`jest`/`@types/jest` devDeps and a `test` script. | Modify |
| `reference-wallet/expo/jest.config.js` | Jest preset config. | Create |
| `reference-wallet/expo/NFC-DEMO.md` | How to program the tag and run the demo (device matrix, troubleshooting). | Create |

---

## Task 1: Add a Jest test runner (jest-expo)

The Expo wallet currently has no test runner (`package.json` only has `typecheck`/`lint`). Add a minimal one so the pure decoder in Task 2 can be TDD'd.

**Files:**
- Modify: `reference-wallet/expo/package.json`
- Create: `reference-wallet/expo/jest.config.js`
- Create: `reference-wallet/expo/lib/__tests__/smoke.test.ts`

- [ ] **Step 1: Add devDeps and a test script to `package.json`**

In `reference-wallet/expo/package.json`, add to `scripts`:

```json
    "test": "jest"
```

and add to `devDependencies`:

```json
    "@types/jest": "^29.5.12",
    "jest": "^29.7.0",
    "jest-expo": "~54.0.0"
```

Then install:

```bash
cd reference-wallet/expo && npm install
```

- [ ] **Step 2: Create `jest.config.js`**

```js
/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-expo',
  testMatch: ['**/__tests__/**/*.test.ts?(x)'],
}
```

- [ ] **Step 3: Create a smoke test to prove the runner works**

`reference-wallet/expo/lib/__tests__/smoke.test.ts`:

```ts
describe('jest runner', () => {
  it('runs', () => {
    expect(1 + 1).toBe(2)
  })
})
```

- [ ] **Step 4: Run it**

```bash
cd reference-wallet/expo && npm test
```

Expected: PASS, 1 test in `smoke.test.ts`.

- [ ] **Step 5: Commit**

```bash
git add reference-wallet/expo/package.json reference-wallet/expo/package-lock.json reference-wallet/expo/jest.config.js reference-wallet/expo/lib/__tests__/smoke.test.ts
git commit -m "test(expo-wallet): add jest-expo test runner"
```

---

## Task 2: Pure NDEF URI/Text decoder (TDD)

NFC Forum URI records prefix the payload with a one-byte abbreviation code (e.g. `0x03` → `http://`). The decoder turns a record payload (`number[]`) into the full string. Text records are also supported as a fallback (some tag-writing apps default to Text).

**Files:**
- Create: `reference-wallet/expo/lib/ndefUri.ts`
- Test: `reference-wallet/expo/lib/__tests__/ndefUri.test.ts`

- [ ] **Step 1: Write the failing tests**

`reference-wallet/expo/lib/__tests__/ndefUri.test.ts`:

```ts
import { decodeNdefUriPayload, decodeNdefTextPayload, bytesOf } from '@/lib/ndefUri'

describe('decodeNdefUriPayload', () => {
  it('expands the http:// prefix code 0x03', () => {
    // 0x03 = "http://", then ASCII "host:3000/verifier"
    const payload = [0x03, ...bytesOf('host:3000/verifier')]
    expect(decodeNdefUriPayload(payload)).toBe('http://host:3000/verifier')
  })

  it('expands the https:// prefix code 0x04', () => {
    const payload = [0x04, ...bytesOf('example.org/verifier')]
    expect(decodeNdefUriPayload(payload)).toBe('https://example.org/verifier')
  })

  it('handles the no-prefix code 0x00 (full URI in body)', () => {
    const payload = [0x00, ...bytesOf('http://1.2.3.4:3000/verifier')]
    expect(decodeNdefUriPayload(payload)).toBe('http://1.2.3.4:3000/verifier')
  })

  it('returns null on empty payload', () => {
    expect(decodeNdefUriPayload([])).toBeNull()
  })

  it('returns null on an unknown prefix code', () => {
    expect(decodeNdefUriPayload([0x7f, ...bytesOf('x')])).toBeNull()
  })
})

describe('decodeNdefTextPayload', () => {
  it('strips the status byte + language code and returns the text', () => {
    // status byte 0x02 => 2-byte language code "en", then the text
    const payload = [0x02, ...bytesOf('en'), ...bytesOf('http://host:3000/verifier')]
    expect(decodeNdefTextPayload(payload)).toBe('http://host:3000/verifier')
  })

  it('returns null on empty payload', () => {
    expect(decodeNdefTextPayload([])).toBeNull()
  })
})
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd reference-wallet/expo && npm test -- ndefUri
```

Expected: FAIL — `Cannot find module '@/lib/ndefUri'`.

- [ ] **Step 3: Implement the decoder**

`reference-wallet/expo/lib/ndefUri.ts`:

```ts
/**
 * Pure helpers to decode NFC Forum NDEF record payloads into strings.
 * No native imports — unit-testable in node. Used by lib/nfcVerifierTag.ts.
 */

/** NFC Forum URI Record Type Definition abbreviation table (codes 0x00–0x23). */
const URI_PREFIXES: string[] = [
  '', // 0x00 — no prefix, full URI follows
  'http://www.',
  'https://www.',
  'http://',
  'https://',
  'tel:',
  'mailto:',
  'ftp://anonymous:anonymous@',
  'ftp://ftp.',
  'ftps://',
  'sftp://',
  'smb://',
  'nfs://',
  'ftp://',
  'dav://',
  'news:',
  'telnet://',
  'imap:',
  'rtsp://',
  'urn:',
  'pop:',
  'sip:',
  'sips:',
  'tftp:',
  'btspp://',
  'btl2cap://',
  'btgoep://',
  'tcpobex://',
  'irdaobex://',
  'file://',
  'urn:epc:id:',
  'urn:epc:tag:',
  'urn:epc:pat:',
  'urn:epc:raw:',
  'urn:epc:',
  'urn:nfc:',
]

/** Encode an ASCII string to a byte array (test helper + internal use). */
export function bytesOf(s: string): number[] {
  return Array.from(s, (c) => c.charCodeAt(0))
}

function bytesToUtf8(bytes: number[]): string {
  // Demo URLs are ASCII; this is sufficient and dependency-free.
  return String.fromCharCode(...bytes)
}

/** Decode an NDEF URI record payload (`number[]`) into a full URI string, or null. */
export function decodeNdefUriPayload(payload: number[]): string | null {
  if (!payload || payload.length === 0) return null
  const code = payload[0]
  const prefix = URI_PREFIXES[code]
  if (prefix === undefined) return null
  return prefix + bytesToUtf8(payload.slice(1))
}

/** Decode an NDEF Text record payload (`number[]`) into its text, or null. */
export function decodeNdefTextPayload(payload: number[]): string | null {
  if (!payload || payload.length === 0) return null
  const status = payload[0]
  const langLen = status & 0x3f // low 6 bits = language-code length
  if (payload.length < 1 + langLen) return null
  return bytesToUtf8(payload.slice(1 + langLen))
}
```

- [ ] **Step 4: Run to verify it passes**

```bash
cd reference-wallet/expo && npm test -- ndefUri
```

Expected: PASS — all `ndefUri` tests green.

- [ ] **Step 5: Commit**

```bash
git add reference-wallet/expo/lib/ndefUri.ts reference-wallet/expo/lib/__tests__/ndefUri.test.ts
git commit -m "feat(expo-wallet): add pure NDEF URI/Text payload decoder"
```

---

## Task 3: Add the NFC dependency and Expo config plugin

**Files:**
- Modify: `reference-wallet/expo/package.json`
- Modify: `reference-wallet/expo/app.json`

- [ ] **Step 1: Install `react-native-nfc-manager`**

```bash
cd reference-wallet/expo && npm install react-native-nfc-manager@^3.16.1
```

> Note: 3.16+ supports React Native's new architecture (this app sets `newArchEnabled: true`). If `npm install` resolves an older 3.x, force the `^3.16.1` range. This is a native module — it requires a dev/EAS build and will NOT run in Expo Go (the app already requires a dev build for `expo-camera`, so this adds no new constraint).

- [ ] **Step 2: Register the config plugin in `app.json`**

In `reference-wallet/expo/app.json`, change the `plugins` array (currently L27-36) to add the NFC plugin entry:

```json
    "plugins": [
      "expo-router",
      "expo-secure-store",
      [
        "expo-camera",
        {
          "cameraPermission": "Allow TrustWeave Wallet to scan degree offer QR codes from the issuer."
        }
      ],
      [
        "react-native-nfc-manager",
        {
          "nfcPermission": "Allow TrustWeave Wallet to read a verifier NFC tag to start a presentation."
        }
      ]
    ],
```

This wires the Android `android.permission.NFC` permission and the iOS `NFCReaderUsageDescription` + NDEF reader entitlement automatically at prebuild time.

- [ ] **Step 3: Verify config compiles (prebuild dry run)**

```bash
cd reference-wallet/expo && npx expo prebuild --platform android --no-install
```

Expected: completes without error; generated `android/app/src/main/AndroidManifest.xml` contains `uses-permission android:name="android.permission.NFC"`. (You may discard the generated `android/` dir afterward if you build via EAS.)

- [ ] **Step 4: Commit**

```bash
git add reference-wallet/expo/package.json reference-wallet/expo/package-lock.json reference-wallet/expo/app.json
git commit -m "build(expo-wallet): add react-native-nfc-manager + config plugin"
```

---

## Task 4: NFC reader wrapper module

A thin, platform-gated wrapper. `readVerifierTagRaw()` returns the decoded raw string from the first usable NDEF record; the caller parses it with the existing `parsePresentationRequestQr`.

**Files:**
- Create: `reference-wallet/expo/lib/nfcVerifierTag.ts`

- [ ] **Step 1: Implement the wrapper**

`reference-wallet/expo/lib/nfcVerifierTag.ts`:

```ts
import { Platform } from 'react-native'
import NfcManager, { NfcTech } from 'react-native-nfc-manager'
import { decodeNdefUriPayload, decodeNdefTextPayload } from '@/lib/ndefUri'

let started = false

async function ensureStarted(): Promise<boolean> {
  if (Platform.OS === 'web') return false
  if (started) return true
  try {
    const supported = await NfcManager.isSupported()
    if (!supported) return false
    await NfcManager.start()
    started = true
    return true
  } catch {
    return false
  }
}

/** True only when NFC hardware exists and is usable on this device. */
export async function isNfcAvailable(): Promise<boolean> {
  if (!(await ensureStarted())) return false
  try {
    // Android exposes enabled-state; iOS reports supported only.
    return Platform.OS === 'android' ? await NfcManager.isEnabled() : true
  } catch {
    return false
  }
}

/**
 * Open a foreground NFC session, read the first NDEF URI/Text record, and
 * return its decoded string (e.g. "http://host:3000/verifier"). Throws on
 * unreadable/empty tags or if NFC is unavailable. Always releases the session.
 */
export async function readVerifierTagRaw(): Promise<string> {
  if (!(await ensureStarted())) {
    throw new Error('NFC is not available on this device.')
  }
  try {
    await NfcManager.requestTechnology(NfcTech.Ndef)
    const tag = await NfcManager.getTag()
    const records = tag?.ndefMessage ?? []
    for (const rec of records) {
      const payload = (rec.payload ?? []) as number[]
      // NDEF type "U" (0x55) = URI record; "T" (0x54) = Text record.
      const type = (rec.type ?? []) as number[]
      const typeChar = type.length === 1 ? type[0] : 0
      const decoded =
        typeChar === 0x55
          ? decodeNdefUriPayload(payload)
          : typeChar === 0x54
            ? decodeNdefTextPayload(payload)
            : (decodeNdefUriPayload(payload) ?? decodeNdefTextPayload(payload))
      if (decoded && decoded.trim()) return decoded.trim()
    }
    throw new Error('This tag has no verifier link on it.')
  } finally {
    try {
      await NfcManager.cancelTechnologyRequest()
    } catch {
      /* session already closed */
    }
  }
}
```

- [ ] **Step 2: Typecheck**

```bash
cd reference-wallet/expo && npm run typecheck
```

Expected: no errors. (`react-native-nfc-manager` ships its own types; `tag.ndefMessage`/`payload`/`type` are typed as number arrays.)

- [ ] **Step 3: Commit**

```bash
git add reference-wallet/expo/lib/nfcVerifierTag.ts
git commit -m "feat(expo-wallet): add NFC verifier-tag reader wrapper"
```

---

## Task 5: NFC scan button component

Mirrors `VerifierQrScannerButton` but drives NFC. Renders nothing when NFC is unavailable (web, or no/disabled NFC), so the QR flow remains the sole path on those devices.

**Files:**
- Create: `reference-wallet/expo/components/NfcVerifierScanButton.tsx`

- [ ] **Step 1: Implement the component**

`reference-wallet/expo/components/NfcVerifierScanButton.tsx`:

```tsx
import { useEffect, useState } from 'react'
import { ActivityIndicator, StyleSheet, Text, TouchableOpacity } from 'react-native'
import { isNfcAvailable, readVerifierTagRaw } from '@/lib/nfcVerifierTag'
import {
  parsePresentationRequestQr,
  type PresentationRequestQrPayload,
} from '@/lib/presentationRequestQr'
import { theme } from '@/lib/credentialDisplay'

interface Props {
  onScan: (request: PresentationRequestQrPayload) => void
  onError: (message: string) => void
  disabled?: boolean
}

export function NfcVerifierScanButton({ onScan, onError, disabled }: Props) {
  const [available, setAvailable] = useState(false)
  const [reading, setReading] = useState(false)

  useEffect(() => {
    let alive = true
    isNfcAvailable().then((ok) => {
      if (alive) setAvailable(ok)
    })
    return () => {
      alive = false
    }
  }, [])

  if (!available) return null

  const onPress = async () => {
    setReading(true)
    try {
      const raw = await readVerifierTagRaw()
      const request = parsePresentationRequestQr(raw)
      if (!request) {
        onError('That NFC tag is not a TrustWeave verifier link.')
        return
      }
      onScan(request)
    } catch (e) {
      onError(e instanceof Error ? e.message : 'Could not read the NFC tag.')
    } finally {
      setReading(false)
    }
  }

  return (
    <TouchableOpacity
      style={[s.btn, (reading || disabled) && s.disabled]}
      onPress={onPress}
      disabled={reading || disabled}
    >
      {reading ? (
        <ActivityIndicator color={theme.primary} />
      ) : (
        <Text style={s.btnText}>📲 Tap an NFC verifier tag</Text>
      )}
    </TouchableOpacity>
  )
}

const s = StyleSheet.create({
  btn: {
    borderWidth: 2,
    borderColor: theme.primary,
    paddingVertical: 12,
    borderRadius: 999,
    alignItems: 'center',
    marginTop: 8,
  },
  disabled: { opacity: 0.6 },
  btnText: { color: theme.primary, fontWeight: '600' },
})
```

- [ ] **Step 2: Typecheck**

```bash
cd reference-wallet/expo && npm run typecheck
```

Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add reference-wallet/expo/components/NfcVerifierScanButton.tsx
git commit -m "feat(expo-wallet): add NFC verifier scan button"
```

---

## Task 6: Wire the NFC button into the present screen

Add the NFC button to the `scan` phase, reusing the existing `onVerifierScanned` handler and a small error setter. No other present-flow code changes.

**Files:**
- Modify: `reference-wallet/expo/app/present.tsx`

- [ ] **Step 1: Import the component**

In `reference-wallet/expo/app/present.tsx`, add after the existing `VerifierQrScanner` import (L5):

```tsx
import { NfcVerifierScanButton } from '@/components/NfcVerifierScanButton'
```

- [ ] **Step 2: Render the NFC button in the scan panel**

In the `scan` phase block, inside `<View style={s.scanPanel}>`, immediately after the existing `<VerifierQrScannerButton .../>` (currently L229-232), add:

```tsx
            <NfcVerifierScanButton
              onScan={onVerifierScanned}
              onError={(message) => setStatus({ kind: 'error', message })}
              disabled={status.kind === 'loadingRequest'}
            />
```

`onVerifierScanned` already accepts a `PresentationRequestQrPayload` and runs the full exchange — the NFC button produces exactly that object, so nothing downstream changes.

- [ ] **Step 3: Typecheck**

```bash
cd reference-wallet/expo && npm run typecheck
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add reference-wallet/expo/app/present.tsx
git commit -m "feat(expo-wallet): offer NFC tap-to-present in the share flow"
```

---

## Task 7: Demo doc — programming the tag + device matrix

**Files:**
- Create: `reference-wallet/expo/NFC-DEMO.md`

- [ ] **Step 1: Write the demo guide**

`reference-wallet/expo/NFC-DEMO.md`:

````markdown
# NFC Tap-to-Present Demo

NFC carries only the verifier link; the credential exchange runs over HTTP to the
demo backend (same as the QR flow). The phone is the NFC **reader**; the verifier
is a passive NTAG sticker.

## 1. Program the tag

Use any NFC writer app (e.g. "NFC Tools") to write **one NDEF URI record**:

```
http://<your-LAN-ip>:3000/verifier
```

Use the same host/port as `extra.demoBackendBaseUrl` in `app.json`. This is the
exact string the verifier QR encodes, so the wallet treats a tap identically to a
scan — including fetching a fresh nonce from the backend per presentation.

NTAG213/215/216 stickers all work. A URI record is preferred; a Text record
containing the same URL also works (the reader falls back to Text).

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
````

- [ ] **Step 2: Commit**

```bash
git add reference-wallet/expo/NFC-DEMO.md
git commit -m "docs(expo-wallet): NFC tap-to-present demo guide"
```

---

## Task 8: Full manual device verification

No automated E2E exists for the wallet; verify on hardware.

**Files:** none (manual)

- [ ] **Step 1: Build + install the dev client on an NFC Android phone** (see NFC-DEMO.md step 2).

- [ ] **Step 2: Happy path** — backend running, tag written with `http://<ip>:3000/verifier`:
  tap NFC button → tap sticker → request loads → select credential → Share → result panel shows "✓ Credential verified". Expected: identical outcome to scanning the verifier QR.

- [ ] **Step 3: Parity check** — run the same presentation via the QR button and confirm the result panel is identical. Confirms NFC only changed the trigger.

- [ ] **Step 4: Negative — wrong tag:** write a tag with `http://example.com/` (no `/verifier`). Tap. Expected: error "That NFC tag is not a TrustWeave verifier link."

- [ ] **Step 5: Negative — blank tag:** tap an unwritten tag. Expected: error "This tag has no verifier link on it."

- [ ] **Step 6: Negative — NFC off / web:** disable NFC (Android) or open the web build. Expected: the NFC button is absent; QR flow still works.

- [ ] **Step 7: Record results** in the PR description (device model, OS version, pass/fail per step).

---

## Self-review notes

- **Spec coverage:** engagement-over-NFC + exchange-over-HTTP (Tasks 4-6); phone-reads-tag (Task 4); Expo holder (all); NTAG NDEF URI (Tasks 2,7); fresh nonce via backend (reusing `parsePresentationRequestQr` URL form → existing `fetchPresentationRequestFromQr`); platform gating for web (Tasks 4-5); Android-first with iOS documented stretch (Tasks 3,7). All spec sections map to a task.
- **No placeholders:** every code/command step is complete.
- **Type consistency:** `PresentationRequestQrPayload` (existing), `readVerifierTagRaw(): Promise<string>`, `isNfcAvailable(): Promise<boolean>`, `decodeNdefUriPayload`/`decodeNdefTextPayload`/`bytesOf` are used with consistent signatures across Tasks 2,4,5.
- **Open item carried from spec:** the plan assumes the `/verifier` URL form already triggers a backend nonce fetch (confirmed in `present.tsx` `onVerifierScanned`, which calls `fetchPresentationRequestFromQr` when `qr.presentationRequest` is absent). No backend change required for the demo.
