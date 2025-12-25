# Terminology Audit

This document provides a comprehensive audit of terminology used across TrustWeave documentation and SDK to ensure consistency.

## Core Terminology Mapping

### Primary Entry Point

| Documentation Term | SDK Class | Status | Notes |
|-------------------|-----------|--------|-------|
| TrustWeave | `TrustWeave` | ✅ Consistent | Main facade class |
| TrustWeave facade | `TrustWeave` | ✅ Consistent | Alternative name |
| TrustWeave instance | `TrustWeave` | ✅ Consistent | Instance of main class |

**❌ Deprecated:** `TrustLayer` - All references have been updated to `TrustWeave`

### Credential Operations

| Documentation Term | SDK Class/Interface | Status | Notes |
|-------------------|---------------------|--------|-------|
| Credential Service | `CredentialService` | ✅ Consistent | Interface in credential-api |
| Issue credential | `issue { }` DSL | ✅ Consistent | DSL method on TrustWeave |
| Verify credential | `verify { }` DSL | ✅ Consistent | DSL method on TrustWeave |
| Verifiable Credential | `VerifiableCredential` | ✅ Consistent | W3C standard model |
| Verifiable Presentation | `VerifiablePresentation` | ✅ Consistent | W3C standard model |

### DID Operations

| Documentation Term | SDK Class/Interface | Status | Notes |
|-------------------|---------------------|--------|-------|
| DID | `Did` | ✅ Consistent | Type-safe identifier |
| DID Document | `DidDocument` | ✅ Consistent | W3C DID Document model |
| DID Method | `DidMethod` | ✅ Consistent | Interface for DID methods |
| Create DID | `createDid { }` DSL | ✅ Consistent | DSL method on TrustWeave |
| Resolve DID | `resolveDid()` | ✅ Consistent | Method on TrustWeave |
| DID Resolution Result | `DidResolutionResult` | ✅ Consistent | Sealed result type |

### Wallet Operations

| Documentation Term | SDK Class/Interface | Status | Notes |
|-------------------|---------------------|--------|-------|
| Wallet | `Wallet` | ✅ Consistent | Wallet interface |
| Create wallet | `wallet { }` DSL | ✅ Consistent | DSL method on TrustWeave |
| Wallet Factory | `WalletFactory` | ✅ Consistent | Factory interface |
| Wallet Creation Result | `WalletCreationResult` | ✅ Consistent | Sealed result type |

### Trust Operations

| Documentation Term | SDK Class/Interface | Status | Notes |
|-------------------|---------------------|--------|-------|
| Trust Registry | `TrustRegistry` | ✅ Consistent | Registry interface |
| Trust Anchor | `TrustAnchorMetadata` | ✅ Consistent | Metadata class |
| Trust Policy | `TrustPolicy` | ✅ Consistent | Policy interface |
| Add trust anchor | `addAnchor { }` DSL | ✅ Consistent | DSL method in trust block |
| Is trusted issuer | `isTrusted()` DSL | ✅ Consistent | DSL method in trust block |
| Trust path | `TrustPath` | ✅ Consistent | Path result type |

### Blockchain Operations

| Documentation Term | SDK Class/Interface | Status | Notes |
|-------------------|---------------------|--------|-------|
| Blockchain Service | `BlockchainService` | ✅ Consistent | Service class |
| Blockchain Anchor Client | `BlockchainAnchorClient` | ✅ Consistent | Client interface |
| Anchor data | `anchor()` | ✅ Consistent | Method on BlockchainService |
| Anchor Reference | `AnchorReference` | ✅ Consistent | Reference type |

### Key Management

| Documentation Term | SDK Class/Interface | Status | Notes |
|-------------------|---------------------|--------|-------|
| Key Management Service | `KeyManagementService` | ✅ Consistent | KMS interface |
| KMS | `KeyManagementService` | ✅ Consistent | Abbreviation acceptable |
| Key ID | `KeyId` | ✅ Consistent | Type-safe identifier |

### Configuration

| Documentation Term | SDK Class/Interface | Status | Notes |
|-------------------|---------------------|--------|-------|
| TrustWeave Configuration | `TrustWeaveConfig` | ✅ Consistent | Configuration class |
| TrustWeave Context | `TrustWeaveContext` | ✅ Consistent | DSL context class |
| Build TrustWeave | `TrustWeave.build { }` | ✅ Consistent | Factory method |

### Result Types

| Documentation Term | SDK Type | Status | Notes |
|-------------------|----------|--------|-------|
| DID Creation Result | `DidCreationResult` | ✅ Consistent | Sealed result type |
| Issuance Result | `IssuanceResult` | ✅ Consistent | Sealed result type |
| Verification Result | `VerificationResult` | ✅ Consistent | Sealed result type |
| Wallet Creation Result | `WalletCreationResult` | ✅ Consistent | Sealed result type |

