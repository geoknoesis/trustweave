---
title: TrustWeave Multi-Domain No-Code SaaS Platform - Design Document
---

# TrustWeave Multi-Domain No-Code SaaS Platform - Design Document

> **Comprehensive design for a domain-agnostic, no-code SaaS platform supporting unlimited domains through schema templates and visual builders**

**Version:** 1.0  
**Last Updated:** 2025-01-15  
**Status:** Design Phase

---

## Executive Summary

This document outlines the design for TrustWeave's multi-domain SaaS platform that enables organizations across any industry to issue and manage verifiable credentials and DIDs without writing code. The platform uses schema templates, visual builders, and domain-agnostic architecture to support unlimited use cases.

### Core Design Principles

1. **Domain-Agnostic Architecture**: No hardcoded domain logic; all domains supported through configuration
2. **Schema Template System**: Pre-built and custom schemas for any credential type
3. **No-Code First**: Visual interfaces for all operations; code optional for advanced users
4. **Template Marketplace**: Share and discover templates across organizations
5. **Progressive Disclosure**: Simple defaults, advanced options available when needed

---

## 1. Architecture Overview

### 1.1 System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Multi-Domain SaaS Platform                 │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │         Domain-Agnostic Core Layer                    │   │
│  │  - Schema Template Engine                             │   │
│  │  - Visual Builder Framework                           │   │
│  │  - No-Code Workflow Engine                            │   │
│  │  - Domain Registry                                    │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                    │
│  ┌───────────────────────┼───────────────────────────────┐   │
│  │                       │                               │   │
│  ┌───────────▼──────────┐ ┌───────────▼──────────┐      │   │
│  │  No-Code DID        │ │  No-Code Credential  │      │   │
│  │  Management         │ │  Management          │      │   │
│  │  - Visual DID       │ │  - Visual Issuance  │      │   │
│  │    Builder          │ │  - Template-Based   │      │   │
│  │  - Method Selector  │ │  - Schema Validator │      │   │
│  │  - Key Manager      │ │  - Bulk Operations  │      │   │
│  └─────────────────────┘ └──────────────────────┘      │   │
│                                                           │   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │         Schema Template System                        │   │
│  │  - Pre-built Domain Templates                        │   │
│  │  - Custom Schema Builder                             │   │
│  │  - Template Marketplace                              │   │
│  │  - Schema Versioning                                │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │         TrustWeave Core SDK                          │   │
│  │  - DID Operations                                    │   │
│  │  - Credential Issuance                               │   │
│  │  - Verification                                      │   │
│  │  - Blockchain Anchoring                              │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 Domain Support Model

**Domain-Agnostic Design:**
- No hardcoded domain logic in core platform
- All domain-specific behavior via templates and schemas
- Unlimited domains supported through configuration
- Domain discovery via template marketplace

**Supported Domains (Examples):**
- Education (degrees, transcripts, certificates)
- Healthcare (licenses, certifications, credentials)
- Finance (KYC, compliance, professional licenses)
- Supply Chain (certifications, quality marks, origin)
- Government (IDs, licenses, permits)
- Professional (certifications, memberships, skills)
- Employment (background checks, references, skills)
- Custom (any domain via custom templates)

---

## 2. Schema Template System

### 2.1 Template Architecture

**Template Components:**

```kotlin
data class CredentialSchemaTemplate(
    // Template Identity
    val id: String,                    // Unique template ID
    val name: String,                  // Human-readable name
    val description: String,            // Template description
    val domain: DomainType,            // Domain category
    val version: String,               // Template version
    
    // Schema Definition
    val schemaId: SchemaId,            // W3C Schema ID
    val schemaDefinition: JsonObject,  // JSON Schema definition
    val schemaFormat: SchemaFormat,    // JSON_SCHEMA or SHACL
    
    // Credential Structure
    val credentialTypes: List<String>, // VC types
    val requiredFields: List<FieldDefinition>,
    val optionalFields: List<FieldDefinition>,
    val fieldValidations: Map<String, ValidationRule>,
    
    // Defaults
    val defaultValidityDays: Int?,     // Default expiration
    val defaultIssuerDid: String?,     // Default issuer (optional)
    val defaultProofType: String,      // Default proof type
    
    // UI Configuration
    val uiConfig: TemplateUIConfig,    // Form builder config
    val fieldLabels: Map<String, String>, // Field display names
    val fieldHelpText: Map<String, String>, // Field help text
    val fieldOrder: List<String>,      // Form field order
    
    // Metadata
    val tags: List<String>,            // Searchable tags
    val category: String,              // Template category
    val author: String,                // Template author
    val marketplace: Boolean,           // Available in marketplace
    val createdAt: Instant,
    val updatedAt: Instant
)

data class FieldDefinition(
    val name: String,                  // Field name (JSON path)
    val label: String,                 // Display label
    val type: FieldType,                // String, Number, Date, Boolean, Object, Array
    val required: Boolean,              // Is field required?
    val defaultValue: JsonElement?,    // Default value
    val validation: ValidationRule?,   // Validation rules
    val helpText: String?,             // Help text
    val placeholder: String?,          // Placeholder text
    val options: List<FieldOption>?    // For select/dropdown fields
)

enum class FieldType {
    STRING, NUMBER, INTEGER, BOOLEAN, DATE, DATETIME,
    EMAIL, URL, PHONE, OBJECT, ARRAY, SELECT, MULTI_SELECT
}

data class ValidationRule(
    val minLength: Int?,
    val maxLength: Int?,
    val minValue: Number?,
    val maxValue: Number?,
    val pattern: String?,              // Regex pattern
    val enumValues: List<String>?,      // Allowed values
    val customValidator: String?       // Custom validation function
)

data class TemplateUIConfig(
    val formLayout: FormLayout,        // Single column, two column, etc.
    val sections: List<FormSection>,    // Grouped fields
    val conditionalLogic: List<ConditionalRule>?, // Show/hide fields
    val customCSS: String?             // Custom styling
)
```

