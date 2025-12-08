# VC-Only API Migration - Extension Function Updates

## Extension Functions Updated ✅

### 1. CredentialServiceExtensions.kt ✅

**Changes:**
- Updated `Credential.supportsSelectiveDisclosure()` → `VerifiableCredential.supportsSelectiveDisclosure()`
- Now works with `VerifiableCredential` and extracts format from proof

**Before:**
```kotlin
fun Credential.supportsSelectiveDisclosure(service: CredentialService): Boolean
```

**After:**
```kotlin
fun VerifiableCredential.supportsSelectiveDisclosure(service: CredentialService): Boolean
```

### 2. CredentialServicesExtensions.kt ✅

**Changes:**
- Updated `issueCredential()` to use VC types:
  - `CredentialFormat` → `CredentialFormatId`
  - `IssuerId` → `Issuer`
  - `SubjectId` → `CredentialSubject`
- Added convenience extensions for creating VC types:
  - `Did.asIssuer()` → creates `Issuer`
  - `Iri.asIssuer()` → creates `Issuer`
  - `Did.asCredentialSubject()` → creates `CredentialSubject`
  - `Iri.asCredentialSubject()` → creates `CredentialSubject`
- Kept useful infix operators for claim creation

**Before:**
```kotlin
suspend fun CredentialService.issueCredential(
    format: CredentialFormat,
    issuer: IssuerId,
    subject: SubjectId,
    claims: Claims,
    ...
)
```

**After:**
```kotlin
suspend fun CredentialService.issueCredential(
    format: CredentialFormatId,
    issuer: Issuer,
    credentialSubject: CredentialSubject,
    type: List<CredentialType>,
    ...
)
```

### 3. CredentialServiceDidExtensions.kt ✅

**Changes:**
- Updated all functions to use `VerifiableCredential` instead of `Credential`
- Updated `issueForDid()` to use VC types:
  - `IssuerId` → `Issuer.fromDid()`
  - `SubjectId` → `CredentialSubject.fromDid()`
  - Returns `IssuanceResult` instead of `Credential`
- Updated `resolveSubjectDid()` to extract DID from `CredentialSubject.id`
- Updated `verifyIssuerDid()` to extract DID from `Issuer.id`
- Updated `verifyWithDidResolution()` to work with `VerifiableCredential`

**Before:**
```kotlin
suspend fun CredentialService.issueForDid(
    ...
): Credential
```

**After:**
```kotlin
suspend fun CredentialService.issueForDid(
    ...
): IssuanceResult
```

### Files Still Needing Updates

1. **CredentialValidator.kt**
   - Still uses old `Credential` type
   - May be redundant if validation is covered in `CredentialService.verify()`
   - **Options:**
     - Update to use `VerifiableCredential`
     - Remove if redundant
     - Mark as deprecated

### Summary

- **Extension Functions**: 100% updated ✅
- **DID Extensions**: 100% updated ✅
- **Utility Validators**: Needs review ⏳

All core extension functions now work with the VC-only API using `VerifiableCredential`, `Issuer`, and `CredentialSubject`.

