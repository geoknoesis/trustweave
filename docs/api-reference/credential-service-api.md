# Credential Service API Reference

This document describes the SPI surface that credential issuers/verifiers plug into and the typed options used to configure providers.

```kotlin
dependencies {
    implementation("com.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("com.geoknoesis.vericore:vericore-spi:1.0.0-SNAPSHOT")
}
```

**Result:** Your project can implement and register custom credential providers using the SPI documented below.

## Core Interfaces

### CredentialService

Implementations perform issuance, verification, and presentation operations.

```kotlin
interface CredentialService {
    val providerName: String
    val supportedProofTypes: List<String>
    val supportedSchemaFormats: List<SchemaFormat>

    suspend fun issueCredential(
        credential: VerifiableCredential,
        options: CredentialIssuanceOptions
    ): VerifiableCredential

    suspend fun verifyCredential(
        credential: VerifiableCredential,
        options: CredentialVerificationOptions
    ): CredentialVerificationResult

    suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        options: PresentationOptions
    ): VerifiablePresentation

    suspend fun verifyPresentation(
        presentation: VerifiablePresentation,
        options: PresentationVerificationOptions
    ): PresentationVerificationResult
}
```

#### Method summary

| Method | Purpose | Returns | Exceptions | Notes |
|--------|---------|---------|------------|-------|
| `issueCredential` | Canonicalise + sign a VC. | `VerifiableCredential` (with proof). | `IllegalArgumentException` for unsupported proof types or schemas. | Used by `VeriCore.issueCredential` facade. |
| `verifyCredential` | Validate a VC’s proof and optional policies. | `CredentialVerificationResult` | `IllegalStateException` if configuration missing (resolver, status service). | Returns `valid=false` when checks fail. |
| `createPresentation` | Assemble a verifiable presentation. | `VerifiablePresentation` | Depends on provider (unsupported -> `UnsupportedOperationException`). | Typically optional; many issuers delegate to wallet presentation services. |
| `verifyPresentation` | Validate presentation proofs and challenges. | `PresentationVerificationResult` | `IllegalArgumentException` for invalid challenge/domain. | Verifiers should check `result.errors`. |

### CredentialServiceProvider

Providers bridge ServiceLoader discovery and actual `CredentialService` instances.

```kotlin
interface CredentialServiceProvider {
    val name: String

    fun create(
        options: CredentialServiceCreationOptions = CredentialServiceCreationOptions()
    ): CredentialService?
}
```

| Method | Returns | Exceptions | Notes |
|--------|---------|------------|-------|
| `create` | `CredentialService?` | – | Return `null` to opt out (e.g., disabled). Called by `CredentialServiceRegistry`. |

## CredentialServiceCreationOptions

`CredentialServiceCreationOptions` replaces the old `Map<String, Any?>` pattern with a structured configuration object.

```kotlin
import com.geoknoesis.vericore.credential.CredentialServiceCreationOptionsBuilder

val options = CredentialServiceCreationOptionsBuilder().apply {
    enabled = true
    priority = 10
    endpoint = "https://issuer.example.com"
    apiKey = System.getenv("ISSUER_API_KEY")
    property("batchSize", 100)
}.build()
```

| Field | Type | Description |
|-------|------|-------------|
| `enabled` | `Boolean` | Master toggle so providers can return `null` when disabled. Defaults to `true`. |
| `priority` | `Int?` | Optional load-order hint when multiple providers are registered. |
| `endpoint` | `String?` | Base URL or connection identifier for remote services. |
| `apiKey` | `String?` | Secret token or credential used during initialization. |
| `additionalProperties` | `Map<String, Any?>` | Provider specific data injected via `property("name", value)`. |

> Providers may throw `IllegalArgumentException` when required fields (endpoint, apiKey) are missing. Use typed builder setters to catch issues at compile time.

Providers can still interoperate with code that expects maps through `toLegacyMap()`:

```kotlin
val legacy = options.toLegacyMap() // Useful when delegating to an adapter that still consumes Map<String, Any?>
```

## Provider Implementation Example

```kotlin
class HttpIssuerProvider : CredentialServiceProvider {
    override val name: String = "httpIssuer"

    override fun create(options: CredentialServiceCreationOptions): CredentialService? {
        if (!options.enabled) return null

        val endpoint = options.endpoint ?: return null
        val apiKey = options.apiKey ?: error("apiKey is required for $name")
        val batchSize = options.additionalProperties["batchSize"] as? Int ?: 50

        return HttpCredentialIssuer(
            httpClient = buildClient(endpoint, apiKey, batchSize)
        )
    }
}
```

## Consumption from VeriCore

When the provider is on the classpath, `CredentialServiceRegistry` and the VeriCore facade automatically hand it the typed options:

```kotlin
val registry = CredentialServiceRegistry.create()
val credential = registry.issue(
    credential = vc,
    options = CredentialIssuanceOptions(
        providerName = "httpIssuer",
        additionalOptions = mapOf("audience" to "did:key:holder")
    )
)
```

`additionalOptions` on the issuance/verification options remain a map because they carry per-call data, whereas the provider-level configuration is now strongly typed.

## Related samples

- [`QuickStartSample`](../../distribution/vericore-examples/src/main/kotlin/com/geoknoesis/vericore/examples/quickstart/QuickStartSample.kt) demonstrates issuance, verification, and anchoring using the default in-memory provider chain.
- Scenario examples in `vericore-examples` showcase custom providers (HTTP issuers, GoDiddy integrations).