### 2.2 Pre-Built Domain Templates

**Education Domain Templates:**

```kotlin
// Degree Credential Template
val degreeTemplate = CredentialSchemaTemplate(
    id = "education-degree-v1",
    name = "University Degree",
    domain = DomainType.EDUCATION,
    credentialTypes = listOf("VerifiableCredential", "DegreeCredential"),
    requiredFields = listOf(
        FieldDefinition("degree.type", "Degree Type", FieldType.SELECT, true,
            options = listOf("Bachelor", "Master", "Doctorate")),
        FieldDefinition("degree.name", "Degree Name", FieldType.STRING, true),
        FieldDefinition("degree.university", "University", FieldType.STRING, true),
        FieldDefinition("degree.graduationDate", "Graduation Date", FieldType.DATE, true)
    ),
    optionalFields = listOf(
        FieldDefinition("degree.major", "Major", FieldType.STRING, false),
        FieldDefinition("degree.gpa", "GPA", FieldType.NUMBER, false,
            validation = ValidationRule(minValue = 0.0, maxValue = 4.0)),
        FieldDefinition("degree.honors", "Honors", FieldType.STRING, false)
    ),
    defaultValidityDays = 3650, // 10 years
    uiConfig = TemplateUIConfig(
        sections = listOf(
            FormSection("Degree Information", listOf("degree.type", "degree.name", "degree.university")),
            FormSection("Academic Details", listOf("degree.graduationDate", "degree.major", "degree.gpa"))
        )
    )
)

// Transcript Credential Template
val transcriptTemplate = CredentialSchemaTemplate(
    id = "education-transcript-v1",
    name = "Academic Transcript",
    domain = DomainType.EDUCATION,
    credentialTypes = listOf("VerifiableCredential", "TranscriptCredential"),
    requiredFields = listOf(
        FieldDefinition("transcript.university", "University", FieldType.STRING, true),
        FieldDefinition("transcript.courses", "Courses", FieldType.ARRAY, true)
    ),
    // ... more fields
)
```

**Healthcare Domain Templates:**

```kotlin
// Medical License Template
val medicalLicenseTemplate = CredentialSchemaTemplate(
    id = "healthcare-medical-license-v1",
    name = "Medical License",
    domain = DomainType.HEALTHCARE,
    credentialTypes = listOf("VerifiableCredential", "MedicalLicenseCredential"),
    requiredFields = listOf(
        FieldDefinition("license.licenseNumber", "License Number", FieldType.STRING, true),
        FieldDefinition("license.specialty", "Medical Specialty", FieldType.SELECT, true),
        FieldDefinition("license.issuingBoard", "Issuing Board", FieldType.STRING, true),
        FieldDefinition("license.issueDate", "Issue Date", FieldType.DATE, true),
        FieldDefinition("license.expirationDate", "Expiration Date", FieldType.DATE, true)
    ),
    defaultValidityDays = 365, // 1 year
    // ... more config
)
```

**Finance Domain Templates:**

```kotlin
// KYC Credential Template
val kycTemplate = CredentialSchemaTemplate(
    id = "finance-kyc-v1",
    name = "Know Your Customer",
    domain = DomainType.FINANCE,
    credentialTypes = listOf("VerifiableCredential", "KYCCredential"),
    requiredFields = listOf(
        FieldDefinition("kyc.verificationLevel", "Verification Level", FieldType.SELECT, true,
            options = listOf("Basic", "Enhanced", "Full")),
        FieldDefinition("kyc.verifiedAt", "Verification Date", FieldType.DATETIME, true),
        FieldDefinition("kyc.verifierDid", "Verifier DID", FieldType.STRING, true)
    ),
    // ... more config
)
```

