# VC-Only API Migration - Legacy Code Cleanup Summary

## Phase 7: Legacy Code Removal - IN PROGRESS

### Files Removed ✅

1. **Old Credential Model** ✅
   - `credentials/credential-api/src/main/kotlin/org.trustweave/credential/model/Credential.kt`
   - Old format-agnostic `Credential` class (replaced by `VerifiableCredential`)

2. **Old CredentialProof** ✅
   - `credentials/credential-api/src/main/kotlin/org.trustweave/credential/model/CredentialProof.kt`
   - Old format-agnostic proof (replaced by VC-specific `CredentialProof` sealed class)

3. **Old CredentialStatus** ✅
   - `credentials/credential-api/src/main/kotlin/org.trustweave/credential/model/CredentialStatus.kt`
   - Old status model (replaced by VC-specific `CredentialStatus` in `model.vc` package)

4. **EnvelopeSerialization Utility** ✅
   - `credentials/credential-api/src/main/kotlin/org.trustweave/credential/internal/util/EnvelopeSerialization.kt`
   - Old utility for serializing format-agnostic credentials (no longer needed)

### Files That Still Need Updates

These files still reference the old `Credential` class and need to be updated or removed:

1. **CredentialServiceExtensions.kt**
   - Uses old `Credential` class
   - Extension function `Credential.supportsSelectiveDisclosure()`
   - **Action**: Update to use `VerifiableCredential` or remove if redundant

2. **CredentialServicesExtensions.kt**
   - Uses old `Credential`, `IssuerId`, `SubjectId`, `CredentialFormat`
   - Extension function `CredentialService.issueCredential()` with old types
   - **Action**: Update to use VC types or remove if redundant

3. **CredentialValidator.kt**
   - Uses old `Credential` class
   - Validation functions for structure and proof
   - **Action**: Update to use `VerifiableCredential` or integrate into verification flow

4. **CredentialServiceDidExtensions.kt**
   - Uses old `Credential` class and old types (`IssuerId`, `SubjectId`, `CredentialFormat`)
   - Extension functions for DID-based operations
   - **Action**: Update to use VC types (`VerifiableCredential`, `Issuer`, `CredentialSubject`)

5. **TemplateService.kt** (and related)
   - May use old types
   - **Action**: Review and update if needed

### Notes

- The `Claims` typealias is still used and is valid (it's just a type alias for `Map<String, JsonElement>`)
- The old `CredentialStatus` in `model` package was a duplicate - VC model has its own `CredentialStatus`
- `EnvelopeSerialization` was only used by old proof adapters that needed to create `CredentialEnvelope` - no longer needed

### Remaining Work

1. Update extension functions to use `VerifiableCredential`
2. Update validator to use `VerifiableCredential`
3. Update DID extensions to use VC types
4. Remove any remaining references to old types (`IssuerId`, `SubjectId` if they're not needed)
5. Check and update template service if needed
6. Update test files that reference old `Credential` class

