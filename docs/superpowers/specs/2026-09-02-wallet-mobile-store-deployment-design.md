# Reference Wallet on iPhone and Android — Deployment Design

**Date:** 2026-09-02
**Status:** Draft for review
**Owner:** Stephane Fellah

## Goal

Put the TrustWeave reference wallet on real iPhones and real Android phones, installable by
a named tester from a link, working **anywhere** — not only on the LAN next to the demo
backend. A prospect on a call should be able to install the wallet, receive a degree
credential, present it with selective disclosure, and see the verifier's verdict, without
anyone being on the same Wi-Fi.

Distribution stops at **internal testing**: TestFlight internal (up to 100 testers who are
members of the App Store Connect team, no Apple review) and the Google Play **internal
testing** track (up to 100 testers by email address, live within minutes of upload). No
public listing.

## Scope decisions (locked during brainstorming)

| Decision | Choice |
|---|---|
| How far down the funnel | Internal testing only — TestFlight internal + Play internal testing |
| Which codebase ships | **Expo only** (`reference-wallet/expo`), both platforms, one bundle id |
| Native Android / iOS wallets | Stay as fork-able reference source. Never shipped to a store |
| Backend | Hosted publicly over HTTPS, **plus** an in-app URL override for LAN development |
| Backend hosting shape | Node **container** (Fly.io) with a persistent volume |
| Build driver | `eas build` / `eas submit` run by hand from Windows. No CI in phase 1 |

## Why Expo and not the native wallets

The [native iOS wallet](../../../reference-wallet/ios/) has **no Xcode project** — only loose
Swift sources, written without macOS access and never compiled. Shipping it means authoring
an `.xcodeproj` blind from Windows and then debugging Swift that cannot be built locally.

The [native Android wallet](../../../reference-wallet/android/) does build, but sits on
`compileSdk`/`targetSdk` 34 with no release signing config and no launcher icon, and it
solves a problem this goal does not have — its Keystore-bound Ed25519 holder key is the
tier-1 story for regulated and eIDAS conversations, not for "let me show you the demo."

The Expo wallet already runs on both platforms from one TypeScript codebase, already has a
stub `eas.json`, and builds iOS on EAS's cloud Macs — the only iOS build path available from
a Windows machine. Its holder key sits at tier 2 (an `expo-secure-store` seed loaded into JS
to sign), which is the accepted trade and is already documented as such in the wallet
READMEs.

## Non-goals

- Public App Store / Play listings, App Review, screenshots, feature graphics.
- Shipping the native Android or native iOS wallets as store artifacts.
- Hardware-bound (tier 1) holder keys in the Expo wallet.
- OID4VCI / OID4VP wire compliance. The demo's simplified direct-HTTP shape is unchanged.
- Turning the reference wallet into a product. It remains a demo and a fork-able starter.

---

## Architecture

```
  Tester's iPhone                      Tester's Android
  +------------------+                 +------------------+
  | TrustWeave Wallet|                 | TrustWeave Wallet|
  |   (TestFlight)   |                 |  (Play internal) |
  +--------+---------+                 +---------+--------+
           |  HTTPS                              |  HTTPS
           +---------------+---------------------+
                           v
             https://trustweave-wallet-demo.fly.dev         Fly.io, 1 machine
             +------------------------------------------+
             | Next.js demo backend (reference-wallet/) |
             |   /api/demo-issuer/*  /api/demo-verifier/*|
             |   WORKDIR /app                            |
             |     data/trust-domains/    (in image)     |
             |     public/subjects,drones/ (in image)    |
             |   /data  <- 1 GB volume                   |
             |     .demo-server-keys.json (stable DIDs)  |
             +------------------------------------------+
```

Both store builds point at the Fly host by default. A Settings screen in the wallet can
retarget a build at a LAN address for development.

---

## Component 1 — Containerized demo backend

**What it does.** Serves the existing Next.js app at `reference-wallet/` unchanged: the web
wallet UI, the demo issuer, the demo verifier, and the four trust domains.

**Why a container rather than serverless.** Two properties of the current code make
serverless hosting a rewrite rather than a deploy:

1. [`lib/server-keys.ts`](../../../reference-wallet/lib/server-keys.ts) **writes** the issuer
   and verifier Ed25519 private keys to `.demo-server-keys.json`. On a read-only,
   per-instance serverless filesystem this either throws or silently mints fresh DIDs per
   cold start — so a credential issued by one instance fails verification on the next, and
   the failure presents as a cryptography bug rather than a hosting bug.
2. The four trust-domain loaders read CSVs, `domain.json`, and portrait or photo JPEGs via
   `readFileSync(path.join(process.cwd(), 'data/...'))` and the equivalent under `public/`.
   Next.js output file tracing routinely misses runtime-computed paths like these, producing
   `ENOENT` in production only.

In a container with `WORKDIR /app` and the application copied in, both behave exactly as they
do on a developer machine. Problem 2 disappears with no code change at all.

**The one backend code change.** A volume cannot mount over `/app` without shadowing the
application, so the key file path must be relocatable:

```ts
// lib/server-keys.ts
const KEYS_FILE =
  process.env.DEMO_KEYS_FILE ?? path.join(process.cwd(), '.demo-server-keys.json')
```

Default behaviour is unchanged; the container sets `DEMO_KEYS_FILE=/data/.demo-server-keys.json`
so issuer and verifier DIDs survive redeploys.

**Dockerfile constraint.** Do **not** enable Next.js `output: 'standalone'`. Standalone
output relocates the server into `.next/standalone`, which changes `process.cwd()` and breaks
every `data/` and `public/` read described above. Use a plain full copy plus `next start`.
Node 20, matching the `engines` field's `>=20`.

**Fly configuration.** App name `trustweave-wallet-demo`, to be claimed in Phase 1 (if taken, the
chosen name propagates to the `EXPO_PUBLIC_DEMO_BACKEND_URL` values in `eas.json`).
One machine, because a volume binds to a single machine. A 1 GB volume
mounted at `/data`. `DEMO_KEYS_FILE` set in the `[env]` block. Fly provisions TLS on
`*.fly.dev` automatically — that HTTPS endpoint is what lets the app drop its cleartext
exceptions.

**Interface to the wallet:** unchanged. The same `/api/demo-issuer/*` and
`/api/demo-verifier/*` routes the four reference wallets already call. Only the origin changes.

---

## Component 2 — Expo app configuration

**`app.json` becomes `app.config.ts`.** The app needs a different network posture per build
profile, which static JSON cannot express. The TypeScript config keys off `EAS_BUILD_PROFILE`:

| Setting | `development` / `preview` | `production` |
|---|---|---|
| `ios.infoPlist.NSAppTransportSecurity.NSAllowsArbitraryLoads` | `true` | omitted |
| `android.usesCleartextTraffic` | `true` | `false` |
| Default backend URL | LAN address | `https://trustweave-wallet-demo.fly.dev` |

The default backend URL comes from `EXPO_PUBLIC_DEMO_BACKEND_URL`, set per profile in
`eas.json`, replacing the hardcoded `192.168.1.252:3000` in
[`app.json`](../../../reference-wallet/expo/app.json).

**Consequence to accept:** a `production` build can only be retargeted at an **HTTPS** URL,
because it no longer permits cleartext. LAN development uses a `preview` build. This is the
correct trade — the cleartext exception is exactly what Apple asks about — but it must be
understood before someone points a TestFlight build at `http://192.168.x.x` and finds it fails.

**Drop the `react-native-nfc-manager` config plugin.** NFC is half-landed: the plugin is
registered and the dependency installed, but `nfcVerifierTag.ts` and
`NfcVerifierScanButton.tsx` from the [NFC plan](../plans/2026-06-01-nfc-tap-to-present-demo.md)
were never written, and no source file imports NFC. The plugin nonetheless injects the iOS
`com.apple.developer.nfc.readersession.formats` entitlement, which forces enabling the Near
Field Communication capability on the App ID for a feature with no code behind it. Remove the
plugin entry only — keep the dependency and `lib/ndefUri.ts` so the NFC plan resumes cleanly
by re-adding one array element.

**Version fields.** `eas.json` sets `appVersionSource: "local"`, so `ios.buildNumber` and
`android.versionCode` live in app config and must be incremented before every upload. Both
stores reject a rebuild that reuses a build number.