### 2.3 Custom Schema Builder (No-Code)

**Visual Schema Builder Interface:**

```
┌─────────────────────────────────────────────────────────┐
│  Create Custom Credential Schema                         │
│                                                           │
│  Step 1: Basic Information                               │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Schema Name: [________________________]             │ │
│  │ Description: [________________________]             │ │
│  │ Domain: [Education ▼]                               │ │
│  │ Credential Types: [DegreeCredential, ...]           │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  Step 2: Define Fields (Drag & Drop)                     │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Available Field Types:                              │ │
│  │ [Text] [Number] [Date] [Select] [Email] [URL]       │ │
│  │                                                       │ │
│  │ Fields:                                               │ │
│  │ ┌─────────────────────────────────────────────────┐ │ │
│  │ │ 📝 degree.name (Text) - Required                │ │ │
│  │ │    Label: "Degree Name"                         │ │ │
│  │ │    [Edit] [Delete]                              │ │ │
│  │ └─────────────────────────────────────────────────┘ │ │
│  │ ┌─────────────────────────────────────────────────┐ │ │
│  │ │ 📅 degree.graduationDate (Date) - Required      │ │ │
│  │ │    Label: "Graduation Date"                     │ │ │
│  │ │    [Edit] [Delete]                              │ │ │
│  │ └─────────────────────────────────────────────────┘ │ │
│  │                                                       │ │
│  │ [+ Add Field]                                        │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  Step 3: Validation Rules                                 │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Field: degree.gpa                                   │ │
│  │ ☑ Required                                          │ │
│  │ ☑ Number between [0] and [4.0]                     │ │
│  │ ☐ Must match pattern: [____________]               │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  [← Back]  [Preview Schema]  [Save Template]             │
└─────────────────────────────────────────────────────────┘
```

**Schema Builder API:**

```kotlin
interface SchemaBuilderService {
    // Create template from visual builder
    suspend fun createTemplateFromBuilder(
        builderData: SchemaBuilderData
    ): CredentialSchemaTemplate
    
    // Validate schema definition
    suspend fun validateSchema(
        schemaDefinition: JsonObject
    ): SchemaValidationResult
    
    // Generate JSON Schema from field definitions
    suspend fun generateJsonSchema(
        fields: List<FieldDefinition>
    ): JsonObject
    
    // Preview template form
    suspend fun previewTemplateForm(
        templateId: String
    ): FormPreview
}
```

---

## 3. No-Code Credential Management

### 3.1 Visual Credential Issuance

**No-Code Credential Issuance Flow:**

```
┌─────────────────────────────────────────────────────────┐
│  Issue Credential (No-Code)                              │
│                                                           │
│  Step 1: Select Template                                 │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Search: [________________]  Filter: [All Domains ▼]│ │
│  │                                                       │ │
│  │ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐    │ │
│  │ │ 🎓 Degree   │ │ 🏥 Medical   │ │ 💼 KYC       │    │ │
│  │ │ Credential  │ │ License     │ │ Credential  │    │ │
│  │ │             │ │             │ │             │    │ │
│  │ │ Education   │ │ Healthcare  │ │ Finance      │    │ │
│  │ │ [Select]    │ │ [Select]    │ │ [Select]    │    │ │
│  │ └─────────────┘ └─────────────┘ └─────────────┘    │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  Step 2: Fill Credential Data (Auto-generated Form)       │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Issuer: [Your Organization DID ▼]                   │ │
│  │                                                       │ │
│  │ Subject (Recipient):                                 │ │
│  │ ┌─────────────────────────────────────────────────┐ │ │
│  │ │ DID: [did:key:z6Mk...] [Verify] [Create New]   │ │ │
│  │ └─────────────────────────────────────────────────┘ │ │
│  │                                                       │ │
│  │ Degree Information:                                   │ │
│  │ ┌─────────────────────────────────────────────────┐ │ │
│  │ │ Degree Type: [Bachelor ▼]                       │ │ │
│  │ │ Degree Name: [Bachelor of Science...]           │ │ │
│  │ │ University: [State University]                  │ │ │
│  │ │ Graduation Date: [2023-05-15] 📅                │ │ │
│  │ │ Major: [Computer Science] (optional)            │ │ │
│  │ │ GPA: [3.8] (optional, 0.0-4.0)                 │ │ │
│  │ └─────────────────────────────────────────────────┘ │ │
│  │                                                       │ │
│  │ Validity:                                            │ │
│  │ ┌─────────────────────────────────────────────────┐ │ │
│  │ │ ☑ Valid for 10 years (default)                 │ │ │
│  │ │ ☐ Custom: [____] years                          │ │ │
│  │ └─────────────────────────────────────────────────┘ │ │
│  │                                                       │ │
│  │ Options:                                             │ │
│  │ ┌─────────────────────────────────────────────────┐ │ │
│  │ │ ☐ Anchor to blockchain                          │ │ │
│  │ │ ☑ Add to trust registry                         │ │ │
│  │ │ ☐ Enable revocation                             │ │ │
│  │ └─────────────────────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  [← Back]  [Preview Credential]  [Issue Credential]       │
└─────────────────────────────────────────────────────────┘
```

