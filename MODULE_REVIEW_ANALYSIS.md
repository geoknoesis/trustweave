# Module Review Analysis: Class Placement Review

## Summary
This document identifies classes that are misplaced and should be moved to their natural domain modules.

---

## 🔴 CRITICAL: Duplicate Common Classes

### Location: `credentials/core/trustweave-core/src/main/kotlin/com/trustweave/core/`

**Issue**: These are duplicates of classes already in `common/` module. They should be **DELETED**.

**Files to Delete**:
- `PluginRegistry.kt` → Already in `common/src/main/kotlin/com/trustweave/core/`
- `PluginMetadata.kt` → Already in `common/src/main/kotlin/com/trustweave/core/`
- `PluginConfiguration.kt` → Already in `common/src/main/kotlin/com/trustweave/core/`
- `ProviderChain.kt` → Already in `common/src/main/kotlin/com/trustweave/core/`
- `ResultExtensions.kt` → Already in `common/src/main/kotlin/com/trustweave/core/`
- `TrustWeaveException.kt` → Already in `common/src/main/kotlin/com/trustweave/core/`
- `TrustWeaveErrors.kt` → Already in `common/src/main/kotlin/com/trustweave/core/`
- `TrustWeaveConstants.kt` → Already in `common/src/main/kotlin/com/trustweave/core/`
- `types/ProofType.kt` → Already in `common/src/main/kotlin/com/trustweave/core/types/`
- `services/ServiceInitialization.kt` → Already in `common/src/main/kotlin/com/trustweave/core/services/`
- `Validation.kt` → **PARTIALLY** duplicate (DidValidator/ChainIdValidator in `common/`, CredentialValidator should stay)

**Action**: Delete entire `credentials/core/trustweave-core/src/main/kotlin/com/trustweave/core/` directory (except Validation.kt which should only have CredentialValidator).

---

## 🟡 PACKAGE NAMING ISSUES

### 1. Trust Module - Credential DSL Classes

**Location**: `trust/src/main/kotlin/com/trustweave/credential/dsl/`

**Issue**: All DSL classes are in `com.trustweave.credential.dsl` package but located in `trust` module.

**Files**:
- `CredentialDsl.kt`
- `CredentialLifecycleDsl.kt`
- `DelegationDsl.kt`
- `DidDocumentDsl.kt`
- `DidDsl.kt`
- `IssuanceDsl.kt`
- `KeyRotationDsl.kt`
- `PresentationDsl.kt`
- `RevocationDsl.kt`
- `SchemaDsl.kt`
- `TrustDsl.kt`
- `TrustLayerConfig.kt`
- `TrustLayerContext.kt`
- `TrustLayerExtensions.kt`
- `TypeSafeHelpers.kt`
- `VerificationDsl.kt`
- `WalletDsl.kt`
- `WalletOperationsDsl.kt`
- `WalletPresentationDsl.kt`
- `WalletQueryDsl.kt`

**Analysis**: 
- These are credential-domain DSLs but in trust module
- `TrustLayerConfig` and `TrustLayerContext` are orchestration layers that coordinate credentials, DID, KMS, chains
- **Decision**: These should stay in `trust` module but package should be `com.trustweave.trust.dsl` OR move to `credentials` module

**Recommendation**: 
- **Option A**: Move all credential DSLs to `credentials/core/trustweave-core/src/main/kotlin/com/trustweave/credential/dsl/`
- **Option B**: Keep in trust module but rename package to `com.trustweave.trust.dsl` (since trust is the orchestration layer)

**Preferred**: **Option A** - Move credential DSLs to credentials module. Trust module should only have trust registry DSLs.

---

### 2. Trust Module - Credential Services

**Location**: `trust/src/main/kotlin/com/trustweave/credential/services/`

**Files**:
- `TrustRegistryService.kt` → Should be `com.trustweave.trust.services.TrustRegistryService`
- `TrustRegistryServiceAdapter.kt` → Should be `com.trustweave.trust.services.TrustRegistryServiceAdapter`
- `TrustRegistryFactory.kt` → Should be `com.trustweave.trust.services.TrustRegistryFactory`

**Action**: Move to `trust/src/main/kotlin/com/trustweave/trust/services/` and update package names.

---

## 🟢 CORRECTLY PLACED (No Action Needed)

### Credentials Core Module

**Correctly placed** (credential-domain specific):
- `credential/models/` - Credential models (VerifiableCredential, VerifiablePresentation)
- `credential/issuer/` - CredentialIssuer
- `credential/verifier/` - CredentialVerifier
- `credential/proof/` - Proof generators and validators
- `credential/schema/` - Schema validators
- `credential/revocation/` - StatusListManager, BlockchainRevocationRegistry, AnchorStrategy
- `credential/anchor/` - CredentialAnchorService (credential-specific anchoring)
- `credential/did/` - Credential-DID integration (DidLinkedCredentialService, CredentialDidResolver)
- `credential/presentation/` - PresentationService
- `credential/template/` - CredentialTemplate
- `credential/transform/` - CredentialTransformer
- `credential/CredentialService.kt` - Credential service interface
- `credential/CredentialServiceRegistry.kt` - Credential service registry

### Trust Module

**Correctly placed**:
- `trust/TrustRegistry.kt` - Trust registry interface

### DID Module

