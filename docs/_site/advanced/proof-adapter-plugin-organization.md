# ProofAdapter Plugin Organization Guide

## Overview

This document describes how to organize and structure plugins that implement the `ProofAdapter` SPI for different credential format standards (VC-LD, SD-JWT-VC, AnonCreds, mDL, etc.).

---

## Directory Structure

### Recommended Organization

```
credentials/
├── credential-api/              # Core API (SPI definitions)
│   └── src/main/kotlin/com/trustweave/credential/proof/spi/
│       ├── ProofAdapter.kt
│       └── ProofAdapterProvider.kt
│
└── plugins/                     # All credential plugins
    ├── proof/                   # Format-specific proof adapters
    │   ├── vcld/               # W3C Verifiable Credentials (Linked Data)
    │   │   ├── build.gradle.kts
    │   │   └── src/main/
    │   │       ├── kotlin/com/trustweave/credential/proof/vcld/
    │   │       │   ├── VcLdProofAdapter.kt
    │   │       │   └── VcLdProofAdapterProvider.kt
    │   │       └── resources/META-INF/services/
    │   │           └── com.trustweave.credential.proof.spi.ProofAdapterProvider
    │   │
    │   ├── sdjwt/              # SD-JWT / SD-JWT-VC
    │   │   ├── build.gradle.kts
    │   │   └── src/main/...
    │   │
    │   ├── anoncreds/          # Hyperledger AnonCreds
    │   │   ├── build.gradle.kts
    │   │   └── src/main/...
    │   │
    │   ├── mdl/                # ISO/IEC 18013-5 Mobile Driver's License
    │   │   ├── build.gradle.kts
    │   │   └── src/main/...
    │   │
    │   ├── x509/               # X.509 / PKI-based credentials
    │   │   ├── build.gradle.kts
    │   │   └── src/main/...
    │   │
    │   └── passkey/            # WebAuthn / PassKeys
    │       ├── build.gradle.kts
    │       └── src/main/...
    │
    ├── exchange/               # Exchange protocol plugins (separate concern)
    │   ├── didcomm/
    │   ├── oidc4vci/
    │   └── chapi/
    │
    └── status-list/            # Status list implementations
        └── database/
```

---

## Naming Conventions

### Module Names
- Format: `credential-proof-{format}`
- Examples:
  - `credential-proof-vcld`
  - `credential-proof-sdjwt`
  - `credential-proof-anoncreds`
  - `credential-proof-mdl`

### Package Names
- Format: `com.trustweave.credential.proof.{format}`
- Examples:
  - `com.trustweave.credential.proof.vcld`
  - `com.trustweave.credential.proof.sdjwt`
  - `com.trustweave.credential.proof.anoncreds`

### Class Names
- **Adapter**: `{Format}ProofAdapter`
  - Examples: `VcLdProofAdapter`, `SdJwtProofAdapter`, `AnonCredsProofAdapter`
- **Provider**: `{Format}ProofAdapterProvider`
  - Examples: `VcLdProofAdapterProvider`, `SdJwtProofAdapterProvider`

---

## Module Structure Template

### build.gradle.kts

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "com.trustweave.credentials"

dependencies {
    // Core API dependency
    implementation(project(":credentials:credential-api"))
    
    // Common dependencies
    implementation(project(":common"))
    implementation(project(":did:did-core"))
    
    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    
    // Format-specific dependencies
    // Example for VC-LD:
    implementation("com.github.jsonld-java:jsonld-java:0.13.4")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    
    // Example for SD-JWT:
    // implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    
    // Test dependencies
    testImplementation(project(":testkit"))
    testImplementation(project(":credentials:credential-api"))
}
```

---

## Implementation Pattern

### 1. Create ProofAdapter Implementation

```kotlin
package com.trustweave.credential.proof.vcld

import com.trustweave.credential.format.CredentialFormat
import com.trustweave.credential.format.FormatOptions
import com.trustweave.credential.model.CredentialEnvelope
import com.trustweave.credential.proof.spi.ProofAdapter
import com.trustweave.credential.proof.spi.ProofAdapterCapabilities
import com.trustweave.credential.proof.spi.ProofAdapterConfig
import com.trustweave.credential.requests.IssuanceRequest
import com.trustweave.credential.requests.PresentationRequest
import com.trustweave.credential.requests.VerificationOptions
import com.trustweave.credential.results.VerificationResult

/**
 * VC-LD (Verifiable Credentials Linked Data) proof adapter.
 * 
 * Supports W3C Verifiable Credentials 2.0 with Linked Data Proofs.
 */