**Bulk Issuance (No-Code):**

```
┌─────────────────────────────────────────────────────────┐
│  Bulk Issue Credentials                                  │
│                                                           │
│  Template: [University Degree ▼]                        │
│                                                           │
│  Upload CSV File:                                        │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ [📁 Drag & drop CSV file here]                      │ │
│  │ or [Browse Files]                                   │ │
│  │                                                       │ │
│  │ Sample format:                                       │ │
│  │ subjectDid,degree.type,degree.name,university,...   │ │
│  │ [Download Sample CSV]                               │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  Preview (First 5 rows):                                  │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ ✅ Row 1: did:key:z6Mk..., Bachelor, B.S. CS...    │ │
│  │ ✅ Row 2: did:key:z6Mk..., Master, M.S. Eng...      │ │
│  │ ✅ Row 3: did:key:z6Mk..., Bachelor, B.A. Econ...  │ │
│  │ ⚠️  Row 4: Missing required field "degree.name"     │ │
│  │ ✅ Row 5: did:key:z6Mk..., Doctorate, Ph.D. Bio... │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  Summary: 4 valid, 1 error                               │
│                                                           │
│  [Fix Errors]  [Issue 4 Valid Credentials]               │
└─────────────────────────────────────────────────────────┘
```

### 3.2 Credential Management Dashboard

**No-Code Credential Dashboard:**

```
┌─────────────────────────────────────────────────────────┐
│  Credentials Dashboard                                   │
│                                                           │
│  Filters: [All Types ▼] [All Status ▼] [Date Range 📅]  │
│  Search: [Search credentials...]                          │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ 📊 Statistics                                       │ │
│  │ Total: 1,234  Valid: 1,200  Expired: 30  Revoked: 4 │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  Credentials List:                                       │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ 🎓 Degree Credential                                │ │
│  │    Subject: did:key:z6Mk...                         │ │
│  │    Issued: 2023-05-15  Valid until: 2033-05-15    │ │
│  │    [View] [Download] [Revoke] [Share]              │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │ 🎓 Degree Credential                                │ │
│  │    Subject: did:key:z6Mk...                         │ │
│  │    Issued: 2023-06-01  Valid until: 2033-06-01    │ │
│  │    [View] [Download] [Revoke] [Share]              │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  [+ Issue New Credential]  [📥 Bulk Import]  [📤 Export] │
└─────────────────────────────────────────────────────────┘
```

### 3.3 Credential API (No-Code)

**REST API for No-Code Operations:**

```kotlin
// Issue credential from template
POST /v1/credentials/issue-from-template
{
  "templateId": "education-degree-v1",
  "issuerDid": "did:key:z6Mk...",
  "subjectDid": "did:key:z6Mk...",
  "fieldValues": {
    "degree.type": "Bachelor",
    "degree.name": "Bachelor of Science in Computer Science",
    "degree.university": "State University",
    "degree.graduationDate": "2023-05-15",
    "degree.major": "Computer Science",
    "degree.gpa": "3.8"
  },
  "options": {
    "validityYears": 10,
    "anchorToBlockchain": false,
    "addToTrustRegistry": true
  }
}

// Bulk issue from CSV
POST /v1/credentials/bulk-issue
{
  "templateId": "education-degree-v1",
  "issuerDid": "did:key:z6Mk...",
  "csvData": "...", // Base64 encoded CSV
  "options": { ... }
}

// List credentials with filters
GET /v1/credentials?templateId=education-degree-v1&status=valid&limit=50

// Revoke credential
POST /v1/credentials/{credentialId}/revoke
{
  "reason": "Degree revoked due to academic misconduct"
}
```

---

## 4. No-Code DID Management

### 4.1 Visual DID Builder

**No-Code DID Creation:**

