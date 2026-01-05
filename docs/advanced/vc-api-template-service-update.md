---
nav_exclude: true
---

# Template Service VC-Only API Update

## ✅ Update Complete

The `TemplateService` has been successfully updated to use VC-only API types.

### Changes Made

1. **CredentialFormat → CredentialFormatId**
   - Changed from `CredentialFormat` enum/data class to `CredentialFormatId` value class
   - Supports plugin-registered format IDs instead of hardcoded constants

2. **IssuerId → Issuer**
   - Changed from `IssuerId` to `Issuer` sealed class
   - Supports IRI-based issuers (DID, URI, URN) per W3C VC spec

3. **SubjectId → CredentialSubject**
   - Changed from `SubjectId` to `CredentialSubject` data class
   - Consolidates subject IRI and claims into single VC-aligned type
   - Supports IRI-based subjects (DID, URI, URN)

4. **API Simplification**
   - Removed separate `claims` parameter (now part of `CredentialSubject`)
   - Method signature now matches VC model more closely

### Updated Method Signature

**Before:**
```kotlin
suspend fun createIssuanceRequest(
    templateId: String,
    format: CredentialFormat,
    issuer: IssuerId,
    subject: SubjectId,
    claims: Claims,
    issuedAt: Instant = Instant.now(),
    validUntil: Instant? = null
): IssuanceRequest
```

**After:**
```kotlin
suspend fun createIssuanceRequest(
    templateId: String,
    format: CredentialFormatId,
    issuer: Issuer,
    credentialSubject: CredentialSubject,
    issuedAt: Instant = Instant.now(),
    validUntil: Instant? = null
): IssuanceRequest
```

### Example Usage

```kotlin
val template = CredentialTemplate(
    id = "person-credential",
    name = "Person Credential",
    schemaId = SchemaId("https://example.com/schemas/person"),
    type = listOf(CredentialType("VerifiableCredential"), CredentialType("Person")),
    defaultValidity = Duration.ofDays(365),
    requiredFields = listOf("name", "email")
)

val service = TemplateServices.default()
service.createTemplate(template)

// Issue credential from template
val issuerDid = Did("did:key:z6Mk...")
val subjectDid = Did("did:key:z6Mk...")

val claims = mapOf(
    "name" to JsonPrimitive("John Doe"),
    "email" to JsonPrimitive("john@example.com")
)

val request = service.createIssuanceRequest(
    templateId = "person-credential",
    format = CredentialFormatId("vc-ld"),
    issuer = Issuer.fromDid(issuerDid),
    credentialSubject = CredentialSubject.fromDid(subjectDid, claims = claims)
)
```

### Files Updated

- `TemplateService.kt` - Interface updated
- `DefaultTemplateService.kt` - Implementation updated

### Migration Status

- ✅ All template service types aligned with VC-only API
- ✅ No backward compatibility maintained (as requested)
- ✅ All linter errors resolved

