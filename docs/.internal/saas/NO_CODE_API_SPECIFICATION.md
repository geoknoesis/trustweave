---
title: TrustWeave No-Code API Specification
---

# TrustWeave No-Code API Specification

> **Complete API reference for no-code credential and DID management**

**Version:** 1.0  
**Base URL:** `https://api.trustweave.io/v1`

---

## Authentication

All API requests require an API key:

```http
Authorization: Bearer tw_live_abc123def456...
```

---

## 1. Schema Template API

### 1.1 List Templates

```http
GET /v1/templates
```

**Query Parameters:**
- `domain` (string, optional): Filter by domain type
- `marketplace` (boolean, optional): Show only marketplace templates
- `organizationId` (string, optional): Show organization templates
- `search` (string, optional): Search template names/descriptions
- `limit` (integer, optional): Results per page (default: 50)
- `offset` (integer, optional): Pagination offset

**Response:**
```json
{
  "templates": [
    {
      "id": "education-degree-v1",
      "name": "University Degree",
      "description": "Template for issuing university degree credentials",
      "domain": "EDUCATION",
      "version": "1.0.0",
      "credentialTypes": ["VerifiableCredential", "DegreeCredential"],
      "usageCount": 1234,
      "rating": 4.8,
      "reviewCount": 234,
      "author": "TrustWeave Team",
      "marketplace": true,
      "createdAt": "2024-01-01T00:00:00Z"
    }
  ],
  "pagination": {
    "total": 150,
    "limit": 50,
    "offset": 0
  }
}
```

### 1.2 Get Template

```http
GET /v1/templates/{templateId}
```

**Response:**
```json
{
  "id": "education-degree-v1",
  "name": "University Degree",
  "description": "Template for issuing university degree credentials",
  "domain": "EDUCATION",
  "version": "1.0.0",
  "schemaId": "https://schemas.trustweave.io/education/degree/v1",
  "schemaDefinition": {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
      "degree": {
        "type": "object",
        "properties": {
          "type": {
            "type": "string",
            "enum": ["Bachelor", "Master", "Doctorate"]
          },
          "name": { "type": "string" },
          "university": { "type": "string" },
          "graduationDate": { "type": "string", "format": "date" },
          "major": { "type": "string" },
          "gpa": { "type": "number", "minimum": 0, "maximum": 4.0 }
        },
        "required": ["type", "name", "university", "graduationDate"]
      }
    }
  },
  "requiredFields": [
    {
      "name": "degree.type",
      "label": "Degree Type",
      "type": "SELECT",
      "required": true,
      "options": ["Bachelor", "Master", "Doctorate"]
    },
    {
      "name": "degree.name",
      "label": "Degree Name",
      "type": "STRING",
      "required": true
    }
  ],
  "optionalFields": [
    {
      "name": "degree.major",
      "label": "Major",
      "type": "STRING",
      "required": false
    },
    {
      "name": "degree.gpa",
      "label": "GPA",
      "type": "NUMBER",
      "required": false,
      "validation": {
        "minValue": 0,
        "maxValue": 4.0
      }
    }
  ],
  "defaultValidityDays": 3650,
  "defaultProofType": "Ed25519Signature2020",
  "uiConfig": {
    "formLayout": "TWO_COLUMN",
    "sections": [
      {
        "title": "Degree Information",
        "fields": ["degree.type", "degree.name", "degree.university"]
      },
      {
        "title": "Academic Details",
        "fields": ["degree.graduationDate", "degree.major", "degree.gpa"]
      }
    ]
  },
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z"
}
```

### 1.3 Create Template

```http
POST /v1/templates
Content-Type: application/json

{
  "name": "Custom Employee Credential",
  "description": "Template for employee verification",
  "domain": "EMPLOYMENT",
  "schemaDefinition": { ... },
  "requiredFields": [ ... ],
  "optionalFields": [ ... ],
  "uiConfig": { ... },
  "marketplace": false
}
```