```
┌─────────────────────────────────────────────────────────┐
│  Create Decentralized Identifier (DID)                  │
│                                                           │
│  Step 1: Choose DID Method                               │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐    │ │
│  │ │ did:key     │ │ did:web     │ │ did:ion     │    │ │
│  │ │             │ │             │ │             │    │ │
│  │ │ ✅ Simple   │ │ 🌐 Web-based│ │ 🔗 Bitcoin  │    │ │
│  │ │ ✅ Fast     │ │ ✅ Domain   │ │ ✅ Anchored │    │ │
│  │ │ ✅ Portable │ │   control  │ │ ✅ Immutable│    │ │
│  │ │             │ │             │ │             │    │ │
│  │ │ [Select]    │ │ [Select]    │ │ [Select]    │    │ │
│  │ └─────────────┘ └─────────────┘ └─────────────┘    │ │
│  │                                                       │ │
│  │ ℹ️  did:key is recommended for most use cases        │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  Step 2: Configure DID                                   │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ Display Name: [My Organization DID]                 │ │
│  │ Description: [Optional description...]              │ │
│  │                                                       │ │
│  │ Key Algorithm: [Ed25519 ▼]                          │ │
│  │   ℹ️  Ed25519 is recommended (fast, secure)           │ │
│  │                                                       │ │
│  │ Key Purposes:                                        │ │
│  │   ☑ Authentication                                   │ │
│  │   ☑ Assertion Method (for signing credentials)       │ │
│  │   ☐ Key Agreement                                    │ │
│  │   ☐ Capability Invocation                            │ │
│  │                                                       │ │
│  │ Advanced Options: [Show Advanced ▼]                 │ │
│  │   ☐ Add service endpoints                            │ │
│  │   ☐ Add additional verification methods                │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  [← Back]  [Create DID]                                  │
└─────────────────────────────────────────────────────────┘
```

**DIDs Management Dashboard (Multiple DIDs Per Tenant):**

```
┌─────────────────────────────────────────────────────────┐
│  DIDs Dashboard                                           │
│                                                           │
│  [+ Create New DID]  [📥 Import DIDs]  [📤 Export]      │
│                                                           │
│  Filters: [All Methods ▼] [All Projects ▼] [All Status ▼]│
│  Search: [Search DIDs...]                                 │
│                                                           │
│  📊 Statistics: 12 DIDs total (10 active, 2 deactivated) │
│                                                           │
│  My DIDs:                                                 │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ 🔑 did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnn│ │
│  │    Name: "University Issuer DID"                    │ │
│  │    Method: did:key  Algorithm: Ed25519              │ │
│  │    Project: Education Credentials                   │ │
│  │    Purpose: Issuer                                  │ │
│  │    Created: 2024-01-15  Status: ✅ Active           │ │
│  │    Credentials Issued: 1,234                        │ │
│  │    [View Details] [Issue Credential] [Export]       │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │ 🔗 did:web:university.edu                           │ │
│  │    Name: "University Web DID"                        │ │
│  │    Method: did:web  Domain: university.edu         │ │
│  │    Project: Education Credentials                   │ │
│  │    Purpose: Issuer                                  │ │
│  │    Created: 2024-01-20  Status: ✅ Active           │ │
│  │    Credentials Issued: 567                          │ │
│  │    [View Details] [Update] [Export]                 │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │ 🔗 did:ion:EiClkZMDnmYGhX8tR8i3z2b5M5fN5hJ5vK5xL5yM│ │
│  │    Name: "Blockchain-Anchored DID"                   │ │
│  │    Method: did:ion  Algorithm: secp256k1            │ │
│  │    Project: Healthcare Credentials                   │ │
│  │    Purpose: Issuer                                  │ │
│  │    Created: 2024-02-01  Status: ✅ Active           │ │
│  │    Credentials Issued: 89                            │ │
│  │    [View Details] [Issue Credential] [Export]       │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  Recent Activity:                                         │
│  • Created did:ion:EiClk... (2 hours ago)                │
│  • Issued 5 credentials with did:key:z6Mk... (1 day ago)  │
│  • Deactivated did:web:old.university.edu (3 days ago)    │
└─────────────────────────────────────────────────────────┘
```

**Key Features:**
- ✅ **Unlimited DIDs**: No limit on number of DIDs per organization
- ✅ **Project Association**: DIDs can be associated with specific projects
- ✅ **Purpose Tagging**: Tag DIDs by purpose (issuer, verifier, holder)
- ✅ **Method Diversity**: Mix different DID methods (key, web, ion, etc.)
- ✅ **Filtering**: Filter by method, project, purpose, status
- ✅ **Statistics**: Track credentials issued per DID

### 4.2 DID Operations (No-Code)

**Visual DID Operations:**

```kotlin
// No-code DID creation API
POST /v1/dids/create
{
  "method": "key",  // or "web", "ion", etc.
  "displayName": "University Issuer DID",
  "description": "DID for issuing university credentials",
  "algorithm": "Ed25519",
  "keyPurposes": ["authentication", "assertionMethod"],
  "options": {
    "addServiceEndpoints": false
  }
}

// Response
{
  "did": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
  "didDocument": { ... },
  "keyId": "key-1",
  "createdAt": "2024-01-15T10:30:00Z"
}

// Resolve DID (no-code)
GET /v1/dids/{did}/resolve

// Update DID (no-code)
PUT /v1/dids/{did}
{
  "displayName": "Updated Name",
  "serviceEndpoints": [
    {
      "id": "service-1",
      "type": "CredentialRevocation",
      "serviceEndpoint": "https://university.edu/revocation"
    }
  ]
}

// Deactivate DID
POST /v1/dids/{did}/deactivate
{
  "reason": "No longer in use"
}
```

