# TrustWeave Cloud API - Complete Reference

> **Complete API documentation for TrustWeave Cloud SaaS platform using API_KEY authentication**

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Authentication](#authentication)
3. [API Endpoints](#api-endpoints)
4. [Data Models](#data-models)
5. [Error Handling](#error-handling)
6. [Client SDK Usage](#client-sdk-usage)
7. [Rate Limiting](#rate-limiting)
8. [OpenAPI Specification](#openapi-specification)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│  Account Management System (Separate)                    │
│  - User signup/login                                     │
│  - Billing & subscriptions                              │
│  - API key generation UI                                 │
│  - Team management                                       │
└──────────────┬──────────────────────────────────────────┘
               │
               │ Creates/manages API keys
               ▼
┌─────────────────────────────────────────────────────────┐
│  API Keys Database                                       │
│  - api_key (unique, identifies organization)            │
│  - organization_id                                        │
│  - trust_domain_id (optional scope)                      │
│  - permissions                                            │
└──────────────┬──────────────────────────────────────────┘
               │
               │ Used in Authorization header
               ▼
┌─────────────────────────────────────────────────────────┐
│  TrustWeave Cloud API                                    │
│  - All endpoints require API key                         │
│  - API key → Organization → Resources                   │
└─────────────────────────────────────────────────────────┘
```

### Key Design Principles

1. **API Key as Account Identifier**: The API key uniquely identifies the organization/account
2. **Automatic Scoping**: All resources are automatically scoped to the organization identified by the API key
3. **Separate Account Management**: User authentication, billing, and team management handled separately
4. **RESTful Design**: Standard HTTP methods and status codes
5. **Type-Safe Models**: All request/response models use Kotlinx Serialization

---

## Authentication

All API requests require an API key in the `Authorization` header:

```http
Authorization: Bearer tw_live_abc123def456ghi789...
```

### API Key Format

- **Production**: `tw_live_{48-char-random-string}`
- **Test**: `tw_test_{48-char-random-string}`

### API Key Structure

The API key format provides:
- **Prefix**: Identifies environment (`tw_live_` or `tw_test_`)
- **Random String**: 48-character base64url-encoded random value
- **Total Length**: ~56 characters

### Example API Keys

```
tw_live_abc123def456ghi789jkl012mno345pqr678stu901vwx234yz
tw_test_xyz789abc123def456ghi789jkl012mno345pqr678stu901vwx
```

---

## API Endpoints

### Base URL

- **Production**: `https://api.trustweave.io/v1`
- **Test**: `https://api.test.trustweave.io/v1`

### 1. Trust Domains

#### List Trust Domains

```http
GET /v1/trust-domains
Authorization: Bearer tw_live_...
```

**Response: 200 OK**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Education Credentials",
    "description": "Trust domain for university credentials",
    "domainType": "EDUCATION",
    "organizationId": "660e8400-e29b-41d4-a716-446655440000",
    "environment": "PRODUCTION",
    "settings": {
      "allowPublicAnchors": false,
      "requireDidValidation": true,
      "autoValidateOnCreate": false,
      "maxAnchors": null
    },
    "createdAt": "2025-01-15T10:30:00Z",
    "updatedAt": "2025-01-15T10:30:00Z"
  }
]
```

#### Get Trust Domain

```http
GET /v1/trust-domains/{trustDomainId}
Authorization: Bearer tw_live_...
```

**Path Parameters:**
- `trustDomainId` (string, UUID): The ID of the trust domain

**Response: 200 OK**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Education Credentials",
  "description": "Trust domain for university credentials",
  "domainType": "EDUCATION",
  "organizationId": "660e8400-e29b-41d4-a716-446655440000",
  "environment": "PRODUCTION",
  "settings": {
    "allowPublicAnchors": false,
    "requireDidValidation": true,
    "autoValidateOnCreate": false,
    "maxAnchors": null
  },
  "createdAt": "2025-01-15T10:30:00Z",
  "updatedAt": "2025-01-15T10:30:00Z"
}
```

#### Create Trust Domain

```http
POST /v1/trust-domains
Authorization: Bearer tw_live_...
Content-Type: application/json

{
  "name": "Healthcare Credentials",
  "description": "Trust domain for medical credentials",
  "domainType": "HEALTHCARE",
  "environment": "PRODUCTION",
  "settings": {
    "allowPublicAnchors": false,
    "requireDidValidation": true,
    "autoValidateOnCreate": false,
    "maxAnchors": null
  }
}
```

**Request Body:**
- `name` (string, required): Name of the trust domain
- `description` (string, optional): Description of the trust domain
- `domainType` (enum, optional): Type of domain (default: `CUSTOM`)
  - `EDUCATION`
  - `HEALTHCARE`
  - `SUPPLY_CHAIN`
  - `FINANCE`
  - `GOVERNMENT`
  - `CUSTOM`
- `environment` (enum, optional): Environment (default: `DEVELOPMENT`)
  - `DEVELOPMENT`
  - `STAGING`
  - `PRODUCTION`
- `settings` (object, optional): Domain-specific settings

**Response: 201 Created**

```json
{
  "id": "770e8400-e29b-41d4-a716-446655440000",
  "name": "Healthcare Credentials",
  "description": "Trust domain for medical credentials",
  "domainType": "HEALTHCARE",
  "organizationId": "660e8400-e29b-41d4-a716-446655440000",
  "environment": "PRODUCTION",
  "settings": {
    "allowPublicAnchors": false,
    "requireDidValidation": true,
    "autoValidateOnCreate": false,
    "maxAnchors": null
  },
  "createdAt": "2025-01-15T11:00:00Z",
  "updatedAt": "2025-01-15T11:00:00Z"
}
```

#### Update Trust Domain

```http
PUT /v1/trust-domains/{trustDomainId}
Authorization: Bearer tw_live_...
Content-Type: application/json

{
  "name": "Healthcare Credentials - Updated",
  "description": "Updated description",
  "settings": {
    "allowPublicAnchors": true
  }
}
```

**Request Body:**
- All fields are optional (only include fields to update)

**Response: 200 OK**

```json
{
  "id": "770e8400-e29b-41d4-a716-446655440000",
  "name": "Healthcare Credentials - Updated",
  "description": "Updated description",
  "domainType": "HEALTHCARE",
  "organizationId": "660e8400-e29b-41d4-a716-446655440000",
  "environment": "PRODUCTION",
  "settings": {
    "allowPublicAnchors": true,
    "requireDidValidation": true,
    "autoValidateOnCreate": false,
    "maxAnchors": null
  },
  "createdAt": "2025-01-15T11:00:00Z",
  "updatedAt": "2025-01-15T11:15:00Z"
}
```

#### Delete Trust Domain

```http
DELETE /v1/trust-domains/{trustDomainId}
Authorization: Bearer tw_live_...
```

**Response: 204 No Content**

---

### 2. Trust Anchors

#### List Trust Anchors

```http
GET /v1/trust-domains/{trustDomainId}/trust-anchors
Authorization: Bearer tw_live_...
```

**Query Parameters:**
- `page` (integer, optional): Page number (default: 1)
- `limit` (integer, optional): Items per page (default: 50, max: 100)
- `status` (string, optional): Filter by status
  - `ACTIVE`
  - `INACTIVE`
  - `PENDING`
  - `SUSPENDED`
- `credentialType` (string, optional): Filter by credential type

**Response: 200 OK**

```json
{
  "data": [
    {
      "id": "880e8400-e29b-41d4-a716-446655440000",
      "trustDomainId": "550e8400-e29b-41d4-a716-446655440000",
      "did": "did:web:university.edu",
      "name": "State University",
      "credentialTypes": ["DegreeCredential", "TranscriptCredential"],
      "description": "Official university trust anchor",
      "status": "ACTIVE",
      "metadata": {
        "contactEmail": "registrar@university.edu",
        "website": "https://university.edu"
      },
      "createdAt": "2025-01-15T10:35:00Z",
      "createdBy": "user-id"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 1,
    "totalPages": 1
  }
}
```

#### Get Trust Anchor

```http
GET /v1/trust-domains/{trustDomainId}/trust-anchors/{anchorId}
Authorization: Bearer tw_live_...
```

**Response: 200 OK**

```json
{
  "id": "880e8400-e29b-41d4-a716-446655440000",
  "trustDomainId": "550e8400-e29b-41d4-a716-446655440000",
  "did": "did:web:university.edu",
  "name": "State University",
  "credentialTypes": ["DegreeCredential", "TranscriptCredential"],
  "description": "Official university trust anchor",
  "status": "ACTIVE",
  "metadata": {
    "contactEmail": "registrar@university.edu",
    "website": "https://university.edu"
  },
  "createdAt": "2025-01-15T10:35:00Z",
  "createdBy": "user-id"
}
```

#### Create Trust Anchor

```http
POST /v1/trust-domains/{trustDomainId}/trust-anchors
Authorization: Bearer tw_live_...
Content-Type: application/json

{
  "did": "did:web:university.edu",
  "name": "State University",
  "credentialTypes": ["DegreeCredential", "TranscriptCredential"],
  "description": "Official university trust anchor",
  "metadata": {
    "contactEmail": "registrar@university.edu",
    "website": "https://university.edu"
  }
}
```

**Request Body:**
- `did` (string, required): The DID of the trust anchor
- `name` (string, required): Human-readable name
- `credentialTypes` (array of strings, required): List of credential types this anchor can issue
- `description` (string, optional): Description of the trust anchor
- `metadata` (object, optional): Additional metadata (key-value pairs)

**Response: 201 Created**

```json
{
  "id": "880e8400-e29b-41d4-a716-446655440000",
  "trustDomainId": "550e8400-e29b-41d4-a716-446655440000",
  "did": "did:web:university.edu",
  "name": "State University",
  "credentialTypes": ["DegreeCredential", "TranscriptCredential"],
  "description": "Official university trust anchor",
  "status": "ACTIVE",
  "metadata": {
    "contactEmail": "registrar@university.edu",
    "website": "https://university.edu"
  },
  "createdAt": "2025-01-15T10:35:00Z",
  "createdBy": "api-key-id"
}
```

#### Update Trust Anchor

```http
PUT /v1/trust-domains/{trustDomainId}/trust-anchors/{anchorId}
Authorization: Bearer tw_live_...
Content-Type: application/json

{
  "name": "State University - Updated",
  "credentialTypes": ["DegreeCredential", "TranscriptCredential", "CertificateCredential"],
  "metadata": {
    "contactEmail": "registrar@university.edu",
    "website": "https://university.edu",
    "phone": "+1-555-0123"
  }
}
```

**Response: 200 OK**

```json
{
  "id": "880e8400-e29b-41d4-a716-446655440000",
  "trustDomainId": "550e8400-e29b-41d4-a716-446655440000",
  "did": "did:web:university.edu",
  "name": "State University - Updated",
  "credentialTypes": ["DegreeCredential", "TranscriptCredential", "CertificateCredential"],
  "description": "Official university trust anchor",
  "status": "ACTIVE",
  "metadata": {
    "contactEmail": "registrar@university.edu",
    "website": "https://university.edu",
    "phone": "+1-555-0123"
  },
  "createdAt": "2025-01-15T10:35:00Z",
  "updatedAt": "2025-01-15T11:20:00Z",
  "createdBy": "user-id"
}
```

#### Delete Trust Anchor

```http
DELETE /v1/trust-domains/{trustDomainId}/trust-anchors/{anchorId}
Authorization: Bearer tw_live_...
```

**Response: 204 No Content**

#### Validate Trust Anchor

```http
POST /v1/trust-domains/{trustDomainId}/trust-anchors/{anchorId}/validate
Authorization: Bearer tw_live_...
```

**Response: 200 OK**

```json
{
  "anchorId": "880e8400-e29b-41d4-a716-446655440000",
  "did": "did:web:university.edu",
  "isValid": true,
  "isResolvable": true,
  "didDocument": {
    "id": "did:web:university.edu",
    "@context": ["https://www.w3.org/ns/did/v1"],
    "verificationMethod": [...],
    "service": [...]
  },
  "validationErrors": [],
  "validatedAt": "2025-01-15T11:25:00Z"
}
```

---

### 3. Bulk Operations

#### Bulk Create Trust Anchors

```http
POST /v1/trust-domains/{trustDomainId}/trust-anchors/bulk
Authorization: Bearer tw_live_...
Content-Type: application/json

{
  "anchors": [
    {
      "did": "did:web:university1.edu",
      "name": "University 1",
      "credentialTypes": ["DegreeCredential"]
    },
    {
      "did": "did:web:university2.edu",
      "name": "University 2",
      "credentialTypes": ["DegreeCredential"]
    }
  ]
}
```

**Response: 200 OK**

```json
{
  "created": 2,
  "failed": 0,
  "results": [
    {
      "index": 0,
      "success": true,
      "anchorId": "990e8400-e29b-41d4-a716-446655440000",
      "did": "did:web:university1.edu"
    },
    {
      "index": 1,
      "success": true,
      "anchorId": "aa0e8400-e29b-41d4-a716-446655440000",
      "did": "did:web:university2.edu"
    }
  ]
}
```

---

### 4. Trust Network

#### Get Trust Network Graph

```http
GET /v1/trust-domains/{trustDomainId}/network
Authorization: Bearer tw_live_...
```

**Query Parameters:**
- `depth` (integer, optional): Maximum depth to traverse (default: 2, max: 5)
- `includeInactive` (boolean, optional): Include inactive anchors (default: false)

**Response: 200 OK**

```json
{
  "trustDomainId": "550e8400-e29b-41d4-a716-446655440000",
  "nodes": [
    {
      "id": "880e8400-e29b-41d4-a716-446655440000",
      "did": "did:web:university.edu",
      "name": "State University",
      "type": "trust_anchor"
    }
  ],
  "edges": [
    {
      "from": "880e8400-e29b-41d4-a716-446655440000",
      "to": "990e8400-e29b-41d4-a716-446655440000",
      "type": "trusts",
      "credentialTypes": ["DegreeCredential"]
    }
  ],
  "generatedAt": "2025-01-15T11:30:00Z"
}
```

#### Find Trust Path

```http
POST /v1/trust-domains/{trustDomainId}/network/path
Authorization: Bearer tw_live_...
Content-Type: application/json

{
  "fromDid": "did:web:university.edu",
  "toDid": "did:web:employer.com",
  "credentialType": "DegreeCredential"
}
```

**Request Body:**
- `fromDid` (string, required): Starting DID
- `toDid` (string, required): Target DID
- `credentialType` (string, optional): Filter by credential type

**Response: 200 OK**

```json
{
  "pathExists": true,
  "path": [
    {
      "did": "did:web:university.edu",
      "name": "State University"
    },
    {
      "did": "did:web:accreditation.org",
      "name": "Accreditation Board"
    },
    {
      "did": "did:web:employer.com",
      "name": "Tech Corp"
    }
  ],
  "trustScore": 0.95,
  "credentialType": "DegreeCredential"
}
```

---

## Data Models

### TrustDomain

```kotlin
@kotlinx.serialization.Serializable
data class TrustDomain(
    val id: String,
    val name: String,
    val description: String?,
    val domainType: DomainType,
    val organizationId: String,
    val environment: Environment,
    val settings: TrustDomainSettings?,
    val createdAt: String,
    val updatedAt: String
)
```

### DomainType

```kotlin
@kotlinx.serialization.Serializable
enum class DomainType {
    EDUCATION,
    HEALTHCARE,
    SUPPLY_CHAIN,
    FINANCE,
    GOVERNMENT,
    CUSTOM
}
```

### Environment

```kotlin
@kotlinx.serialization.Serializable
enum class Environment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION
}
```

### TrustDomainSettings

```kotlin
@kotlinx.serialization.Serializable
data class TrustDomainSettings(
    val allowPublicAnchors: Boolean = false,
    val requireDidValidation: Boolean = true,
    val autoValidateOnCreate: Boolean = false,
    val maxAnchors: Int? = null
)
```

### TrustAnchor

```kotlin
@kotlinx.serialization.Serializable
data class TrustAnchor(
    val id: String,
    val trustDomainId: String,
    val did: String,
    val name: String,
    val credentialTypes: List<String>,
    val description: String?,
    val status: AnchorStatus,
    val metadata: Map<String, String>?,
    val createdAt: String,
    val createdBy: String
)
```

### AnchorStatus

```kotlin
@kotlinx.serialization.Serializable
enum class AnchorStatus {
    ACTIVE,
    INACTIVE,
    PENDING,
    SUSPENDED
}
```

### CreateTrustDomainRequest

```kotlin
@kotlinx.serialization.Serializable
data class CreateTrustDomainRequest(
    val name: String,
    val description: String? = null,
    val domainType: DomainType = DomainType.CUSTOM,
    val environment: Environment = Environment.DEVELOPMENT,
    val settings: TrustDomainSettings? = null
)
```

### UpdateTrustDomainRequest

```kotlin
@kotlinx.serialization.Serializable
data class UpdateTrustDomainRequest(
    val name: String? = null,
    val description: String? = null,
    val settings: TrustDomainSettings? = null
)
```

### CreateTrustAnchorRequest

```kotlin
@kotlinx.serialization.Serializable
data class CreateTrustAnchorRequest(
    val did: String,
    val name: String,
    val credentialTypes: List<String>,
    val description: String? = null,
    val metadata: Map<String, String>? = null
)
```

### PaginatedResponse

```kotlin
@kotlinx.serialization.Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val pagination: Pagination
)

@kotlinx.serialization.Serializable
data class Pagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    val totalPages: Int
)
```

---

## Error Handling

All errors follow a consistent format:

```kotlin
@kotlinx.serialization.Serializable
data class ErrorResponse(
    val error: ErrorDetail
)

@kotlinx.serialization.Serializable
data class ErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null
)
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_API_KEY` | 401 | API key is missing, invalid, or expired |
| `FORBIDDEN` | 403 | API key doesn't have permission for this resource |
| `NOT_FOUND` | 404 | Resource not found or doesn't belong to organization |
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many requests |
| `INTERNAL_ERROR` | 500 | Server error |

### Example Error Response

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "DID format is invalid",
    "details": {
      "field": "did",
      "value": "invalid-did",
      "expectedFormat": "did:method:identifier"
    }
  }
}
```

---

## Client SDK Usage

### Initialize Client

```kotlin
// Initialize client with API key
val client = TrustWeaveCloudClient(
    apiKey = "tw_live_abc123def456ghi789..."
)
```

### Trust Domain Operations

```kotlin
// List all trust domains for the organization
val domains = client.trustDomains.list()

// Create a new trust domain
val newDomain = client.trustDomains.create(
    CreateTrustDomainRequest(
        name = "Education Credentials",
        description = "Trust domain for university credentials",
        domainType = DomainType.EDUCATION,
        environment = Environment.PRODUCTION
    )
)

// Get a specific trust domain
val domain = client.trustDomains.get(newDomain.id)

// Update trust domain
val updated = client.trustDomains.update(
    trustDomainId = newDomain.id,
    request = UpdateTrustDomainRequest(
        name = "Education Credentials - Updated",
        settings = TrustDomainSettings(
            allowPublicAnchors = true
        )
    )
)

// Delete trust domain
client.trustDomains.delete(newDomain.id)
```

### Trust Anchor Operations

```kotlin
// List trust anchors in a domain
val anchors = client.trustDomains.listTrustAnchors(
    trustDomainId = newDomain.id
)

// Create a trust anchor
val anchor = client.trustDomains.createTrustAnchor(
    trustDomainId = newDomain.id,
    request = CreateTrustAnchorRequest(
        did = "did:web:university.edu",
        name = "State University",
        credentialTypes = listOf("DegreeCredential", "TranscriptCredential"),
        description = "Official university trust anchor"
    )
)

// Get a specific trust anchor
val anchorDetails = client.trustDomains.getTrustAnchor(
    trustDomainId = newDomain.id,
    anchorId = anchor.id
)

// Update trust anchor
val updatedAnchor = client.trustDomains.updateTrustAnchor(
    trustDomainId = newDomain.id,
    anchorId = anchor.id,
    request = UpdateTrustAnchorRequest(
        name = "State University - Updated",
        credentialTypes = listOf("DegreeCredential", "TranscriptCredential", "CertificateCredential")
    )
)

// Validate trust anchor
val validation = client.trustDomains.validateTrustAnchor(
    trustDomainId = newDomain.id,
    anchorId = anchor.id
)

// Delete trust anchor
client.trustDomains.deleteTrustAnchor(
    trustDomainId = newDomain.id,
    anchorId = anchor.id
)
```

### Trust Network Operations

```kotlin
// Get trust network graph
val network = client.trustDomains.getNetwork(
    trustDomainId = newDomain.id,
    depth = 2,
    includeInactive = false
)

// Find trust path
val path = client.trustDomains.findTrustPath(
    trustDomainId = newDomain.id,
    fromDid = "did:web:university.edu",
    toDid = "did:web:employer.com",
    credentialType = "DegreeCredential"
)
```

---

## Rate Limiting

Rate limits are communicated via HTTP headers:

```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1642248000
```

### Rate Limit Tiers

| Tier | Requests per Minute | Requests per Hour |
|------|---------------------|-------------------|
| Free | 60 | 1,000 |
| Starter | 300 | 10,000 |
| Pro | 1,000 | 100,000 |
| Enterprise | Unlimited | Unlimited |

When rate limit is exceeded, the API returns:

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1642248000
Retry-After: 60

{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded. Retry after 60 seconds."
  }
}
```

---

## OpenAPI Specification

The complete OpenAPI 3.0 specification is available at:

- **Production**: `https://api.trustweave.io/openapi.json`
- **Test**: `https://api.test.trustweave.io/openapi.json`

### Key OpenAPI Components

#### Security Scheme

```yaml
components:
  securitySchemes:
    ApiKeyAuth:
      type: http
      scheme: bearer
      bearerFormat: API_KEY
      description: |
        API key in format: tw_live_... or tw_test_...
        The API key identifies the organization/account.
```

#### Schemas

All data models are defined in the OpenAPI specification with:
- Type definitions
- Required fields
- Validation rules
- Example values

---

## Summary

### Key Features

- ✅ **API Key Authentication**: Simple, secure authentication using API keys
- ✅ **Automatic Organization Scoping**: All resources automatically scoped to API key's organization
- ✅ **Trust Domain Management**: Full CRUD operations for trust domains
- ✅ **Trust Anchor Management**: Create, update, delete, and validate trust anchors
- ✅ **Bulk Operations**: Efficient bulk creation of trust anchors
- ✅ **Trust Network**: Graph visualization and path finding
- ✅ **Comprehensive Error Handling**: Structured error responses with codes and details
- ✅ **Rate Limiting**: Clear rate limit headers and error responses
- ✅ **Type-Safe Models**: Kotlinx Serialization for type safety
- ✅ **RESTful Design**: Standard HTTP methods and status codes

### Next Steps

1. Generate API keys in the account management dashboard
2. Use the client SDK or make direct HTTP requests
3. Start managing trust domains and anchors
4. Build trust networks and verify trust paths

---

**Document Version:** 1.0  
**Last Updated:** 2025-01-15  
**API Version:** 1.0

