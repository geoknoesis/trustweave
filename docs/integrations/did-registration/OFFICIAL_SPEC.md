---
title: Official DID Registration Specification Support
---

# Official DID Registration Specification Support

This document explains how Trustweave implements support for the official [DID Registration specification](https://identity.foundation/did-registration/) format.

## Official Registry Format

Trustweave now supports the **official DID Method Registry JSON format** used by identity.foundation/did-registration. This allows you to use registry entries directly without modification.

### Format Structure

```json
{
  "name": "web",
  "status": "implemented",
  "specification": "https://w3c-ccg.github.io/did-method-web/",
  "contact": {
    "name": "W3C Credentials Community Group",
    "email": "contact@example.com",
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

## How It Works

### 1. Registry Entry Parsing

The `DidMethodRegistryEntry` data class matches the official registry format:

```kotlin
data class DidMethodRegistryEntry(
    val name: String,
    val status: String? = null,
    val specification: String? = null,
    val contact: ContactInfo? = null,
    val implementations: List<MethodImplementation> = emptyList()
)
```

### 2. Automatic Mapping to DidMethod

The `RegistryEntryMapper` automatically converts registry entries to Trustweave `DidMethod` implementations:

1. **Selects Best Implementation**: Chooses non-testnet implementation with `driverUrl`
2. **Extracts Resolver URL**: Uses `implementations[].driverUrl` as the resolver endpoint
3. **Determines Protocol Adapter**: 
   - URLs containing "godiddy" → GoDiddy adapter
   - Otherwise → Standard Universal Resolver adapter
4. **Creates HttpDidMethod**: Wraps the resolver in an `HttpDidMethod` instance

### 3. Usage

```kotlin
// Load registry entry
val entry = DidMethodRegistryEntryParser.parse(jsonString)

// Map to DidMethod
val method = RegistryEntryMapper.mapToDidMethod(entry, kms)

// Register and use
registry.register(method)
val result = registry.resolve("did:web:example.com")
```

## Implementation Selection

When multiple implementations are provided, the mapper selects:

1. **Priority**: Non-testnet implementations (`testNet: false`)
2. **Fallback**: First implementation with a `driverUrl`
3. **Error**: Returns null if no implementation has a `driverUrl`

## Protocol Adapter Detection

The protocol adapter is automatically determined:

- **GoDiddy**: If implementation name or URL contains "godiddy"
- **Standard**: All other cases (default Universal Resolver protocol)

## Capabilities

Currently, JSON-registered methods support:
- ✅ **Resolution**: Automatically enabled if `driverUrl` is provided
- ❌ **Create**: Requires native implementation
- ❌ **Update**: Requires native implementation  
- ❌ **Deactivate**: Requires native implementation

## Examples

### Single Implementation

```json
{
  "name": "web",
  "implementations": [
    {
      "driverUrl": "https://dev.uniresolver.io"
    }
  ]
}
```

### Multiple Implementations (Testnet + Mainnet)

```json
{
  "name": "ethr",
  "implementations": [
    {
      "name": "Universal Resolver (Mainnet)",
      "driverUrl": "https://dev.uniresolver.io",
      "testNet": false
    },
    {
      "name": "Universal Resolver (Testnet)",
      "driverUrl": "https://testnet.uniresolver.io",
      "testNet": true
    }
  ]
}
```

The mapper will automatically select the mainnet implementation.

### GoDiddy Implementation

```json
{
  "name": "example",
  "implementations": [
    {
      "name": "GoDiddy",
      "driverUrl": "https://api.godiddy.com"
    }
  ]
}
```

The mapper automatically detects GoDiddy from the URL and uses the appropriate adapter.

## Backward Compatibility

The loader also supports the legacy Trustweave format (with `driver` and `capabilities` fields) for backward compatibility, but the official registry format is recommended.

