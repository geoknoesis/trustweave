# ProofAdapter Plugins Implementation

## ✅ Implementation Complete

All ProofAdapter plugins for major credential formats have been implemented with skeleton structures ready for full implementation.

---

## Implemented Plugins

### 1. ✅ VC-LD Plugin (`credential-proof-vcld`)
**Format**: W3C Verifiable Credentials 2.0 (Linked Data Proofs)

**Location**: `credentials/plugins/proof/vcld/`

**Capabilities**:
- Selective disclosure
- Revocation (status lists)
- Presentations
- Predicates (JSON-LD)

**Dependencies**:
- `jsonld-java` for canonicalization
- `bouncycastle` for signatures

**Files**:
- `VcLdProofAdapter.kt` - Main adapter implementation
- `VcLdProofAdapterProvider.kt` - SPI provider
- `build.gradle.kts` - Build configuration
- `META-INF/services/...` - ServiceLoader registration

---

### 2. ✅ SD-JWT-VC Plugin (`credential-proof-sdjwt`)
**Format**: IETF SD-JWT-VC (Selective Disclosure JWT)

**Location**: `credentials/plugins/proof/sdjwt/`

**Capabilities**:
- Selective disclosure (core feature)
- Revocation
- Presentations

**Dependencies**:
- `nimbus-jose-jwt` for JWT handling

**Files**:
- `SdJwtProofAdapter.kt` - Main adapter implementation
- `SdJwtProofAdapterProvider.kt` - SPI provider
- `build.gradle.kts` - Build configuration
- `META-INF/services/...` - ServiceLoader registration

---

### 3. ✅ AnonCreds Plugin (`credential-proof-anoncreds`)
**Format**: Hyperledger AnonCreds

**Location**: `credentials/plugins/proof/anoncreds/`

**Capabilities**:
- Selective disclosure
- Zero-knowledge proofs
- Revocation (revocation registry)
- Presentations
- Predicates

**Dependencies**:
- AnonCreds library (placeholder - to be added)

**Files**:
- `AnonCredsProofAdapter.kt` - Main adapter implementation
- `AnonCredsProofAdapterProvider.kt` - SPI provider
- `build.gradle.kts` - Build configuration
- `META-INF/services/...` - ServiceLoader registration

---

### 4. ✅ mDL Plugin (`credential-proof-mdl`)
**Format**: ISO/IEC 18013-5 Mobile Driver's License

**Location**: `credentials/plugins/proof/mdl/`

**Capabilities**:
- Selective disclosure
- Revocation
- Presentations

**Dependencies**:
- `jose4j` for COSE/CBOR
- `cbor` for CBOR encoding

**Files**:
- `MdlProofAdapter.kt` - Main adapter implementation
- `MdlProofAdapterProvider.kt` - SPI provider
- `build.gradle.kts` - Build configuration
- `META-INF/services/...` - ServiceLoader registration

---

### 5. ✅ X.509 Plugin (`credential-proof-x509`)
**Format**: X.509 / PKI-based credentials

**Location**: `credentials/plugins/proof/x509/`

**Capabilities**:
- Revocation (CRL/OCSP)
- Presentations

**Dependencies**:
- `bouncycastle` for X.509 certificate handling

**Files**:
- `X509ProofAdapter.kt` - Main adapter implementation
- `X509ProofAdapterProvider.kt` - SPI provider
- `build.gradle.kts` - Build configuration
- `META-INF/services/...` - ServiceLoader registration

---

### 6. ✅ PassKey Plugin (`credential-proof-passkey`)
**Format**: WebAuthn / PassKeys

**Location**: `credentials/plugins/proof/passkey/`

**Capabilities**:
- Revocation
- Presentations (via WebAuthn assertions)

**Dependencies**:
- WebAuthn library (placeholder - to be added)

**Files**:
- `PassKeyProofAdapter.kt` - Main adapter implementation
- `PassKeyProofAdapterProvider.kt` - SPI provider
- `build.gradle.kts` - Build configuration
- `META-INF/services/...` - ServiceLoader registration

---

## Directory Structure

```
credentials/plugins/proof/
├── vcld/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── kotlin/org.trustweave/credential/proof/vcld/
│       │   ├── VcLdProofAdapter.kt
│       │   └── VcLdProofAdapterProvider.kt
│       └── resources/META-INF/services/
│           └── org.trustweave.credential.proof.spi.ProofAdapterProvider
│
├── sdjwt/
│   ├── build.gradle.kts
│   └── src/main/...
│
├── anoncreds/
│   ├── build.gradle.kts
│   └── src/main/...
│
├── mdl/
│   ├── build.gradle.kts
│   └── src/main/...
│
├── x509/
│   ├── build.gradle.kts
│   └── src/main/...
│
└── passkey/
    ├── build.gradle.kts
    └── src/main/...
```

---

## Implementation Status

### ✅ Completed
- x] All plugin module structures created
- x] All `ProofAdapter` implementations (skeleton)
- x] All `ProofAdapterProvider` implementations
- x] All ServiceLoader registrations
- x] All build configurations
- x] Proper package organization
- x] Format-specific capabilities defined

### 🔨 TODO: Full Implementation
Each plugin needs full implementation of:
- issue()` - Convert `IssuanceRequest` to format and generate proof
- verify()` - Verify proof and return `VerificationResult`
- derivePresentation()` - Selective disclosure implementation (where supported)
- Format-specific serialization/deserialization
- Integration with format libraries

---

## Usage

### Auto-Discovery (Recommended)

```kotlin
import org.trustweave.credential.*
import org.trustweave.credential.proof.ProofAdapters
import org.trustweave.credential.proof.ProofRegistries

// Auto-discover and register all plugins on classpath
val registry = ProofRegistries.default()
ProofAdapters.autoRegister(registry)

val service = createCredentialService(
    adapterRegistry = registry
)

// Now service supports all discovered formats
val formats = service.supportedFormats()
// [VcLd, SdJwtVc, AnonCreds, Mdl, X509, PassKey]
```

### Manual Registration

```kotlin
import org.trustweave.credential.proof.vcld.VcLdProofAdapter
import org.trustweave.credential.proof.ProofRegistries

val registry = ProofRegistries.default()
registry.register(VcLdProofAdapter())
```

---

## Next Steps

1. **Implement Core Logic**: Fill in `TODO` sections in each adapter
2. **Add Tests**: Create test suites for each plugin
3. **Documentation**: Add usage examples and format-specific docs
4. **Integration**: Test with real credential scenarios
5. **Performance**: Optimize proof generation and verification

---

## Plugin Architecture

All plugins follow the same pattern:

1. **ProofAdapter** - Implements format-specific operations
2. **ProofAdapterProvider** - Factory for adapter creation
3. **ServiceLoader** - Auto-discovery via META-INF/services
4. **Build Config** - Format-specific dependencies
5. **Capabilities** - Declared format features

This ensures:
- Consistent structure across all plugins
- Auto-discovery works seamlessly
- Easy to add new formats
- Format isolation (no cross-plugin dependencies)