## Naming Conventions

### ✅ Consistent Patterns

1. **Services**: All service interfaces end with `Service`
   - `CredentialService`
   - `KeyManagementService`
   - `BlockchainService`

2. **Factories**: All factory interfaces end with `Factory`
   - `WalletFactory`
   - `KmsFactory`
   - `DidMethodFactory`

3. **Registries**: All registry classes end with `Registry`
   - `TrustRegistry`
   - `DidMethodRegistry`
   - `BlockchainAnchorRegistry`

4. **Result Types**: All result types end with `Result`
   - `DidCreationResult`
   - `IssuanceResult`
   - `VerificationResult`

5. **Requests**: All request types end with `Request`
   - `IssuanceRequest`
   - `PresentationRequest`

6. **Options**: All options types end with `Options`
   - `VerificationOptions`
   - `DidCreationOptions`

7. **Builders**: All DSL builders end with `Builder`
   - `IssuanceBuilder`
   - `VerificationBuilder`
   - `CredentialBuilder`

## Abbreviations

### ✅ Acceptable Abbreviations

| Full Term | Abbreviation | Context |
|-----------|--------------|---------|
| Decentralized Identifier | DID | Standard industry term |
| Verifiable Credential | VC | When context is clear |
| Key Management Service | KMS | Standard industry term |
| Application Programming Interface | API | Standard term |
| Domain-Specific Language | DSL | Standard term |

### ❌ Avoid in Documentation

- Don't use abbreviations in titles or headings
- Don't use abbreviations without first defining the full term
- Don't use abbreviations in API method names (SDK already follows this)

## Terminology Consistency Checklist

### Documentation Files

- [x] `TrustLayer` → `TrustWeave` (completed)
- [x] Module names: `distribution-all`, `testkit` (completed)
- [x] Kotlin version: 2.2.21+ (completed)
- [x] API methods match SDK signatures (verified)
- [x] Class names match SDK exactly (verified)

### SDK Code

- [x] All classes follow naming conventions (verified)
- [x] All interfaces follow naming conventions (verified)
- [x] All methods follow Kotlin conventions (verified)
- [x] Result types are sealed classes (verified)

## Common Terminology Issues (Resolved)

### ✅ Resolved Issues

1. **`TrustLayer` → `TrustWeave`**
   - **Status**: ✅ Fixed in all documentation
   - **Files Updated**: All getting-started, API reference, and introduction docs

2. **Module Names**
   - **Status**: ✅ Standardized to `distribution-all` and `testkit`
   - **Files Updated**: All documentation files

3. **Kotlin Version**
   - **Status**: ✅ Standardized to 2.2.21+
   - **Files Updated**: Installation and getting-started guides

## Glossary Alignment

The [GLOSSARY.md](GLOSSARY.md) file should be updated to include:

- [x] Core TrustWeave terminology
- [x] DID terminology (W3C aligned)
- [x] Verifiable Credential terminology (W3C aligned)
- [x] Trust terminology
- [x] Wallet terminology
- [x] Blockchain terminology
- [x] Key Management terminology

## Recommendations

### For Documentation Writers

1. **Always use exact SDK class names** when referring to types
2. **Use `TrustWeave`** (never `TrustLayer`)
3. **Use `distribution-all`** for the main module 
4. **Use `testkit`** for testing utilities
5. **Define abbreviations** on first use
6. **Follow W3C terminology** for DID and VC terms
7. **Use sealed result types** correctly in examples

### For SDK Developers

1. **Follow naming conventions** strictly
2. **Use W3C standard terms** where applicable
3. **Maintain consistency** with existing patterns
4. **Document terminology** in KDoc
5. **Update glossary** when adding new terms

## Verification

To verify terminology consistency:

1. **Check documentation for SDK class names**:
   ```bash
   grep -r "TrustLayer\|trustweave-all\|distribution-all\|trustweave-common" docs/ --include="*.md"
   ```

2. **Check for correct module names**:
   ```bash
   grep -r "distribution-all\|testkit" docs/ --include="*.md"
   ```

3. **Verify Kotlin version**:
   ```bash
   grep -r "Kotlin.*2\.[0-9]" docs/ --include="*.md"
   ```

## Conclusion

✅ **Terminology is consistent** across documentation and SDK:

- All deprecated terms have been removed
- Module names are standardized
- API method names match SDK exactly
- Class names match SDK exactly
- Naming conventions are followed consistently
- W3C terminology is used correctly

The documentation accurately reflects the SDK's terminology and naming conventions.

