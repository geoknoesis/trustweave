---
layout: default
title: Migrating to TrustWeave 0.7.0
nav_order: 10
parent: Migration Guides
grand_parent: How-To Guides
---

# Migrating to TrustWeave 0.7.0

This guide covers upgrading an application from TrustWeave `0.6.x` to `0.7.0`.

## 1. Toolchain & Core Dependencies

Upgrade your build environment to match the `0.7.0` toolchain:

| Component | 0.6.x | 0.7.0 |
|-----------|-------|-------|
| Java | 21 | **21+** |
| Kotlin | 2.2.x / 2.3.x | **2.4.21** |
| Gradle | 8.5+ / 9.2 / 9.5 | **9.8.1** |
| `kotlinx-coroutines` | 1.10.x | **1.11.0** |
| `kotlinx-serialization` | 1.10.x | **1.11.0** |
| Ktor | 3.4.x | **3.6.0** |
| Nimbus JOSE+JWT | 9.48 | **10.10** |

## 2. BOM, `distribution-all`, and Unpublished Stubs

### Use the TrustWeave BOM

Import `org.trustweave:distribution-bom:0.7.0` to align versions across all published TrustWeave modules:

```kotlin
dependencies {
    implementation(platform("org.trustweave:distribution-bom:0.7.0"))
    implementation("org.trustweave:distribution-all")
    testImplementation("org.trustweave:testkit:0.7.0")
}
```

### `distribution-all` Includes `kms-plugins-inmemory`

`org.trustweave:distribution-all:0.7.0` now bundles `kms-plugins-inmemory` alongside `did-plugins-key` and the core modules so `TrustWeave.quickStart()` works out of the box without pulling `:testkit` onto your main classpath. For production, prefer individual modules plus a cloud/HSM KMS plugin (`kms-plugins-aws`, `kms-plugins-azure`, `kms-plugins-google`, `kms-plugins-hashicorp`, etc.).

### 12 Pure Stubs Removed from BOM & Maven Central

The following 12 unimplemented stub modules are excluded from `distribution-bom` and Maven Central publishing in `0.7.0`:

- `anchors:plugins:starknet`
- `credentials:plugins:platforms:salesforce`, `credentials:plugins:platforms:servicenow`
- `did:plugins:btcr`, `did:plugins:ens`, `did:plugins:tezos`, `did:plugins:threebox`
- `kms:plugins:entrust`, `kms:plugins:thales-luna`, `kms:plugins:utimaco`, `kms:plugins:venafi`
- `signatures:pades`

If your build declared a dependency on any of these modules, remove it — every public operation threw `UnsupportedOperationException` / `InvalidOperation`.

### Snapshot Repository URL

Legacy Sonatype OSSRH snapshot repositories are retired. If you consume `-SNAPSHOT` builds, switch your snapshot repository URL to Central Portal snapshots:

```kotlin
maven {
    url = uri("https://central.sonatype.com/repository/maven-snapshots/")
}
```

## 3. Sealed Result APIs (Replacing Deprecated Facade Methods)

Several `TrustWeave` facade methods that previously returned `kotlin.Result`, `Boolean`, or nullable failure objects are deprecated in `0.7.0` in favor of sealed result types:

| Deprecated in 0.7.0 | Replacement | Return Type |
|---------------------|-------------|-------------|
| `trustWeave.revoke { ... }` | `trustWeave.revokeResult { ... }` | `RevocationResult` |
| `trustWeave.trust { ... }` | `trustWeave.trustResult { ... }` | `TrustOperationResult` |
| `trustWeave.getKeyId(did)` | `trustWeave.getKeyIdResult(did)` | `KeyIdResult` |

Example migration for credential revocation:

```kotlin
import org.trustweave.trust.types.RevocationResult

when (val result = trustWeave.revokeResult {
    credential(credentialId)
    statusList(statusListId)
}) {
    is RevocationResult.Revoked -> println("Credential revoked")
    is RevocationResult.NotRevoked -> println("Entry unchanged")
    is RevocationResult.TimedOut -> println("Timed out — re-check status before retrying")
    is RevocationResult.NotConfigured -> println("Revocation manager not configured: ${result.reason}")
    is RevocationResult.InvalidRequest -> println("Invalid request: ${result.reason}")
    is RevocationResult.Failure -> println("Failed: ${result.reason}")
}
```

## 4. KMS Provider Names & Contract Hardening

### Canonical `KmsProviders.*` SPI Names

