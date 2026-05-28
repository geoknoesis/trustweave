# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build
./gradlew build                                         # Full build with tests
./gradlew build -x test                                 # Build without tests
./gradlew :did:did-core:build                           # Build a specific module

# Testing
./gradlew test                                          # All tests
./gradlew :did:did-core:test                            # Tests for a specific module
./gradlew :did:did-core:test --tests "DidMethodTest"    # Single test class

# Code quality
./gradlew ktlintCheck                                   # Lint check
./gradlew ktlintFormat                                  # Auto-format

# Local publishing
./gradlew publishToMavenLocal
```

**Windows note**: Build outputs go to `%LOCALAPPDATA%\TrustWeave\gradle-build\` by default to avoid IDE/antivirus JAR locking. Set `trustweave.windowsInRepoBuild=true` in `gradle.properties` to keep outputs in-repo.

## Architecture Overview

TrustWeave is a Kotlin library for decentralized identity and trust management. It is structured around five domains, each with a `-core` module defining SPI interfaces and multiple plugin modules providing implementations:

| Domain | Core Module | What it does |
|---|---|---|
| **DID** | `did:did-core` | DID CRUD, resolution, batch ops, registrar |
| **Wallet** | `wallet:wallet-core` | Credential storage, presentation, lifecycle |
| **KMS** | `kms:kms-core` | Key management (sign, verify, generate) |
| **Anchors** | `anchors:anchor-core` | Blockchain anchoring for DIDs/credentials |
| **Credentials** | `credentials:credential-api` | VC issuance, verification, exchange protocols |

### Entry Point

`trust/src/main/kotlin/org/trustweave/trust/TrustWeave.kt` is the main facade. It wires all services together:

```kotlin
// In-memory quick start (did:key + in-memory KMS)
val tw = TrustWeave.quickStart()

// Custom DSL configuration
val tw = TrustWeave.build {
    kms { provider = AwsKmsProvider(...) }
    did { methods += EthrDidMethod(...) }
    wallet { storage = DatabaseWalletStorage(...) }
}
```

### Plugin System

All extensible services use SPI-based discovery via `PluginMetadata` / `PluginLifecycle` interfaces in `common`. Plugins register themselves in `META-INF/services/` files. New providers (KMS, DID method, blockchain, wallet storage) implement the corresponding SPI interface and are loaded automatically if on the classpath.

### Result Pattern

All service operations return `Result<T>` sealed classes — never raw values or thrown exceptions across module boundaries. Use `.getOrThrow()`, `.getOrNull()`, or pattern-match on `Success`/`Failure`. Domain-specific subtypes: `IssuanceResult`, `VerificationResult`.

### Exception Hierarchy

`TrustWeaveException` → `PluginException`, `ProviderException`, `ConfigException`, `SerializationException`, plus domain-specific: `DidException`, `WalletException`, `KmsException`, `BlockchainException`.

### DSL Builders

Type-safe builders are the primary configuration API: `DidBuilder`, `DidDocumentBuilder`, `IssuanceBuilder`, `VerificationBuilder`, `WalletBuilder`, etc. Prefer builders over raw data class construction.

## Module Map

86+ modules across five domains. Key modules:

- **`common`** — shared utilities, exceptions, plugin infrastructure
- **`trust`** — main facade (`TrustWeave.kt`), integration tests
- **`testkit`** — test doubles and in-memory implementations (use in tests instead of mocks)
- **`contract`** — smart contract interfaces
- **`distribution:all`** — all-in-one BOM for consumers
- **`distribution:examples`** — runnable usage examples per scenario

DID method plugins follow naming: `did:plugins:<method>` (e.g., `did:plugins:key`, `did:plugins:web`, `did:plugins:ethr`).

KMS provider plugins: `kms:plugins:<provider>` (e.g., `kms:plugins:aws`, `kms:plugins:azure`, `kms:plugins:in-memory`).

Blockchain plugins: `anchors:plugins:<chain>` (e.g., `anchors:plugins:ethereum`, `anchors:plugins:algorand`).

## Key Files

| File | Purpose |
|---|---|
| `settings.gradle.kts` | Module definitions (all 86+ includes) |
| `build.gradle.kts` | Central config, publishing, Windows JAR handling |
| `gradle/libs.versions.toml` | Centralized dependency versions and bundles |
| `gradle.properties` | JVM args, parallel builds, Kotlin daemon settings |

## Tech Stack

- **Language**: Kotlin 2.3.21, JVM target 21 (enforced via toolchain)
- **Build**: Gradle 9.5.0
- **Testing**: JUnit 5 + Kotest assertions; TestContainers for DB/Docker tests
- **HTTP**: Ktor 2.3.x (internal servers), OkHttp (client)
- **Security**: Bouncy Castle 1.84, Nimbus JOSE JWT
- **Blockchain**: Web3j, Algorand SDK, Bitcoin-j
- **Spring Boot**: 3.5.x (registrar server variant only)
- **Coroutines**: kotlinx-coroutines 1.10.x throughout

## Coding Conventions

- KTLint is enforced in CI — always run `./gradlew ktlintFormat` before committing
- Compiler flags: `-Xjsr305=strict` (strict null-safety for JSR-305 annotations)
- Conventional Commits are required for PRs
- Configuration cache is enabled; the Kotlin circular dep is mitigated via `kotlin.build.archivesTaskOutputAsFriendModule=false` in `gradle.properties`