class VcLdProofAdapter(
    private val config: ProofAdapterConfig = ProofAdapterConfig()
) : ProofAdapter {
    
    override val format = CredentialFormat.VcLd
    override val formatName = "Verifiable Credentials (Linked Data)"
    override val formatVersion = "2.0"
    
    override val capabilities = ProofAdapterCapabilities(
        selectiveDisclosure = true,    // VC-LD supports selective disclosure
        zeroKnowledge = false,          // VC-LD doesn't support ZK by default
        revocation = true,              // Via status lists
        presentation = true,            // Supports presentations
        predicates = true               // Via JSON-LD predicates
    )
    
    override suspend fun issue(request: IssuanceRequest): CredentialEnvelope {
        // 1. Convert IssuanceRequest to VC-LD format
        // 2. Generate Linked Data Proof
        // 3. Convert back to CredentialEnvelope
        // Implementation here...
    }
    
    override suspend fun verify(
        envelope: CredentialEnvelope,
        options: VerificationOptions
    ): VerificationResult {
        // 1. Extract proof from envelope.proof.data
        // 2. Verify Linked Data Proof
        // 3. Verify DID Document resolution
        // 4. Return VerificationResult
        // Implementation here...
    }
    
    override suspend fun derivePresentation(
        envelope: CredentialEnvelope,
        request: PresentationRequest
    ): CredentialEnvelope {
        // 1. Extract disclosed claims from request
        // 2. Create selective disclosure
        // 3. Generate new proof
        // 4. Return presentation envelope
        // Implementation here...
    }
    
    override suspend fun initialize(config: ProofAdapterConfig) {
        // Initialize any required resources
        // Load configuration
    }
    
    override suspend fun close() {
        // Cleanup resources
    }
    
    override fun isReady(): Boolean {
        // Check if adapter is ready for operations
        return true
    }
}
```

### 2. Create ProofAdapterProvider

```kotlin
package com.trustweave.credential.proof.vcld

import com.trustweave.credential.format.CredentialFormat
import com.trustweave.credential.proof.spi.ProofAdapter
import com.trustweave.credential.proof.spi.ProofAdapterConfig
import com.trustweave.credential.proof.spi.ProofAdapterProvider

/**
 * SPI Provider for VC-LD proof adapter.
 */
class VcLdProofAdapterProvider : ProofAdapterProvider {
    
    override val name = "vcld"
    
    override val supportedFormats = listOf(CredentialFormat.VcLd)
    
    override fun create(options: Map<String, Any?>): ProofAdapter? {
        return try {
            val config = ProofAdapterConfig(
                properties = options
            )
            VcLdProofAdapter(config)
        } catch (e: Exception) {
            // Log error
            null
        }
    }
}
```

### 3. Register SPI Provider

Create file: `src/main/resources/META-INF/services/com.trustweave.credential.proof.spi.ProofAdapterProvider`

```
com.trustweave.credential.proof.vcld.VcLdProofAdapterProvider
```

---

## Package Organization Within Plugin

```
credential-proof-vcld/
└── src/main/kotlin/com/trustweave/credential/proof/vcld/
    ├── VcLdProofAdapter.kt              # Main adapter implementation
    ├── VcLdProofAdapterProvider.kt      # SPI provider
    ├── internal/                        # Internal implementation details
    │   ├── VcLdProofGenerator.kt        # Proof generation logic
    │   ├── VcLdProofVerifier.kt         # Proof verification logic
    │   ├── VcLdCanonicalizer.kt         # JSON-LD canonicalization
    │   └── VcLdSignatureSuite.kt        # Signature suite handlers
    ├── model/                           # Format-specific models (optional)
    │   └── VcLdProof.kt
    └── util/                            # Utilities (optional)
        └── VcLdUtils.kt
```

**Visibility Guidelines:**
- **Public**: Only `VcLdProofAdapter` and `VcLdProofAdapterProvider`
- **Internal**: All implementation details in `internal/` package
- **Private**: Format-specific models if needed

---

## Integration with CredentialService

### Auto-Discovery (Recommended)

```kotlin
import com.trustweave.credential.*
import com.trustweave.credential.proof.ProofAdapters
import com.trustweave.credential.proof.ProofRegistries
import com.trustweave.did.resolver.DidResolver

// Auto-discover and register all adapters on classpath
val registry = ProofRegistries.default()
ProofAdapters.autoRegister(registry)

val service = createCredentialService(
    adapterRegistry = registry,
    didResolver = didResolver
)
```

### Manual Registration

```kotlin
import com.trustweave.credential.proof.vcld.VcLdProofAdapter
import com.trustweave.credential.proof.ProofRegistries

