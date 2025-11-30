# Pattern for Fixing Example Files

## Common Issues and Fixes

### 1. DID Creation
**OLD (WRONG):**
```kotlin
val did = trustweave.createDid()
println(did.id)  // ERROR: createDid() returns Did, not DidDocument
did.verificationMethod  // ERROR: Did doesn't have verificationMethod
```

**NEW (CORRECT):**
```kotlin
val did = trustweave.createDid()
println(did.value)  // Use .value to get DID string

// To get verification methods, resolve the DID:
val resolution = trustweave.resolveDid(did)
val didDoc = when (resolution) {
    is DidResolutionResult.Success -> resolution.document
    else -> throw IllegalStateException("Failed to resolve DID")
}
val keyId = didDoc.verificationMethod.first().id.substringAfter("#")
```

### 2. Credential Issuance
**OLD (WRONG):**
```kotlin
val credential = trustweave.issueCredential(
    issuer = issuerDid.id,
    subject = subjectMap,
    config = IssuanceConfig(...),
    types = listOf(...)
)
```

**NEW (CORRECT):**
```kotlin
val credential = trustweave.issue {
    credential {
        type("MyCredentialType")
        issuer(issuerDid.value)
        subject {
            id(subjectId)
            "key" to "value"
        }
        issued(Instant.now())
    }
    by(issuerDid = issuerDid.value, keyId = keyId)
}
```

### 3. Verification Result Properties
**Available properties:**
- `result.valid` - Boolean
- `result.proofValid` - Boolean  
- `result.issuerValid` - Boolean
- `result.notExpired` - Boolean
- `result.notRevoked` - Boolean
- `result.allWarnings` - List<String>
- `result.allErrors` - List<String>
- `result.warnings` - List<String>
- `result.errors` - List<String>

### 4. Wallet Creation
**OLD (WRONG):**
```kotlin
val wallet = trustweave.createWallet(holderDid = did.id)
```

**NEW (CORRECT):**
```kotlin
val wallet = trustweave.wallet {
    holderDid(did.value)
}
```

### 5. Import Statements
**OLD:**
```kotlin
import com.trustweave.TrustWeave
import com.trustweave.services.IssuanceConfig
```

**NEW:**
```kotlin
import com.trustweave.trust.TrustWeave
// IssuanceConfig removed - use DSL instead
```

## Files Needing Fixes

1. ✅ `did-key/KeyDidExample.kt` - Fixed
2. ✅ `did-jwk/JwkDidExample.kt` - Fixed  
3. ✅ `quickstart/QuickStartSample.kt` - Fixed
4. 🔄 `eo/EarthObservationExample.kt` - In progress
5. ⏳ `indy/IndyIntegrationExample.kt` - Pending
6. ⏳ `national/NationalEducationExample.kt` - Pending
7. ⏳ Test files - Pending