**Correctly placed**:
- `did/DidMethod.kt` - DID method interface
- `did/DidMethodRegistry.kt` - DID method registry
- `did/DidModels.kt` - DID document models
- `did/services/` - DID service interfaces and adapters

### Wallet Module

**Correctly placed**:
- `wallet/Wallet.kt` - Wallet interface
- `wallet/CredentialStorage.kt` - Credential storage interface
- `wallet/CredentialOrganization.kt` - Organization interface
- `wallet/CredentialLifecycle.kt` - Lifecycle interface
- `wallet/CredentialPresentation.kt` - Presentation interface
- `wallet/services/` - WalletFactory, WalletCreationOptions

### Chains Module

**Correctly placed**:
- `anchor/BlockchainAnchorClient.kt` - Anchor client interface
- `anchor/BlockchainAnchorRegistry.kt` - Anchor registry
- `anchor/services/` - BlockchainAnchorClientFactory

### Contract Module

**Correctly placed**:
- `contract/SmartContractService.kt` - Smart contract service
- `contract/models/` - Contract models
- `contract/evaluation/` - Contract evaluation engines

### Common Module

**Correctly placed**:
- `core/TrustWeaveException.kt` - Base exceptions
- `core/TrustWeaveErrors.kt` - Structured errors
- `core/TrustWeaveConstants.kt` - Constants
- `core/PluginRegistry.kt` - Plugin registry
- `core/PluginMetadata.kt` - Plugin metadata
- `core/PluginConfiguration.kt` - Plugin configuration
- `core/ProviderChain.kt` - Provider chain
- `core/ResultExtensions.kt` - Result utilities
- `core/Validation.kt` - Common validation (DidValidator, ChainIdValidator)
- `core/types/ProofType.kt` - Proof type enum
- `core/services/ServiceInitialization.kt` - Service initialization

---

## 📋 ACTION ITEMS

### Priority 1: Delete Duplicates
1. Delete `credentials/core/trustweave-core/src/main/kotlin/com/trustweave/core/` directory
2. Update `credentials/core/trustweave-core/src/main/kotlin/com/trustweave/core/Validation.kt` to only contain `CredentialValidator` (remove DidValidator/ChainIdValidator)

### Priority 2: Fix Package Names
1. Move `trust/src/main/kotlin/com/trustweave/credential/services/` → `trust/src/main/kotlin/com/trustweave/trust/services/`
2. Update package declarations in those files

### Priority 3: Consider Moving Credential DSLs
1. **Decision needed**: Should credential DSLs stay in `trust` module (orchestration layer) or move to `credentials` module?
2. If moving: Move `trust/src/main/kotlin/com/trustweave/credential/dsl/` → `credentials/core/trustweave-core/src/main/kotlin/com/trustweave/credential/dsl/`
3. If staying: Rename package to `com.trustweave.trust.dsl`

---

## 🎯 RECOMMENDED STRUCTURE

```
common/                          # Root-level common (exceptions, SPI, utilities)
├── src/main/kotlin/com/trustweave/core/
    ├── TrustWeaveException.kt
    ├── TrustWeaveErrors.kt
    ├── TrustWeaveConstants.kt
    ├── PluginRegistry.kt
    ├── PluginMetadata.kt
    ├── PluginConfiguration.kt
    ├── ProviderChain.kt
    ├── ResultExtensions.kt
    ├── Validation.kt (DidValidator, ChainIdValidator)
    ├── types/ProofType.kt
    └── services/ServiceInitialization.kt

credentials/core/trustweave-core/
├── src/main/kotlin/com/trustweave/
    ├── credential/              # Credential domain
    │   ├── models/
    │   ├── issuer/
    │   ├── verifier/
    │   ├── proof/
    │   ├── schema/
    │   ├── revocation/
    │   ├── anchor/
    │   ├── did/
    │   ├── presentation/
    │   ├── template/
    │   ├── transform/
    │   ├── dsl/                 # ← MOVE FROM trust/ (if decided)
    │   ├── CredentialService.kt
    │   └── CredentialServiceRegistry.kt
    └── core/
        └── Validation.kt        # Only CredentialValidator

trust/
├── src/main/kotlin/com/trustweave/
    ├── trust/                   # Trust registry domain
    │   ├── TrustRegistry.kt
    │   └── services/
    │       ├── TrustRegistryService.kt
    │       ├── TrustRegistryServiceAdapter.kt
    │       └── TrustRegistryFactory.kt
    └── trust/dsl/              # Trust-specific DSLs (if any)

did/trustweave-did/             # DID domain
kms/trustweave-kms/             # KMS domain
wallet/trustweave-wallet/       # Wallet domain
chains/trustweave-anchor/       # Blockchain anchor domain
contract/                       # Smart contract domain
```

---

## ❓ QUESTIONS FOR DECISION

1. **Credential DSLs in trust module**: Should `TrustLayerConfig` and credential DSLs stay in `trust` module (as orchestration layer) or move to `credentials` module?
   - **Recommendation**: Move to `credentials` module since they're credential-domain specific. Trust module should only have trust registry functionality.

2. **TrustLayerConfig location**: If credential DSLs move, where should `TrustLayerConfig` go?
   - **Option A**: Move to `credentials` module (it's credential orchestration)
   - **Option B**: Keep in `trust` module but rename to `TrustOrchestrationConfig`
   - **Recommendation**: Option A - Move to credentials module