val registry = ProofRegistries.default()
registry.register(VcLdProofAdapter())
```

### Format-Specific Registration

```kotlin
val registry = ProofRegistries.default()

// Only register specific formats
ProofAdapters.autoRegisterFormats(
    registry = registry,
    formats = listOf(
        CredentialFormat.VcLd,
        CredentialFormat.SdJwtVc
    )
)
```

---

## Testing Plugin

### Test Structure

```
credential-proof-vcld/
└── src/test/kotlin/com/trustweave/credential/proof/vcld/
    ├── VcLdProofAdapterTest.kt
    ├── VcLdProofGeneratorTest.kt
    └── VcLdProofVerifierTest.kt
```

### Example Test

```kotlin
import com.trustweave.credential.*
import com.trustweave.credential.proof.ProofRegistries
import com.trustweave.credential.proof.vcld.VcLdProofAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VcLdProofAdapterTest {
    
    @Test
    fun `should issue VC-LD credential`() = runTest {
        val adapter = VcLdProofAdapter()
        val request = IssuanceRequest(
            format = CredentialFormat.VcLd,
            issuer = IssuerId("did:key:issuer"),
            subject = SubjectId.fromDid(Did("did:key:subject")),
            type = listOf(CredentialType.VerifiableCredential),
            claims = mapOf(
                "name" to JsonPrimitive("Alice")
            ),
            issuedAt = Instant.now()
        )
        
        val envelope = adapter.issue(request)
        
        assertEquals(CredentialFormat.VcLd, envelope.proof.format)
        assertTrue(envelope.proof.data.isNotBlank())
    }
    
    @Test
    fun `should verify VC-LD credential`() = runTest {
        // Test verification logic
    }
}
```

---

## Best Practices

### 1. **Format Isolation**
- Each format should be completely isolated in its own module
- No cross-format dependencies
- Share only through `credential-api`

### 2. **Dependency Management**
- Minimize dependencies
- Prefer standard libraries (jsonld-java, nimbus-jose-jwt, etc.)
- Avoid bringing in large frameworks

### 3. **Error Handling**
- Return `VerificationResult.Invalid` with clear error messages
- Don't throw exceptions for expected validation failures
- Log errors internally but expose user-friendly messages

### 4. **Performance**
- Cache DID Documents when appropriate
- Lazy initialization of expensive resources
- Use coroutines for async operations

### 5. **Documentation**
- Document format-specific behaviors in KDoc
- Provide usage examples
- Document required configuration options

### 6. **Versioning**
- Follow semantic versioning
- Document breaking changes in CHANGELOG
- Maintain compatibility with `credential-api` versions

---

## Example: Complete VC-LD Plugin

See the reference implementation structure:

```
credential-proof-vcld/
├── build.gradle.kts
├── README.md
└── src/
    ├── main/
    │   ├── kotlin/com/trustweave/credential/proof/vcld/
    │   │   ├── VcLdProofAdapter.kt
    │   │   ├── VcLdProofAdapterProvider.kt
    │   │   └── internal/
    │   │       ├── VcLdProofGenerator.kt
    │   │       ├── VcLdProofVerifier.kt
    │   │       └── VcLdCanonicalizer.kt
    │   └── resources/META-INF/services/
    │       └── com.trustweave.credential.proof.spi.ProofAdapterProvider
    └── test/
        └── kotlin/com/trustweave/credential/proof/vcld/
            └── VcLdProofAdapterTest.kt
```

---

## Migration from credential-core

If migrating existing proof generators from `credential-core`:

1. **Extract format-specific code** to new plugin module
2. **Implement `ProofAdapter`** instead of format-specific interfaces
3. **Create `ProofAdapterProvider`** for SPI registration
4. **Update dependencies** to use `credential-api` instead of `credential-core`
5. **Register in META-INF/services**

Example migration path:
```
credential-core/proof/JwtProofGenerator.kt
  → credential-proof-sdjwt/src/.../SdJwtProofAdapter.kt
```

---

## Summary

**Key Principles:**
1. ✅ One format = One module
2. ✅ Clear naming: `credential-proof-{format}`
3. ✅ Package: `com.trustweave.credential.proof.{format}`
4. ✅ SPI registration via `META-INF/services`
5. ✅ Auto-discovery via `ProofAdapters.discoverProviders()`
6. ✅ Format isolation (no cross-format deps)
7. ✅ Public API only through `ProofAdapter` interface

This organization ensures:
- **Discoverability**: Plugins auto-discover via ServiceLoader
- **Isolation**: Format implementations don't interfere
- **Extensibility**: Easy to add new formats
- **Maintainability**: Clear module boundaries
- **Testability**: Each plugin can be tested independently

