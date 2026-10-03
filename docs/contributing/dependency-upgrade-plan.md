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

Status as of October 2026. Versions are the catalog versions on `main`; "latest" is the newest
release on Maven Central when this was written. Update this page in the same PR that completes an item.

| Item | Current | Target | Scope | Risk |
| ---- | ------- | ------ | ----- | ---- |
| [Unmaintained Vault driver](#vault-java-driver) | `com.bettercloud:vault-java-driver` 5.1.0 | Maintained client or plain HTTP | `kms:plugins:hashicorp` | Medium |
| [DIDComm library with embedded Nimbus](#didcommx) | `org.didcommx:didcomm` 0.3.2 | Maintained implementation | `credentials:plugins:didcomm` | High (security) |
| [Ktor 3](#ktor-3) | 2.3.13 | 3.x (latest 3.6.0) | ~14 build files (servers, clients) | High |
| [OkHttp 5](#okhttp-5) | 4.12.0 | 5.x (latest 5.5.0) | 36 build files | Medium |
| [Kotest 6](#kotest-6) | 5.9.1 | 6.x (latest 6.2.5) | 2 build files, 6 test sources | Low |
| [Testcontainers 2](#testcontainers-2) | 1.21.4 | 2.x (latest 2.0.5) | 10 build files | Medium |
| [kotlinx-datetime 0.7+](#kotlinx-datetime) | 0.6.2 | 0.7.x / 0.8.x | 58 build files, ~230 sources; **public API** | High |
| [bitcoinj 0.17](#bitcoinj) | 0.16.2 (`bitcoinj-legacy`) | 0.17.x | 2 build files | Medium |

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

## didcommx

`org.didcommx:didcomm` 0.3.2 (August 2022, still the latest release) embeds Nimbus JOSE+JWT
9.16-preview.1 and json-smart 2.4.7, which carry denial-of-service advisories. See
[SECURITY.md, Known Dependency Risks](https://github.com/geoknoesis/trustweave/blob/main/SECURITY.md#known-dependency-risks)
for the advisories and the mitigations required of users in the meantime.

Steps:

1. Check for a didcommx release that depends on (rather than shades) a current Nimbus.
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