### 1.4 Generate Form Configuration

```http
POST /v1/templates/{templateId}/generate-form
```

**Response:**
```json
{
  "templateId": "education-degree-v1",
  "formConfig": {
    "fields": [
      {
        "name": "degree.type",
        "label": "Degree Type",
        "type": "SELECT",
        "required": true,
        "options": ["Bachelor", "Master", "Doctorate"],
        "helpText": "Select the type of degree",
        "placeholder": "Choose degree type"
      },
      {
        "name": "degree.name",
        "label": "Degree Name",
        "type": "STRING",
        "required": true,
        "helpText": "Full name of the degree",
        "placeholder": "e.g., Bachelor of Science in Computer Science"
      }
    ],
    "layout": "TWO_COLUMN",
    "sections": [ ... ],
    "validationRules": { ... }
  }
}
```

---

## 2. No-Code Credential API

### 2.1 Issue Credential from Template

```http
POST /v1/credentials/issue-from-template
Content-Type: application/json

{
  "templateId": "education-degree-v1",
  "issuerDid": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
  "issuerKeyId": "key-1",
  "subjectDid": "did:key:z6Mkstudent123",
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
    "addToTrustRegistry": true,
    "enableRevocation": false
  }
}
```

**Response:**
```json
{
  "credential": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "id": "urn:uuid:abc123-def456-ghi789",
    "type": ["VerifiableCredential", "DegreeCredential"],
    "issuer": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
    "issuanceDate": "2024-01-15T10:30:00Z",
    "expirationDate": "2034-01-15T10:30:00Z",
    "credentialSubject": {
      "id": "did:key:z6Mkstudent123",
      "degree": {
        "type": "Bachelor",
        "name": "Bachelor of Science in Computer Science",
        "university": "State University",
        "graduationDate": "2023-05-15",
        "major": "Computer Science",
        "gpa": 3.8
      }
    },
    "proof": {
      "type": "Ed25519Signature2020",
      "proofValue": "z..."
    }
  },
  "credentialId": "urn:uuid:abc123-def456-ghi789",
  "issuedAt": "2024-01-15T10:30:00Z",
  "templateId": "education-degree-v1"
}
```

### 2.2 Bulk Issue Credentials

```http
POST /v1/credentials/bulk-issue
Content-Type: multipart/form-data

{
  "templateId": "education-degree-v1",
  "issuerDid": "did:key:z6Mk...",
  "issuerKeyId": "key-1",
  "csvFile": <file>,
  "options": {
    "validityYears": 10,
    "anchorToBlockchain": false
  }
}
```

**CSV Format:**
```csv
subjectDid,degree.type,degree.name,degree.university,degree.graduationDate,degree.major,degree.gpa
did:key:student1,Bachelor,B.S. Computer Science,State University,2023-05-15,Computer Science,3.8
did:key:student2,Master,M.S. Engineering,Tech University,2023-06-01,Engineering,3.9
```

**Response:**
```json
{
  "jobId": "bulk-issue-123",
  "status": "processing",
  "totalRows": 100,
  "validRows": 98,
  "invalidRows": 2,
  "errors": [
    {
      "row": 45,
      "error": "Missing required field: degree.name"
    },
    {
      "row": 67,
      "error": "Invalid GPA value: 5.0 (must be between 0 and 4.0)"
    }
  ],
  "results": [
    {
      "row": 1,
      "credentialId": "urn:uuid:...",
      "status": "issued"
    }
  ]
}
```

### 2.3 List Credentials

```http
GET /v1/credentials
```

**Query Parameters:**
- `templateId` (string, optional): Filter by template
- `issuerDid` (string, optional): Filter by issuer
- `subjectDid` (string, optional): Filter by subject
- `status` (string, optional): `valid`, `expired`, `revoked`
- `limit` (integer, optional): Results per page
- `offset` (integer, optional): Pagination offset