**App icon and splash.** No icon, splash, or adaptive icon exists anywhere in the repository,
and there is no logo to derive one from. A source mark must be created — an SVG wordmark
rendered to a 1024x1024 PNG is sufficient — producing `assets/icon.png`,
`assets/adaptive-icon.png` (the Android foreground layer), and `assets/splash.png`.

**iOS export compliance.** Every TestFlight upload asks the encryption question, and an
unanswered one blocks the build from reaching testers. The wallet's cryptography is Ed25519
digital signatures, SHA-256 hashing, and HTTPS — all within Apple's exemption for encryption
limited to authentication and digital signature. Declare
`ios.config.usesNonExemptEncryption: false` in app config so the question is answered at build
time rather than per upload. Confirm this classification before the first submission; if a
future build adds data-at-rest encryption beyond `expo-secure-store`, it must be revisited.

---

## Component 3 — Runtime backend URL override

**Why.** A shipped build whose backend address is frozen at build time cannot be pointed at a
developer machine, and every backend move becomes a rebuild-and-resubmit cycle.

**The obstacle.** [`demoBackend.ts:17`](../../../reference-wallet/expo/lib/demoBackend.ts#L17)
resolves `baseUrl` as a module-level `const`, evaluated at import. Nothing set later can reach
it. The surface is small and fully enumerated: five internal uses inside `demoBackend.ts`
(lines 83, 91, 103, 109, 119), one exported `demoBackendBaseUrl` const (line 128), consumed at
four sites in `app/present.tsx` (lines 81, 154, 164, 245).

**Design.** A new `lib/backendUrl.ts` owns resolution:

```ts
resolveBackendUrl(): Promise<string>   // AsyncStorage override ?? build-time default
backendUrl(): string                   // last resolved value, for synchronous render paths
setBackendUrlOverride(url: string | null): Promise<void>
```

`app/_layout.tsx` awaits `resolveBackendUrl()` once on mount and gates the tab navigator on
that promise, so no screen can render before the value is known. `demoBackend.ts` swaps its
`const baseUrl` for `backendUrl()` calls, and its exported const becomes the same accessor.
`AsyncStorage` is already a dependency; nothing new is added.

**Settings screen.** `app/settings.tsx` becomes a fourth tab in the existing `Tabs` layout in
[`_layout.tsx`](../../../reference-wallet/expo/app/_layout.tsx): the effective backend URL, a
text input, Save, and Reset-to-default. It validates that a `production` build is given an
`https://` URL and explains why when it is not.

---

## Component 4 — Store accounts and credentials

These gate everything else and both now run identity verification measured in days. Start
them first; the app work proceeds in parallel.

**Apple Developer Program** — 99 USD per year. An **Individual** account is typically same-day
and is sufficient for internal TestFlight. An **Organization** account requires a D-U-N-S
number and can take one to two weeks; choose it only if the wallet must be published under the
company name later. TestFlight internal testers must be members of the App Store Connect team,
which is what exempts them from Beta App Review.

**Google Play Console** — 25 USD once, plus identity verification. Expect to complete the
**App content** declarations (privacy policy URL, Data safety form, content rating
questionnaire, target audience, ads declaration) before rolling out to any track, internal
testing included. Verify the current requirement set in the console during Phase 0 — if a
store listing is also demanded, the fallback is a 512x512 icon, a 1024x500 feature graphic,
and two phone screenshots.

**Privacy policy.** A URL is required by Play's Data safety form. The repository already
publishes `docs/` to GitHub Pages via [`deploy.yml`](../../../.github/workflows/deploy.yml),
so a short policy page added there is served at a stable URL with no new infrastructure.

**Bundle identifier** — `org.trustweave.referencewallet.expo`, already set for both platforms,
registered in both consoles. The Play package name is **permanent** once uploaded; confirm it
before the first upload.

**Signing credentials.** EAS generates and stores the Android upload keystore and the iOS
distribution certificate and provisioning profile. Nothing is generated by hand and no Mac is
involved.

---

## Component 5 — Build and submit

**`eas.json`** replaces the current stub with three build profiles and a submit profile:

| Profile | Distribution | Android artifact | Backend URL | Purpose |
|---|---|---|---|---|
| `development` | internal, dev client | apk | LAN | Hot reload on a real device |
| `preview` | internal | apk | Fly | Sideloadable build; cleartext still permitted, so the Settings override can reach a LAN host |
| `production` | store | app-bundle | Fly | What reaches TestFlight and Play |

Per platform: `eas build --profile production`, then `eas submit --profile production`.
`eas submit` can create the App Store Connect app record on first run. Android submits
straight to `track: internal`.

**Tester onboarding.** iOS: add each tester as an App Store Connect user, then to the internal
TestFlight group. Android: add tester email addresses to the internal testing track and share
the opt-in link.

---

## Error handling

| Failure | Where it surfaces | Handling |
|---|---|---|
| Backend unreachable | Any `fetch` in `demoBackend.ts` | The Settings screen shows the effective URL and a reachability check, so "is it my network or the build?" is answerable on the spot |
| Cleartext URL entered in a production build | Settings screen | Rejected at input with an explanation, rather than failing later as an opaque network error |
| Issuer DID rotated | Presentation verification fails | Prevented by the `/data` volume; if the volume is ever lost, the tester re-receives the credential |
| New-architecture incompatibility | First EAS build | See Risks |
| Build number reuse | `eas submit` rejection | Increment in app config; the plan makes this an explicit step in the release sequence |

## Testing

The Expo wallet has a `jest-expo` runner already, with tests under `lib/__tests__/`. Two new
pure units are worth covering, and both are testable without a device:

- `lib/backendUrl.ts` — override precedence, reset-to-default, and rejection of a cleartext URL
  under a production build.
- `app.config.ts` — asserts that the `production` profile omits `NSAllowsArbitraryLoads` and
  sets `usesCleartextTraffic: false`, and that non-production profiles keep both. This is the
  regression that would otherwise be caught only by an App Store reviewer.

The container gets a smoke check rather than a test suite: build the image, run it, and confirm
`/api/demo-issuer/trust-domain` returns the same issuer DID across a container restart — the
direct test of the `DEMO_KEYS_FILE` volume behaviour.

## Definition of done

A fresh iPhone and a fresh Android phone, each installing from the TestFlight and Play internal
links, **on cellular with the development laptop powered off**, both completing the
demo-university flow end to end: receive a degree credential, present it with selective
disclosure, and receive the verifier's checklist showing issuer signature, disclosure hashes,
KB-JWT signature, audience binding, nonce binding, `sd_hash` binding, and temporal validity.

## Risks

**New architecture on first build.** `newArchEnabled: true` on React Native 0.81 has never been
exercised through a release build of this dependency set — Expo Go and dev-server runs do not
prove it. The first `eas build` is where any incompatibility appears. Budget a cycle for it,
and note that removing the unused NFC plugin also removes one native module from the surface.

**No OTA updates.** Without `expo-updates`, every JavaScript fix is a full rebuild and
resubmit — roughly 15 to 30 minutes per platform, plus Play's internal-track propagation. This
is the strongest phase 6 candidate: it collapses that loop to seconds and is a small,
self-contained addition once the pipeline exists. Deliberately excluded from phase 1 so that
"can we install it at all" is answered before adding another moving part.

**Account verification latency.** Apple Organization enrolment and Play identity verification
are outside our control and can take one to two weeks. This is why account setup is phase 0 and
runs in parallel with everything else.

## Phasing

| Phase | Work | Blocks |
|---|---|---|
| 0 | Apple + Play accounts, identity verification, privacy policy page | Everything downstream; start first, runs in parallel |
| 1 | `DEMO_KEYS_FILE`, Dockerfile, Fly deploy, HTTPS smoke check | Phase 2's default URL |
| 2 | `app.config.ts`, assets, NFC plugin removal, `backendUrl.ts`, Settings tab, unit tests | Phase 3 |
| 3 | `eas.json` profiles, EAS credentials, first builds on both platforms | Phase 4 |
| 4 | `eas submit` to TestFlight and Play internal, tester onboarding | Phase 5 |
| 5 | Definition-of-done run on two physical phones over cellular | — |
| 6 (deferred) | `expo-updates` OTA | — |
