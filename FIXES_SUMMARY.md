# Build Fixes Summary

## Overview
Fixed compilation errors in test and example files after eliminating the `distribution/all` module and updating to use `com.trustweave.trust.TrustWeave` directly.

## Changes Made

### 1. Import Updates
- Changed `import com.trustweave.TrustWeave` → `import com.trustweave.trust.TrustWeave` in all example and test files

### 2. API Updates

#### TrustWeave Creation
- Changed `TrustWeave.create()` → `TrustWeave.build { ... }`
- Updated DSL syntax for configuration

#### DID Creation
- `createDid()` now returns `Did` type (not `DidDocument`)
- Changed `did.id` → `did.value` to access the DID string
- Added DID resolution where needed to get `DidDocument` with verification methods

#### Credential Issuance
- Replaced `IssuanceConfig` with DSL-based `issue { }` API
- Changed from:
  ```kotlin
  issueCredential(issuer, subject, config = IssuanceConfig(...), types = ...)
  ```
- To:
  ```kotlin
  issue {
      credential {
          type("Type1", "Type2")
          issuer(issuerDid)
          subject { ... }
          issued(Instant.now())
      }
      by(issuerDid = issuerDid, keyId = keyId)
  }
  ```

#### Wallet Creation
- Changed `createWallet(holderDid = ...)` → `wallet { holderDid(...) }`

#### Blockchain Configuration
- Updated from `blockchains { chainId to client }` to:
  ```kotlin
  anchor {
      chain(chainId) { ... }
  }
  ```
- Manually register clients when needed: `registry.register(chainId, client)`

### 3. Files Fixed

#### Example Files
- `distribution/examples/.../did-key/KeyDidExample.kt`
- `distribution/examples/.../did-jwk/JwkDidExample.kt`
- `distribution/examples/.../eo/EarthObservationExample.kt`
- `distribution/examples/.../indy/IndyIntegrationExample.kt`
- `distribution/examples/.../national/NationalEducationExample.kt`
- `distribution/examples/.../quickstart/QuickStartSample.kt`

#### Test Files
- `anchors/plugins/indy/src/test/.../IndyIntegrationScenarioTest.kt`

### 4. Remaining Issues

Some example files may still have issues with:
- Complex credential subject structures (JsonObject handling in DSL)
- Wallet API usage patterns
- DID resolution for key extraction

### 5. Next Steps

1. Run full build to identify remaining compilation errors
2. Fix any remaining API mismatches
3. Update documentation to reflect the new API patterns
4. Verify all examples compile and run correctly