**Response:**
```json
{
  "credentials": [
    {
      "id": "urn:uuid:abc123",
      "templateId": "education-degree-v1",
      "templateName": "University Degree",
      "issuerDid": "did:key:z6Mk...",
      "subjectDid": "did:key:z6Mkstudent123",
      "status": "valid",
      "issuedAt": "2024-01-15T10:30:00Z",
      "expiresAt": "2034-01-15T10:30:00Z",
      "credentialPreview": {
        "degree": {
          "type": "Bachelor",
          "name": "Bachelor of Science in Computer Science"
        }
      }
    }
  ],
  "pagination": {
    "total": 1234,
    "limit": 50,
    "offset": 0
  }
}
```

### 2.4 Get Credential

```http
GET /v1/credentials/{credentialId}
```

**Response:**
```json
{
  "credential": {
    "@context": ["https://www.w3.org/2018/credentials/v1"],
    "id": "urn:uuid:abc123",
    "type": ["VerifiableCredential", "DegreeCredential"],
    "issuer": "did:key:z6Mk...",
    "credentialSubject": { ... },
    "proof": { ... }
  },
  "metadata": {
    "templateId": "education-degree-v1",
    "templateName": "University Degree",
    "issuedAt": "2024-01-15T10:30:00Z",
    "expiresAt": "2034-01-15T10:30:00Z",
    "status": "valid",
    "verificationUrl": "https://verify.trustweave.io/credential/urn:uuid:abc123"
  }
}
```

### 2.5 Revoke Credential

```http
POST /v1/credentials/{credentialId}/revoke
Content-Type: application/json

{
  "reason": "Degree revoked due to academic misconduct"
}
```

---

## 3. No-Code DID API

### 3.1 Create DID

```http
POST /v1/dids/create
Content-Type: application/json

{
  "method": "key",
  "displayName": "University Issuer DID",
  "description": "DID for issuing university credentials",
  "algorithm": "Ed25519",
  "keyPurposes": ["authentication", "assertionMethod"],
  "options": {
    "addServiceEndpoints": false
  }
}
```

**Response:**
```json
{
  "did": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
  "didDocument": {
    "@context": ["https://www.w3.org/ns/did/v1"],
    "id": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
    "verificationMethod": [ ... ],
    "authentication": [ ... ],
    "assertionMethod": [ ... ]
  },
  "keyId": "key-1",
  "displayName": "University Issuer DID",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### 3.2 List DIDs

**Note:** Each organization (tenant) can own **unlimited DIDs**. This endpoint returns all DIDs owned by the authenticated organization.

```http
GET /v1/dids
```

**Query Parameters:**
- `method` (string, optional): Filter by DID method (key, web, ion, etc.)
- `projectId` (string, optional): Filter by project
- `purpose` (string, optional): Filter by purpose (issuer, verifier, holder)
- `status` (string, optional): Filter by status (active, deactivated)
- `search` (string, optional): Search by display name or DID
- `limit` (integer, optional): Results per page (default: 50)
- `offset` (integer, optional): Pagination offset

**Response:**
```json
{
  "dids": [
    {
      "did": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
      "displayName": "University Issuer DID",
      "description": "DID for issuing university credentials",
      "method": "key",
      "algorithm": "Ed25519",
      "projectId": "project-123",
      "projectName": "Education Credentials",
      "purpose": "issuer",
      "status": "active",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z",
      "credentialsIssued": 1234
    },
    {
      "did": "did:web:university.edu",
      "displayName": "University Web DID",
      "description": "Web-based DID for public-facing credentials",
      "method": "web",
      "algorithm": "Ed25519",
      "projectId": "project-123",
      "projectName": "Education Credentials",
      "purpose": "issuer",
      "status": "active",
      "createdAt": "2024-01-20T10:30:00Z",
      "updatedAt": "2024-01-20T10:30:00Z",
      "credentialsIssued": 567
    },
    {
      "did": "did:ion:EiClkZMDnmYGhX8tR8i3z2b5M5fN5hJ5vK5xL5yM5zN5oP5q",
      "displayName": "Blockchain-Anchored DID",
      "description": "Ion DID for immutable credential issuance",
      "method": "ion",
      "algorithm": "secp256k1",
      "projectId": "project-456",
      "projectName": "Healthcare Credentials",
      "purpose": "issuer",
      "status": "active",
      "createdAt": "2024-02-01T10:30:00Z",
      "updatedAt": "2024-02-01T10:30:00Z",
      "credentialsIssued": 89
    }
  ],
  "pagination": {
    "total": 12,
    "limit": 50,
    "offset": 0
  },
  "summary": {
    "totalDids": 12,
    "activeDids": 10,
    "deactivatedDids": 2,
    "byMethod": {
      "key": 5,
      "web": 4,
      "ion": 3
    }
  }
}
```

### 3.3 Get DID Details

```http
GET /v1/dids/{did}
```

**Response:**
```json
{
  "did": "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK",
  "displayName": "University Issuer DID",
  "description": "DID for issuing university credentials",
  "method": "key",
  "algorithm": "Ed25519",
  "didDocument": { ... },
  "status": "active",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "statistics": {
    "credentialsIssued": 1234,
    "credentialsValid": 1200,
    "credentialsExpired": 30,
    "credentialsRevoked": 4
  }
}
```

### 3.4 Update DID

```http
PUT /v1/dids/{did}
Content-Type: application/json