---

## 5. Domain Registry & Template Marketplace

### 5.1 Domain Registry

**Domain Type Registry:**

```kotlin
enum class DomainType {
    EDUCATION,          // Academic credentials
    HEALTHCARE,         // Medical licenses, certifications
    FINANCE,            // KYC, compliance, licenses
    SUPPLY_CHAIN,       // Certifications, quality marks
    GOVERNMENT,         // IDs, licenses, permits
    PROFESSIONAL,       // Certifications, memberships
    EMPLOYMENT,         // Background checks, references
    CUSTOM              // User-defined domains
}

data class DomainRegistry(
    val domainType: DomainType,
    val name: String,
    val description: String,
    val icon: String,                    // Icon identifier
    val defaultTemplates: List<String>,  // Template IDs
    val schemaExamples: List<String>,     // Example schema IDs
    val useCases: List<String>,          // Common use cases
    val industries: List<String>         // Related industries
)
```

### 5.2 Template Marketplace

**Template Marketplace Features:**

- **Public Templates**: Pre-built templates available to all users
- **Organization Templates**: Private templates within organization
- **Shared Templates**: Templates shared between organizations
- **Template Ratings**: User ratings and reviews
- **Template Usage Stats**: How many credentials issued from template
- **Template Versioning**: Multiple versions, migration support

**Template Marketplace UI:**

```
┌─────────────────────────────────────────────────────────┐
│  Template Marketplace                                     │
│                                                           │
│  Browse: [All Domains ▼]  Search: [Search templates...] │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ 🎓 University Degree                                 │ │
│  │    Education • 1,234 credentials issued               │ │
│  │    ⭐⭐⭐⭐⭐ (4.8) 234 reviews                        │ │
│  │    By: TrustWeave Team                               │ │
│  │    [Preview] [Use Template] [View Schema]            │ │
│  ├─────────────────────────────────────────────────────┤ │
│  │ 🏥 Medical License                                   │ │
│  │    Healthcare • 567 credentials issued              │ │
│  │    ⭐⭐⭐⭐ (4.2) 89 reviews                          │ │
│  │    By: Healthcare Authority                          │ │
│  │    [Preview] [Use Template] [View Schema]            │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  My Templates:                                            │
│  ┌─────────────────────────────────────────────────────┐ │
│  │ 📝 Custom Employee Credential                      │ │
│  │    Custom • 45 credentials issued                   │ │
│  │    [Edit] [Share] [Delete]                          │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  [+ Create Custom Template]                              │
└─────────────────────────────────────────────────────────┘
```

---

## 6. API Design

### 6.1 Schema Template API

```kotlin
// Template Management
POST   /v1/templates                    // Create template
GET    /v1/templates                    // List templates
GET    /v1/templates/{templateId}      // Get template
PUT    /v1/templates/{templateId}       // Update template
DELETE /v1/templates/{templateId}      // Delete template

// Template Marketplace
GET    /v1/templates/marketplace        // Browse marketplace
POST   /v1/templates/{templateId}/share // Share template
GET    /v1/templates/{templateId}/usage // Get usage stats

// Schema Builder
POST   /v1/templates/builder/validate   // Validate schema
POST   /v1/templates/builder/preview   // Preview form
POST   /v1/templates/builder/generate  // Generate from builder
```

### 6.2 No-Code Credential API

```kotlin
// Issue from template
POST   /v1/credentials/issue-from-template
POST   /v1/credentials/bulk-issue

// Credential management
GET    /v1/credentials                  // List credentials
GET    /v1/credentials/{id}            // Get credential
POST   /v1/credentials/{id}/revoke     // Revoke credential
POST   /v1/credentials/{id}/verify     // Verify credential
GET    /v1/credentials/{id}/download   // Download as JSON/PDF
```

### 6.3 No-Code DID API

```kotlin
// DID creation
POST   /v1/dids/create                 // Create DID (no-code)
GET    /v1/dids                        // List DIDs
GET    /v1/dids/{did}                  // Get DID details
PUT    /v1/dids/{did}                  // Update DID
POST   /v1/dids/{did}/deactivate       // Deactivate DID
GET    /v1/dids/{did}/resolve         // Resolve DID document
```

---

## 7. Implementation Phases

### Phase 1: Core Template System (Months 1-2)
- [ ] Schema template data model
- [ ] Template storage and retrieval
- [ ] Pre-built templates for 5 domains
- [ ] Template validation

### Phase 2: No-Code Credential Issuance (Months 2-3)
- [ ] Visual credential form builder
- [ ] Template-based issuance API
- [ ] Bulk issuance from CSV
- [ ] Credential management dashboard

