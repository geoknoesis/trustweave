# DID Method Registration JSON Specification

This document explains how JSON registration files following the **official DID Registration specification** (https://identity.foundation/did-registration/) are used to create `DidMethod` implementations in Trustweave.

## Overview

Trustweave supports the **official DID Method Registry format** from identity.foundation/did-registration. The JSON file defines:
1. **Method Identity**: The method name and metadata (name, status, specification, contact)
2. **Implementations**: Available resolver services with `driverUrl` pointing to resolver endpoints
3. **Automatic Mapping**: Trustweave automatically maps registry entries to `HttpDidMethod` implementations

## JSON to DidMethod Mapping

### 1. Method Identity

```json
{
  "name": "example"
}
```

**Maps to:** an `org.trustweave.did.registrar.method.HttpDidMethod` constructed from a `DidRegistrationSpec` whose `name` is `"example"`. The class implements `DidMethod` and does not extend a shared `AbstractDidMethod` base type.

The `name` field becomes the method identifier used in DID strings: `did:example:123`

### 2. Driver Configuration

The `driver` object determines how the DID method operations are implemented:

```json
{
  "driver": {
    "type": "universal-resolver",
    "baseUrl": "https://dev.uniresolver.io",
    "protocolAdapter": "standard",
    "timeout": 30
  }
}
```

**Maps to:**
```kotlin
// Creates a UniversalResolver instance
private val universalResolver: UniversalResolver = createUniversalResolver()

private fun createUniversalResolver(): UniversalResolver {
    val driver = registrationSpec.driver
    val baseUrl = driver.baseUrl  // "https://dev.uniresolver.io"
    val protocolAdapter = createProtocolAdapter(driver.protocolAdapter)  // "standard"
    val timeout = driver.timeout ?: 30

    return DefaultUniversalResolver(
        baseUrl = baseUrl,
        timeout = timeout,
        protocolAdapter = protocolAdapter
    )
}
```

**Driver Types:**
- `universal-resolver`: Creates a `DefaultUniversalResolver` that delegates to an external Universal Resolver instance
- `native`: Requires custom code (not supported via JSON)
- `custom`: Requires additional configuration (not supported via JSON)

### 3. Capabilities Mapping

The `capabilities` object maps directly to `DidMethod` interface methods:

```json
{
  "capabilities": {
    "create": false,
    "resolve": true,
    "update": false,
    "deactivate": false
  }
}
```

**Maps to DidMethod Interface:**

| JSON Field | DidMethod Method | Implementation |
|------------|------------------|----------------|
| `capabilities.create` | `createDid(options)` | Throws exception if `false`, otherwise delegates (not yet implemented) |
| `capabilities.resolve` | `resolveDid(did)` | Delegates to `UniversalResolver.resolveDid()` if `true` |
| `capabilities.update` | `updateDid(did, updater)` | Throws exception if `false`, otherwise delegates (not yet implemented) |
| `capabilities.deactivate` | `deactivateDid(did)` | Throws exception if `false`, otherwise delegates (not yet implemented) |

**Example (conceptual):** `resolveDid` takes a type-safe `Did`, checks `capabilities?.resolve`, validates the DID method prefix, then delegates to the configured `UniversalResolver`.

## Complete Flow: JSON → DidMethod

### Step 1: Parse JSON

```kotlin
val jsonString = """
{
  "name": "web",
  "driver": {
    "type": "universal-resolver",
    "baseUrl": "https://dev.uniresolver.io"
  },
  "capabilities": {
    "resolve": true
  }
}
"""

val spec = DidRegistrationSpecParser.parse(jsonString)
// Creates: DidRegistrationSpec(name="web", driver=DriverConfig(...), capabilities=MethodCapabilities(resolve=true))
```

### Step 2: Create DidMethod Instance

```kotlin
val method = HttpDidMethod(registrationSpec = spec)
// Creates a DidMethod with:
// - method = "web"
// - internal Universal Resolver client from driver.baseUrl / protocolAdapter
// - capabilities = MethodCapabilities(resolve=true)
```

### Step 3: Register and Use

```kotlin
registry.register(method)

// Now you can use it
val result = registry.resolve("did:web:example.com")
// Internally calls: method.resolveDid("did:web:example.com")
// Which calls: universalResolver.resolveDid("did:web:example.com")
```

## Field Reference

### Required Fields

| Field | Type | Description | Maps To |
|-------|------|-------------|---------|
| `name` | string | DID method name | `DidMethod.method` property |
| `driver` | object | Driver configuration | Internal `DefaultUniversalResolver` inside `HttpDidMethod` |

### Optional Fields

| Field | Type | Description | Maps To |
|-------|------|-------------|---------|
| `status` | string | Implementation status | Metadata only |
| `specification` | string (URI) | Spec URL | Metadata only |
| `contact` | object | Contact info | Metadata only |
| `capabilities` | object | Supported operations | `DidMethod` method implementations |
| `driver.baseUrl` | string (URI) | Universal Resolver URL | `DefaultUniversalResolver.baseUrl` |
| `driver.protocolAdapter` | string | Protocol adapter name | `DefaultUniversalResolver.protocolAdapter` |
| `driver.timeout` | integer | Request timeout | `DefaultUniversalResolver.timeout` |
| `driver.apiKey` | string | API key | `DefaultUniversalResolver.apiKey` |

## Implementation Details

### HttpDidMethod Class

`org.trustweave.did.registrar.method.HttpDidMethod` implements `DidMethod` for HTTP-backed Universal Resolver (and optional Universal Registrar) workflows:

```kotlin
class HttpDidMethod(
    val registrationSpec: DidRegistrationSpec,
    private val registrar: DidRegistrar? = null,
    private val additionalAdapters: Map<String, UniversalResolverProtocolAdapter> = emptyMap(),
) : DidMethod
```

Resolution uses `DefaultUniversalResolver` built from `registrationSpec.driver`. When a registrar is available (injected or derived from `driver.registrarUrl` on registry entries), create/update/deactivate can delegate to that registrar instead of failing as “not implemented.”

### Protocol Adapters

The `driver.protocolAdapter` field determines which protocol adapter is used:

- `"standard"`: Uses `StandardUniversalResolverAdapter` (default)
  - Endpoint: `/1.0/identifiers/{did}`
  - Used by: dev.uniresolver.io, most Universal Resolver instances

- `"godiddy"`: Uses `GodiddyProtocolAdapter` (requires godiddy plugin)
  - Endpoint: `/1.0.0/universal-resolver/identifiers/{did}`
  - Used by: GoDiddy service

## Limitations

1. **Driver surface**: Legacy JSON with a `driver` object is limited to `type = "universal-resolver"` when using `JsonDidMethodLoader` for full specs; other driver types need a native `DidMethod` / `DidMethodProvider`.

2. **Universal Resolver dependency**: Resolver-backed methods need an HTTP endpoint (Universal Resolver or compatible) that supports the method.

3. **Protocol adapters**: Built-in adapter keys include `"standard"` and `"godiddy"`; additional resolver protocols can be supplied via `HttpDidMethod`’s `additionalAdapters` map in code.

## Example: Complete JSON → DidMethod

**Input JSON:**
```json
{
  "name": "ion",
  "status": "implemented",
  "specification": "https://identity.foundation/ion/",
  "driver": {
    "type": "universal-resolver",
    "baseUrl": "https://dev.uniresolver.io",
    "protocolAdapter": "standard",
    "timeout": 30
  },
  "capabilities": {
    "resolve": true
  }
}
```

**Resulting DidMethod:**
```kotlin
// Equivalent to:
val method = HttpDidMethod(
    registrationSpec = DidRegistrationSpec(
        name = "ion",
        status = "implemented",
        specification = "https://identity.foundation/ion/",
        driver = DriverConfig(
            type = "universal-resolver",
            baseUrl = "https://dev.uniresolver.io",
            protocolAdapter = "standard",
            timeout = 30
        ),
        capabilities = MethodCapabilities(resolve = true)
    )
)

// method.method == "ion"
// method.resolveDid(Did("did:ion:...")) delegates to Universal Resolver
```

## Validation

The JSON is validated against the schema (`schema.json`) when parsed. Invalid JSON will throw an `IllegalArgumentException` with details about the validation error.

