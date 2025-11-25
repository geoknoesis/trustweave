---
title: DID Registration Specification Implementation
---

# DID Registration Specification Implementation

## Overview

Trustweave now fully supports the **official DID Method Registry format** from https://identity.foundation/did-registration/. You can use registry entries directly to create DID method implementations without writing code.

## What Was Implemented

### 1. Official Registry Format Support

**New Data Model**: `DidMethodRegistryEntry`
- Matches the official registry JSON structure
- Fields: `name`, `status`, `specification`, `contact`, `implementations`
- Supports `implementations[].driverUrl` for resolver endpoints

### 2. Automatic Mapping

**Mapper**: `RegistryEntryMapper`
- Converts official registry entries → Trustweave `DidMethod` implementations
- Automatically extracts resolver configuration from `driverUrl`
- Selects best implementation (prefers non-testnet)
- Determines protocol adapter (standard or godiddy)

### 3. Dual Format Support

The loader supports both:
1. **Official Format** (recommended): Registry format with `implementations[].driverUrl`
2. **Legacy Format** (backward compatible): Trustweave format with `driver` and `capabilities`

## Usage Examples

### Official Registry Format

```json
{
  "name": "web",
  "status": "implemented",
  "specification": "https://w3c-ccg.github.io/did-method-web/",
  "contact": {
    "name": "W3C CCG",
    "url": "https://www.w3.org/community/credentials/"
  },
  "implementations": [
    {
      "name": "Universal Resolver",
      "driverUrl": "https://dev.uniresolver.io",
      "testNet": false
    }
  ]
}
```

### Loading and Using

```kotlin
val kms = InMemoryKeyManagementService()
val registry = DidMethodRegistry()

// Load from official registry format
DidMethodRegistration.registerFromClasspath(registry, kms)

// Use the method
val result = registry.resolve("did:web:example.com")
```

## How It Works

```
┌─────────────────────────────────────────┐
│  Official Registry JSON Format          │
│  {                                      │
│    "name": "web",                       │
│    "implementations": [                 │
│      { "driverUrl": "https://..." }     │
│    ]                                    │
│  }                                      │
└──────────────┬──────────────────────────┘
               │
               │ Parsed by
               │ DidMethodRegistryEntryParser
               ▼
┌─────────────────────────────────────────┐
│  DidMethodRegistryEntry (Data Class)    │
│  - name: "web"                          │
│  - implementations: [...]               │
└──────────────┬──────────────────────────┘
               │
               │ Mapped by
               │ RegistryEntryMapper
               ▼
┌─────────────────────────────────────────┐
│  HttpDidMethod (DidMethod impl)         │
│  - method: "web"                        │
│  - resolver: UniversalResolver           │
│    (configured from driverUrl)          │
└─────────────────────────────────────────┘
```

## Key Features

1. **Official Format**: Uses the exact format from identity.foundation/did-registration
2. **Automatic Detection**: Protocol adapter automatically determined from URL/name
3. **Smart Selection**: Prefers mainnet over testnet implementations
4. **Backward Compatible**: Still supports legacy Trustweave format
5. **Zero Code**: Just drop a JSON file following the registry format

## Files Created/Modified

**New Files:**
- `DidMethodRegistryEntry.kt` - Official registry format data model
- `RegistryEntryMapper.kt` - Maps registry entries to DidMethod
- `OFFICIAL_SPEC.md` - Documentation for official format support

**Updated Files:**
- `JsonDidMethodLoader.kt` - Now supports both formats
- `JsonDidMethodProvider.kt` - Works with registry entries
- Example JSON files - Updated to official format
- `schema.json` - Updated to match official spec

## Benefits

1. ✅ **Standards Compliant**: Uses official registry format
2. ✅ **Easy Integration**: Copy registry entries directly
3. ✅ **Automatic Configuration**: Resolver settings extracted automatically
4. ✅ **Future Proof**: Supports additional registry fields via `additionalProperties`