### Phase 3: No-Code DID Management (Months 3-4)
- [ ] Visual DID builder
- [ ] DID creation wizard
- [ ] DID management dashboard
- [ ] DID update/deactivate flows

### Phase 4: Template Marketplace (Months 4-5)
- [ ] Template marketplace UI
- [ ] Template sharing and discovery
- [ ] Template versioning
- [ ] Template ratings and reviews

### Phase 5: Advanced Features (Months 5-6)
- [ ] Custom schema builder (visual)
- [ ] Conditional form logic
- [ ] Template analytics
- [ ] Multi-domain support expansion

---

## 8. Data Models

### 8.1 Template Storage

```kotlin
// Database schema
object Templates : UUIDTable("templates") {
    val organizationId = reference("organization_id", Organizations).nullable()
    val name = varchar("name", 255)
    val description = text("description").nullable()
    val domain = varchar("domain", 50) // DomainType enum
    val version = varchar("version", 50)
    val schemaId = varchar("schema_id", 255)
    val schemaDefinition = json("schema_definition")
    val credentialTypes = json("credential_types") // JSON array
    val fieldDefinitions = json("field_definitions") // JSON array
    val uiConfig = json("ui_config").nullable()
    val marketplace = bool("marketplace").default(false)
    val usageCount = integer("usage_count").default(0)
    val rating = decimal("rating", 3, 2).nullable()
    val reviewCount = integer("review_count").default(0)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}
```

### 8.2 DID Ownership Model

**Key Design Decision: Multiple DIDs Per Tenant**

Each organization (tenant) can own **unlimited DIDs**. This enables:
- **Multiple Issuer Identities**: Different DIDs for different credential types or departments
- **DID Method Diversity**: Mix of `did:key`, `did:web`, `did:ion`, etc.
- **Project Isolation**: Different DIDs per project
- **Role-Based DIDs**: Separate DIDs for different roles (issuer, verifier, holder)

```kotlin
object OrganizationDids : UUIDTable("organization_dids") {
    val organizationId = reference("organization_id", Organizations)
    val projectId = reference("project_id", Projects).nullable() // Optional project association
    val did = varchar("did", 255).uniqueIndex() // The actual DID string
    val displayName = varchar("display_name", 255) // User-friendly name
    val description = text("description").nullable()
    val method = varchar("method", 50) // did:key, did:web, did:ion, etc.
    val algorithm = varchar("algorithm", 50) // Ed25519, secp256k1, etc.
    val keyId = varchar("key_id", 255) // Internal key identifier
    val didDocument = json("did_document") // Full DID Document JSON
    val status = varchar("status", 50).default("active") // active, deactivated
    val purpose = varchar("purpose", 50).nullable() // issuer, verifier, holder, etc.
    val metadata = json("metadata").nullable() // Additional metadata
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    val deactivatedAt = datetime("deactivated_at").nullable()
    
    // Indexes for fast lookups
    init {
        index(isUnique = false, organizationId, projectId)
        index(isUnique = false, organizationId, status)
        index(isUnique = false, organizationId, method)
    }
}
```

**Usage Examples:**

```kotlin
// Organization can have multiple DIDs
val org = Organization.findById(orgId)

// Get all DIDs for organization
val allDids = OrganizationDids.select { 
    OrganizationDids.organizationId eq orgId 
}.map { it[OrganizationDids.did] }

// Get DIDs by project
val projectDids = OrganizationDids.select {
    (OrganizationDids.organizationId eq orgId) and
    (OrganizationDids.projectId eq projectId)
}

// Get DIDs by purpose (e.g., all issuer DIDs)
val issuerDids = OrganizationDids.select {
    (OrganizationDids.organizationId eq orgId) and
    (OrganizationDids.purpose eq "issuer")
}

// Get DIDs by method
val webDids = OrganizationDids.select {
    (OrganizationDids.organizationId eq orgId) and
    (OrganizationDids.method eq "web")
}
```

**API Support:**

```kotlin
// List all DIDs for organization
GET /v1/dids
// Returns all DIDs owned by the authenticated organization

// Filter by project
GET /v1/dids?projectId=project-123

// Filter by method
GET /v1/dids?method=web

// Filter by purpose
GET /v1/dids?purpose=issuer

// Create new DID (adds to organization's DID collection)
POST /v1/dids/create
// Automatically associates with organization from API key
```

### 8.3 Credential Issuance Records

```kotlin
object CredentialIssuances : UUIDTable("credential_issuances") {
    val organizationId = reference("organization_id", Organizations)
    val projectId = reference("project_id", Projects).nullable()
    val templateId = reference("template_id", Templates)
    val credentialId = varchar("credential_id", 255).uniqueIndex()
    val issuerDid = varchar("issuer_did", 255) // References one of organization's DIDs
    val subjectDid = varchar("subject_did", 255)
    val credentialData = json("credential_data") // Full VC JSON
    val status = varchar("status", 50) // issued, revoked, expired
    val issuedAt = datetime("issued_at")
    val expiresAt = datetime("expires_at").nullable()
    val revokedAt = datetime("revoked_at").nullable()
    val createdAt = datetime("created_at")
    
    // Index for finding credentials by issuer DID
    init {
        index(isUnique = false, organizationId, issuerDid)
    }
}
```