`KmsProviders.*` constants are aligned to canonical SPI provider names (`inmemory`, `aws`, `azure`, `google-cloud-kms`, `vault`). `KeyManagementServices` continues to accept legacy aliases (such as `inMemory`, `awsKms`, `azureKms`, `googleKms`) case-insensitively during the transition, but new code should use `KmsProviders.*` or the canonical SPI names.

### Provider-Specific KMS Behaviour Changes

Review these contract fixes before upgrading if you use the corresponding KMS plugins:

- **AWS KMS**: Does **not** support `Ed25519` — use `"P-256"` (`Secp256r1`) or `secp256k1` (or RSA).
- **HashiCorp Vault**: RSA signing now explicitly sends `signature_algorithm=pkcs1v15` with key-sized hashes (RSA-3072 -> `sha2-384`, RSA-4096 -> `sha2-512`); `generateKey` returns `DuplicateKeyId` rather than silently reusing an existing Transit key; `sign` on a missing key returns `KeyNotFound`.
- **Google Cloud KMS**: EC and RSA keys send key-algorithm-matched `AsymmetricSignRequest.digest` values; new RSA-4096 keys use `RSA_SIGN_PKCS1_4096_SHA512`; JWK `n`/`e` omit leading `0x00` bytes and Ed25519 `x` is the raw 32-byte key.
- **Azure Key Vault**: `deleteKey` returns `Failure` on timeout (no longer swallowed as cancellation) and `sign` honors `endpointOverride`.
- **Thales / Fortanix / IBM / PKCS#11 / CloudHSM**: `sign` validates the key's actual algorithm and returns `UnsupportedAlgorithm` on mismatch; URL-path key IDs reject traversal/control characters; Fortanix and IBM reject plaintext `http://` to non-loopback hosts.

## 5. Persistent Wallet & Storage Breaking Changes

### `DatabaseWallet` Schema v2 (Composite Primary Keys)

`DatabaseWallet` tables are now keyed by `(wallet_id, id)` with a `schema_version` table recording version `2`. Existing PostgreSQL and H2 databases migrate automatically and idempotently on first `DatabaseWallet.create`:

- **Back up your database** before upgrading.
- **Do not run 0.6.x and 0.7.0 instances concurrently** against the same database during rollout.

### `CloudWalletFactory` Per-Wallet Key Prefixes

`CloudWalletFactory` now scopes every wallet under `<storagePath>/wallet-<percent-encoded walletId>/` so two wallets sharing a `storagePath` cannot access each other's credentials.

- **Migration**: Copy existing objects from `<storagePath>/{credentials,collections,metadata,tags}/` into `<storagePath>/wallet-<walletId>/`, or pass `additionalProperties["legacyLayout"] = true` for a single-wallet bucket during transition.
- Passing `WalletCreationOptions.encryptionKey` to `CloudWalletFactory` or `DatabaseWalletFactory` now throws `IllegalArgumentException` (neither encrypts credentials client-side; use storage-side encryption or `wallet:plugins:file`).

## 6. Security & Protocol Hardening

- **SSRF guard**: `ssrfGuardedOkHttpClient()` checks every TCP connect (including redirects), blocks IP-literal private/loopback/link-local addresses, and ignores system proxies.
- **OID4VP / SIOP / OID4VCI**: `request_uri` and `response_uri` enforce HTTPS-or-loopback (`localhost` or literal loopback IP only), and protocol response bodies are capped at 1 MiB.
- **DIDComm PostgreSQL storage**: Encrypted rows use keyed blind indexes (`MessageEncryption.blindIndex`) instead of plaintext columns; rows stored prior to `0.7.0` must be re-stored to be discoverable by DID/thread lookups.
- **EVM anchors**: Gas limits incorporate the EIP-7623 calldata floor.

## 7. Verification Checklist

1. Update Gradle wrapper (`9.8.1`), Kotlin (`2.4.21`), and TrustWeave BOM (`0.7.0`).
2. Replace deprecated `revoke { }`, `trust { }`, and `getKeyId(...)` calls with `revokeResult { }`, `trustResult { }`, and `getKeyIdResult(...)`.
3. Remove any dependencies on the 12 unpublished stub modules.
4. Back up databases and verify `DatabaseWallet` / `CloudWalletFactory` migrations in staging.
5. Run your full unit and integration test suite.

For the full list of changes, see [CHANGELOG.md](https://github.com/geoknoesis/trustweave/blob/main/CHANGELOG.md).
