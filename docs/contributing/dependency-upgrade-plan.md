---
title: Dependency Upgrade Plan
parent: Contributing to TrustWeave
nav_order: 90
---

# Dependency Upgrade Plan

Major-version upgrades and unmaintained libraries that are deliberately **not** done as routine
Dependabot bumps. Each needs its own PR because it changes APIs that TrustWeave code, tests or
consumers compile against. Minor and patch updates within the current major still go through
Dependabot and `gradle/libs.versions.toml` as usual.

Status as of 3 October 2026. Versions are the catalog versions on `main`; "latest" is the newest
release on Maven Central when this was written. Update this page in the same PR that completes an item.

| Item | Current | Target | Scope | Risk | Owner |
| ---- | ------- | ------ | ----- | ---- | ----- |
| [Unmaintained Vault driver](#vault-java-driver) | `com.bettercloud:vault-java-driver` 5.1.0 | Maintained client or plain HTTP | `kms:plugins:hashicorp` | Medium | TBD (assign) |
| [DIDComm library with embedded Nimbus](#didcommx) | `org.didcommx:didcomm` 0.3.2 | Maintained implementation | `credentials:plugins:didcomm` | High (security) | TBD (assign) |
| [Ktor 3](#ktor-3) | 2.3.13 | 3.x (latest 3.6.0) | ~14 build files (servers, clients) | High | TBD (assign) |
| [OkHttp 5](#okhttp-5) | 4.12.0 | 5.x (latest 5.5.0) | 36 build files | Medium | TBD (assign) |
| [Kotest 6](#kotest-6) | 5.9.1 | 6.x (latest 6.2.5) | 2 build files, 6 test sources | Low | TBD (assign) |
| [Testcontainers 2](#testcontainers-2) | 1.21.4 | 2.x (latest 2.0.5) | 10 build files | Medium | TBD (assign) |
| [kotlinx-datetime 0.7+](#kotlinx-datetime) | 0.6.2 | 0.7.x / 0.8.x | 58 build files, ~230 sources; **public API** | High | TBD (assign) |
| [bitcoinj 0.17](#bitcoinj) | 0.16.2 (`bitcoinj-legacy`) | 0.17.x | 2 build files | Medium | TBD (assign) |
| Deferred: [Expo SDK 54 pins](#reference-wallet-deferrals) | react 19.1.0, react-native 0.81.5, async-storage 2.2.0, safe-area-context ~5.6.0, jest 29 | react 19.3 / RN 0.87 / async-storage 3.1 / safe-area 5.10 / jest 30 | `reference-wallet/expo` | High | TBD (assign) |
| Deferred: [Android toolchain](#reference-wallet-deferrals) | AGP 8.5.0, Kotlin 2.0.0, Gradle 8.9, compileSdk 34, Compose BOM 2024.06.00, OkHttp 4.12.0 | Kotlin 2.4.20, Gradle 9.8, BOM 2026.09.00, OkHttp 5.5.0 | `reference-wallet/android` | High | TBD (assign) |

## Routine bumps done in the current cycle

Within-major updates that were safe to take without a migration, verified by compiling and testing
the modules that use them: Jackson 2.22.3, Bouncy Castle 1.86 (both clear advisories that OSV
reported against the previous versions), SLF4J 2.0.20, MongoDB BSON 4.11.5, Azure Identity 1.18.7.
Still open and routine (Dependabot can propose them): Hikari 7.1.0, web3j 5.0.3, OpenTelemetry
1.66.0, json-path 2.10.0, Kover 0.9.11, AWS SDK BOM 2.55.x, Google libraries-bom 26.90.0, Azure SDK
BOM 1.3.8. Take them in separate small PRs and run the affected modules' tests.

The remaining OSV advisories are tracked in `config/osv/baseline.json` (see SECURITY.md). Most come
from transitive Netty 4.1.x, Jackson 2.17/2.18 and Bouncy Castle 1.69-1.80 copies pulled in by cloud
SDKs and web3j; upgrading those SDK BOMs is the way to shrink the baseline. After each upgrade,
regenerate the report and remove the entries that no longer appear.

## vault-java-driver

`com.bettercloud:vault-java-driver` has had no release since 2019 and its repository is archived.
It is only used by `kms:plugins:hashicorp` (4 source files) to call Vault's Transit engine.

Risk: unpatched TLS/HTTP handling in an unmaintained client that carries key-management traffic.

Steps:

1. Inventory the Vault calls the plugin makes (Transit `keys`, `sign`, `verify`, auth login).
2. Choose a replacement: the community fork `io.github.jopenlibs:vault-java-driver` (drop-in,
   package rename `com.bettercloud.vault` → `io.github.jopenlibs.vault`) is the smallest change; a
   thin client over the HTTP API with OkHttp/Ktor removes the dependency entirely.
3. Swap the catalog alias, update imports, and run the plugin's tests plus the Vault
   Testcontainers suite.
4. Note the change in `CHANGELOG.md`; the plugin's public API does not change.

Recommended approach (documentation only, no code change yet): replace the driver with a thin
internal client over Vault's HTTP API using the OkHttp the project already depends on. The plugin
needs only `POST /v1/auth/<method>/login` (static token needs no call; AppRole login is the only auth call the plugin makes today), `POST/GET
/v1/transit/keys/<name>`, `POST /v1/transit/sign/<name>` and `POST /v1/transit/verify/<name>`, with
the `X-Vault-Token` header. That is a few hundred lines, removes the
unmaintained dependency and its TLS code, and lets the plugin reuse the repository's SSRF and timeout
conventions. The jopenlibs fork is the fallback if a drop-in is needed in a hurry. Keep the existing
parsing of Vault public keys (strict, curve-checked) and the Testcontainers Vault suite as the
regression gate.

Next step: open an issue for the plugin owner (TBD), write the HTTP client behind the existing
plugin interface, run the Vault suite against the old and new clients, then remove the catalog alias.

## didcommx

`org.didcommx:didcomm` 0.3.2 (August 2022, still the latest release) embeds Nimbus JOSE+JWT
9.16-preview.1 and json-smart 2.4.7, which carry denial-of-service advisories. See
[SECURITY.md, Known Dependency Risks](https://github.com/geoknoesis/trustweave/blob/main/SECURITY.md#known-dependency-risks)
for the advisories and the mitigations required of users in the meantime.

Steps:

0. Checked on 3 October 2026: Maven Central has no didcomm release after 0.3.2, so there is nothing
   to bump. The OSV gate (`config/osv/baseline.json`) now fails on any new advisory against the
   embedded copies.
1. Check for a didcommx release that depends on (rather than shades) a current Nimbus (recheck quarterly).
2. Otherwise evaluate a maintained DIDComm v2 implementation, or implement packing/unpacking on
   the catalog's Nimbus (9.48+) behind the existing `crypto/interop` adapter boundary.
3. Remove the `nimbus-jose-jwt` exclusion from `credentials/plugins/didcomm/build.gradle.kts` once
   no shaded copy remains, and drop the SECURITY.md entry when the OSV-Scanner job
   (`.github/workflows/security.yml`) no longer reports the embedded copies.

## Ktor 3

Used by the registrar, VC API, OIDC4VCI, status-list and trust-registry servers (`libs.bundles.ktor-server`)
and by HTTP clients (`libs.bundles.ktor-client`).

Risk: Ktor 3 moves to kotlinx-io, changes `ApplicationEngine`/`EmbeddedServer` startup, removes
deprecated APIs and changes some plugin configuration DSLs; server tests (`ktor-server-test-host`)
need `testApplication` updates. Servers are published artifacts, so behaviour must be re-qualified
(the host observability and conformance workflows).

Steps:

1. Bump `ktor` in the catalog on a branch and compile everything that uses it
   (`grep -rl "libs.ktor\|libs.bundles.ktor" --include=build.gradle.kts`).
2. Fix server bootstrap (`embeddedServer(...).start(wait = ...)`), `call.receive`/`respond` channel
   APIs and any `ByteReadChannel` code for kotlinx-io.
3. Run each server's tests and `./gradlew checkKotlinAbi`; update ABI dumps where server APIs
   legitimately change and record it in `CHANGELOG.md`.
4. Re-run conformance (`conformance-pr.yml`) and host observability checks from `ci.yml`.

## OkHttp 5

Used in 36 modules, mostly as an HTTP client for DID methods, anchors and KMS providers;
`mockwebserver` is used in tests.

Risk: OkHttp 5 is source-compatible for most client code, but `mockwebserver` moves to
`mockwebserver3` with a new API, and the artifact is published as Kotlin Multiplatform (`okhttp-jvm`
resolution through Gradle metadata). Interceptors and TLS configuration must be re-checked.

Steps:

1. Bump `okhttp`; switch test code from `okhttp3.mockwebserver` to `mockwebserver3`
   (`com.squareup.okhttp3:mockwebserver3`) and update its catalog alias.
2. Compile and run tests for all 36 modules (in batches; see CLAUDE.md for targeted Gradle tasks).
3. Check the CycloneDX SBOM for a single OkHttp/Okio version afterwards.

## Kotest 6

Only assertions/runner in 2 build files and 6 test sources.

Risk: low; Kotest 6 needs Kotlin 2.2+ (satisfied) and renames some matchers/packages.

Steps: bump `kotest`, fix imports in the 6 test files, run those modules' tests.

## Testcontainers 2

Used by 10 modules' tests (PostgreSQL, Vault, LocalStack-style integration tests).

Risk: Testcontainers 2 drops JUnit 4, renames the module artifacts to `testcontainers-<module>`
(for example `org.testcontainers:testcontainers-postgresql`) and moves container classes to
per-module packages. CI relies on these suites for qualification evidence.

Steps:

1. Update the catalog coordinates (`testcontainers`, `testcontainers-junit`,
   `testcontainers-postgresql`) to the 2.x artifact names.
2. Update imports and any `@Container`/`@Testcontainers` usage; remove JUnit 4 rule usage.
3. Run the Docker-backed suites in CI (they cannot run where Docker is unavailable).

## kotlinx-datetime

`kotlinx.datetime.Instant`/`Clock` appear in about 230 source files across 58 modules,
including **public API** (credential and DID document models).

Risk: high. kotlinx-datetime 0.7 removes `kotlinx.datetime.Instant` and `Clock` in favour of
Kotlin's `kotlin.time.Instant`/`kotlin.time.Clock` (stable in Kotlin 2.3). Every public signature
that exposes `kotlinx.datetime.Instant` changes, which is a binary-incompatible change for
consumers and for serialized formats that rely on its serializer.

Steps:

1. Decide the public API type (`kotlin.time.Instant` is the forward path) and announce the break
   in `CHANGELOG.md` for the next minor release.
2. Optionally stage it: move to the `0.7.x-0.6.x-compat` / `0.8.0-0.6.x-compat` artifact first,
   which keeps the old classes while the code migrates.
3. Migrate module by module, regenerate ABI dumps (`./gradlew updateKotlinAbi`) and review the
   diffs, and verify JSON serialization of timestamps is unchanged (ISO-8601) with the existing
   round-trip tests.

## bitcoinj

Already recorded in `gradle/libs.versions.toml`: two consumers still build against 0.16.2 through
the `bitcoinj-legacy` alias because 0.17 moves `Transaction`, `NetworkParameters`, `HEX` and
`isOpReturn`. Migrate those two modules, then delete the legacy alias.

## Reference wallet deferrals

Dependabot PRs #31 to #50 for `reference-wallet/` were resolved in one pass. Applied: the web npm
group, `jose` 6, `@noble/curves` 2, TypeScript 7, React 19.3, Android `security-crypto` 1.1.0 and
`bcprov` 1.86. Deferred, each as its own PR:

- **Expo `react`/`react-dom` 19.3 and `react-native` 0.87 (#34).** Expo SDK 54 is built for React 19.1
  and React Native 0.81; the renderer rejects a different React version and the Expo native
  modules are not built for RN 0.87. Next step: upgrade the Expo SDK (`npx expo install --fix`) as one change.
- **Expo `async-storage` 3.1.1 (#38) and `react-native-safe-area-context` 5.10.1 (#40).** `expo install
  --check` pins 2.2.0 and ~5.6.0 for SDK 54; async-storage 3 is a major with a new native module.
  Next step: take them with the Expo SDK upgrade, then adapt `lib/storage.ts`.
- **Expo `jest` 30 and `@types/jest` 30 (#37, #39).** `jest-expo` 54 is built for Jest 29 and
  `jest-watch-typeahead` requires `^29`; a clean install nests a second Jest 29 and the suites fail to
  load Babel presets. Next step: retry with the `jest-expo` release that supports Jest 30.
- **Android Kotlin 2.4.20, `core-ktx` 1.19, `navigation-compose` 2.10, kotlinx-serialization 1.11,
  coroutines 1.11, datetime 0.8 (#43).** They need a newer Android Gradle Plugin and `compileSdk` than
  the 8.5.0 and 34 in use, and `kotlinOptions { }` is removed in current Kotlin. Next step: upgrade AGP,
  `compileSdk` and Kotlin together on a machine with the Android SDK, then the libraries.
- **Compose BOM 2026.09.00 (#44), Gradle wrapper 9.8.0 (#45), OkHttp 5.5.0 (#48).** Not verifiable
  without an Android build; each changes the build or the HTTP client API. Next step: one PR each so
  the CI job "Android app tests and shared compilation" validates them; the wrapper must match the AGP.
