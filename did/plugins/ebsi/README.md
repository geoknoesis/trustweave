# TrustWeave did:ebsi Plugin

DID method plugin for the [EU Blockchain Services Infrastructure](https://hub.ebsi.eu/) (EBSI), providing create / resolve / update / deactivate operations against the EBSI DID Registry REST API.

## Overview

This module implements the [`did:ebsi`](https://hub.ebsi.eu/vc-framework/did/legal-entities) DID method on top of TrustWeave's [DidMethod](../../did-core/src/main/kotlin/org/trustweave/did/DidMethod.kt) SPI. It targets the EBSI DID Registry v5 JSON-RPC / REST endpoints and ships with three pre-configured network environments (pilot, conformance, production).

Key design points:

- **Identifier derivation** — `did:ebsi:<base58btc>` where the suffix is the base58btc encoding of the first 16 bytes of `SHA-256(publicKey)`. For P-256 keys the public-key bytes are the concatenation of the `x` and `y` JWK coordinates.
- **Resolve-only by default** — no bearer token is required to read DID documents from the registry.
- **Write operations gated by a bearer token** — `create`, `update`, and `deactivate` require [`EbsiDidConfig.bearerToken`](src/main/kotlin/org/trustweave/ebsidid/EbsiDidConfig.kt) to be set; otherwise [`EbsiException.authRequired`](src/main/kotlin/org/trustweave/ebsidid/EbsiException.kt) is thrown.
- **In-memory cache fallback** — DIDs created in the current process are cached locally so that resolution still works when the registry is unreachable or returns 404.

## Identifier Format

```
did:ebsi:<base58btc(SHA-256(publicKey)[0..16])>
```

EBSI mandates **P-256** as the canonical key type. The plugin will accept other algorithms supported by the configured KMS (for example `Ed25519`), in which case the raw JWK `x` bytes (or a key-id fallback) are hashed instead.

## Configuration

Configuration lives in the [`EbsiDidConfig`](src/main/kotlin/org/trustweave/ebsidid/EbsiDidConfig.kt) data class:

| Property | Type | Default | Description |
|---|---|---|---|
| `apiBaseUrl` | `String` | (per network) | Base URL of the EBSI DID Registry REST API |
| `network` | [`EbsiNetwork`](src/main/kotlin/org/trustweave/ebsidid/EbsiNetwork.kt) | — | `PILOT`, `CONFORMANCE`, or `PRODUCTION` |
| `bearerToken` | `String?` | `null` | OAuth bearer token; `null` selects resolve-only mode |
| `timeoutSeconds` | `Long` | `30` | HTTP connect / read / write timeout |

Convenience factories pre-set the matching base URL:

```kotlin
import org.trustweave.ebsidid.EbsiDidConfig

// Resolve-only configurations
val pilot       = EbsiDidConfig.pilot()
val conformance = EbsiDidConfig.conformance()
val production  = EbsiDidConfig.production()

// With a bearer token for create / update / deactivate
val writable = EbsiDidConfig.pilot(bearerToken = "eyJhbGciOi...")
```

The base URLs exposed as constants on the companion object are:

- `EbsiDidConfig.PILOT_URL` — `https://api-pilot.ebsi.eu`
- `EbsiDidConfig.CONFORMANCE_URL` — `https://api-conformance.ebsi.eu`
- `EbsiDidConfig.PRODUCTION_URL` — `https://api.ebsi.eu`

## Supported Operations

| Operation | Bearer token required? | EBSI endpoint |
|---|---|---|
| `resolveDid(did)` | No | `GET  /did-registry/v5/identifiers/{did}` |
| `createDid(options)` | Yes | `POST /did-registry/v5/jsonrpc` (`insertDidDocument`) |
| `updateDid(did, updater)` | Yes | `PATCH /did-registry/v5/identifiers/{did}` |
| `deactivateDid(did)` | Yes | `PATCH /did-registry/v5/identifiers/{did}` (empties verification methods) |

When no token is configured, `createDid` / `updateDid` / `deactivateDid` throw [`EbsiException`](src/main/kotlin/org/trustweave/ebsidid/EbsiException.kt) with code `EBSI_AUTH_REQUIRED`. `resolveDid` always returns a [`DidResolutionResult`](../../did-core/src/main/kotlin/org/trustweave/did/resolver/DidResolutionResult.kt), falling back to the in-memory cache on 404 or network errors.

## Usage

### Gradle dependency

```kotlin
dependencies {
    implementation("org.trustweave.did:did-plugins-ebsi:0.6.0")
}
```

### Direct instantiation

```kotlin
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.ebsidid.EbsiDidConfig
import org.trustweave.ebsidid.EbsiDidMethod
import org.trustweave.ebsidid.EbsiException
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val kms    = InMemoryKeyManagementService()
    val config = EbsiDidConfig.pilot(bearerToken = System.getenv("EBSI_BEARER_TOKEN"))
    val method = EbsiDidMethod(kms, config)

    // Create a did:ebsi DID using a P-256 key (EBSI canonical key type)
    val document = try {
        method.createDid(
            didCreationOptions { algorithm = KeyAlgorithm.P256 },
        )
    } catch (e: EbsiException) {
        println("EBSI error ${e.code} (HTTP ${e.httpStatus}): ${e.message}")
        return@runBlocking
    }

    println("Created: ${document.id.value}")
}
```

### Resolving an existing DID (no token required)

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.errorMessage
import org.trustweave.ebsidid.EbsiDidConfig
import org.trustweave.ebsidid.EbsiDidMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val method = EbsiDidMethod(
        kms = InMemoryKeyManagementService(),
        config = EbsiDidConfig.pilot(), // resolve-only
    )

    val result = method.resolveDid(Did("did:ebsi:z23EQVGi5so9sBwytv5xfk"))

    when (result) {
        is DidResolutionResult.Success -> println(result.document.id.value)
        is DidResolutionResult.Failure -> println("Failed: ${result.errorMessage}")
    }
}
```

### Updating and deactivating

```kotlin
import org.trustweave.did.identifiers.Did
import org.trustweave.ebsidid.EbsiDidConfig
import org.trustweave.ebsidid.EbsiDidMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val method = EbsiDidMethod(
        kms = InMemoryKeyManagementService(),
        config = EbsiDidConfig.pilot(bearerToken = System.getenv("EBSI_BEARER_TOKEN")),
    )

    val did = Did("did:ebsi:z23EQVGi5so9sBwytv5xfk")

    // Update — apply a copy() transform to the resolved document
    method.updateDid(did) { current -> current.copy(service = emptyList()) }

    // Deactivate — clears verification methods on the registry
    val wasDeactivated: Boolean = method.deactivateDid(did)
    println("Deactivated: $wasDeactivated")
}
```

### Injecting a custom OkHttpClient

`EbsiDidMethod` accepts an optional [`OkHttpClient`](https://square.github.io/okhttp/) for tests or to share connection pools / interceptors:

```kotlin
val method = EbsiDidMethod(kms, config, httpClient = myOkHttpClient)
```

## SPI Discovery

The plugin registers itself for automatic discovery via `java.util.ServiceLoader`. The service file [`META-INF/services/org.trustweave.did.spi.DidMethodProvider`](src/main/resources/META-INF/services/org.trustweave.did.spi.DidMethodProvider) declares:

```
org.trustweave.ebsidid.spi.EbsiDidMethodProvider
```

When the JAR is on the classpath, [`EbsiDidMethodProvider`](src/main/kotlin/org/trustweave/ebsidid/spi/EbsiDidMethodProvider.kt) handles `methodName == "ebsi"` and reads configuration from `DidCreationOptions.additionalProperties` via [`EbsiDidConfig.fromMap`](src/main/kotlin/org/trustweave/ebsidid/EbsiDidConfig.kt). Recognised keys:

| Key | Type | Notes |
|---|---|---|
| `kms` | `KeyManagementService` | Required (resolved by the abstract provider base class) |
| `apiBaseUrl` | `String` | Overrides the per-network default |
| `network` | `String` | `PILOT` / `CONFORMANCE` / `PRODUCTION` (default: `PILOT`) |
| `bearerToken` | `String` | Required for create / update / deactivate |
| `timeoutSeconds` | `Number` | HTTP timeout in seconds (default: 30) |

When invoked without configuration, the provider returns an `EbsiDidMethod` pointing at the pilot environment in resolve-only mode.

## Limitations / Status

- **JSON-RPC create flow is minimal.** Registration sends a single `insertDidDocument` call with the document as the only param. EBSI's full onboarding flow (Trusted Apps registration, ETSI ES 256K signed payloads, capability tokens) is **not** implemented in this plugin; you are expected to obtain a bearer token out-of-band.
- **Update is `PATCH` with the full document.** There is no JSON-Patch diffing — the updater function receives the resolved document and must return the desired final state.
- **Deactivation is local-only without a token.** Without a bearer token the operation only removes the document from the in-memory cache and returns `false` for unknown DIDs.
- **No JWS verification of registry responses.** The registry response body is parsed as-is into a `DidDocument`. Consumers requiring stronger trust guarantees should layer a verifier over the resolved document.
- **P-256 is the canonical key type.** Other key algorithms work but fall back to hashing a single coordinate / key-id, which may not match identifiers produced by other EBSI implementations.

## References

- [EBSI DID Method for Legal Entities](https://hub.ebsi.eu/vc-framework/did/legal-entities) — authoritative method specification
- [W3C DID Core 1.0](https://www.w3.org/TR/did-core/) — the underlying DID data model
- [`did-core`](../../did-core/) — TrustWeave DID SPI this plugin implements
- [`did:plugins:base`](../base/) — shared `AbstractDidMethod` / `AbstractDidMethodProvider` used here