{
  "displayName": "Updated University Issuer DID",
  "description": "Updated description",
  "serviceEndpoints": [
    {
      "id": "service-1",
      "type": "CredentialRevocation",
      "serviceEndpoint": "https://university.edu/revocation"
    }
  ]
}
```

### 3.5 Deactivate DID

```http
POST /v1/dids/{did}/deactivate
Content-Type: application/json

{
  "reason": "No longer in use"
}
```

---

## 4. Domain Registry API

### 4.1 List Domains

```http
GET /v1/domains
```

**Response:**
```json
{
  "domains": [
    {
      "type": "EDUCATION",
      "name": "Education",
      "description": "Academic credentials, degrees, transcripts",
      "icon": "🎓",
      "templateCount": 15,
      "credentialsIssued": 50000
    },
    {
      "type": "HEALTHCARE",
      "name": "Healthcare",
      "description": "Medical licenses, certifications, credentials",
      "icon": "🏥",
      "templateCount": 8,
      "credentialsIssued": 25000
    }
  ]
}
```

### 4.2 Get Domain Templates

```http
GET /v1/domains/{domainType}/templates
```

**Response:**
```json
{
  "domain": "EDUCATION",
  "templates": [
    {
      "id": "education-degree-v1",
      "name": "University Degree",
      "usageCount": 1234
    },
    {
      "id": "education-transcript-v1",
      "name": "Academic Transcript",
      "usageCount": 567
    }
  ]
}
```

---

## 5. Error Responses

All errors follow this format:

```json
{
  "error": {
    "code": "TEMPLATE_NOT_FOUND",
    "message": "Template with ID 'education-degree-v1' not found",
    "details": {
      "templateId": "education-degree-v1",
      "availableTemplates": ["education-degree-v2", "education-transcript-v1"]
    }
  }
}
```

**Common Error Codes:**
- `TEMPLATE_NOT_FOUND` - Template ID doesn't exist
- `INVALID_FIELD_VALUE` - Field value doesn't match validation rules
- `MISSING_REQUIRED_FIELD` - Required field not provided
- `DID_RESOLUTION_FAILED` - Could not resolve DID
- `CREDENTIAL_ISSUANCE_FAILED` - Failed to issue credential
- `BULK_IMPORT_ERROR` - Error in bulk import CSV

---

**Document Version:** 1.0  
**Last Updated:** 2025-01-15