---

## 9. User Experience Flows

### 9.1 First-Time User: Issue Credential

1. **Sign Up** → Create organization
2. **Create Project** → "Education Credentials"
3. **Browse Templates** → Select "University Degree"
4. **Create Issuer DID** → One-click DID creation
5. **Fill Form** → Template-generated form
6. **Issue Credential** → One-click issuance
7. **View Credential** → See issued credential with QR code

**Time to First Credential: < 5 minutes**

### 9.2 Power User: Custom Template

1. **Create Custom Template** → Use schema builder
2. **Define Fields** → Drag & drop field types
3. **Set Validation** → Configure rules
4. **Preview Form** → Test form layout
5. **Save Template** → Template ready to use
6. **Issue Credentials** → Use custom template

**Time to Custom Template: < 15 minutes**

---

## 10. Success Metrics

### Adoption Metrics
- Templates created per organization
- Credentials issued per template
- Custom templates vs. marketplace templates
- Domain distribution (which domains are most used)

### Usability Metrics
- Time to first credential: Target < 5 minutes
- Template creation time: Target < 15 minutes
- Error rate: Target < 2%
- User satisfaction: Target NPS > 50

### Business Metrics
- Templates in marketplace: Target 100+ by month 6
- Organizations using templates: Target 500+ by month 6
- Credentials issued via templates: Target 10,000+ by month 6

---

## 11. Technical Implementation

### 11.1 Template Engine

```kotlin
class TemplateEngine(
    private val templateService: TemplateService,
    private val schemaRegistry: SchemaRegistry,
    private val credentialService: CredentialService
) {
    /**
     * Issue credential from template with field values
     */
    suspend fun issueFromTemplate(
        templateId: String,
        issuerDid: String,
        subjectDid: String,
        fieldValues: Map<String, JsonElement>,
        options: IssuanceOptions
    ): VerifiableCredential {
        // 1. Load template
        val template = templateService.getTemplate(templateId)
            ?: throw TemplateNotFoundException(templateId)
        
        // 2. Validate field values against template
        validateFieldValues(template, fieldValues)
        
        // 3. Build credential subject from field values
        val credentialSubject = buildCredentialSubject(
            template = template,
            fieldValues = fieldValues,
            subjectDid = subjectDid
        )
        
        // 4. Create credential using TrustWeave SDK
        return credentialService.issue(
            request = IssuanceRequest(
                credential = buildCredential(
                    template = template,
                    issuerDid = issuerDid,
                    credentialSubject = credentialSubject,
                    options = options
                ),
                proofOptions = ProofOptions(
                    proofType = template.defaultProofType
                )
            )
        )
    }
    
    private fun buildCredentialSubject(
        template: CredentialSchemaTemplate,
        fieldValues: Map<String, JsonElement>,
        subjectDid: String
    ): CredentialSubject {
        // Build nested JSON structure from field paths
        val claims = buildNestedClaims(template.fieldOrder, fieldValues)
        return CredentialSubject.fromDid(subjectDid, claims = claims)
    }
}
```

### 11.2 Form Generator

```kotlin
class FormGenerator(
    private val templateService: TemplateService
) {
    /**
     * Generate form configuration from template
     */
    suspend fun generateForm(templateId: String): FormConfig {
        val template = templateService.getTemplate(templateId)
            ?: throw TemplateNotFoundException(templateId)
        
        return FormConfig(
            fields = template.requiredFields + template.optionalFields,
            layout = template.uiConfig.formLayout,
            sections = template.uiConfig.sections,
            conditionalLogic = template.uiConfig.conditionalLogic,
            validationRules = template.fieldValidations
        )
    }
}
```

---

## 12. Next Steps

### Immediate Actions
1. **Finalize Template Data Model** - Review and approve schema
2. **Design UI Mockups** - Create detailed wireframes
3. **Build Template Engine** - Core template processing
4. **Create Pre-built Templates** - 10+ domain templates

### Short-term (1-3 months)
1. **No-Code Credential Issuance** - Visual form builder
2. **No-Code DID Management** - DID creation wizard
3. **Template Marketplace** - Basic sharing and discovery

### Long-term (3-6 months)
1. **Custom Schema Builder** - Visual schema creation
2. **Advanced Workflows** - Conditional logic, automation
3. **Analytics Dashboard** - Template usage, credential stats

---

**Document Version:** 1.0  
**Last Updated:** 2025-01-15  
**Status:** Ready for Implementation

