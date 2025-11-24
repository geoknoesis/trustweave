# DID Method Registration Files

This directory contains JSON registration files for DID methods following the official [DID Registration specification](https://identity.foundation/did-registration/).

## Adding a New DID Method

To add support for a new DID method, create a JSON file in this directory using the **official DID Method Registry format**:

```json
{
  "name": "method-name",
  "status": "implemented",
  "specification": "https://example.com/did-method-spec",
  "contact": {
    "name": "Method Maintainer",
    "email": "contact@example.com",
    "url": "https://example.com"
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

This format matches the official DID Method Registry structure, making it easy to copy entries directly from the registry.

## Implementation Configuration

### Implementations Array

The `implementations` array lists available resolver services for this DID method.

**Required fields:**
- `driverUrl`: URL to the resolver service (typically a Universal Resolver endpoint)

**Optional fields:**
- `name`: Name of the implementation (e.g., "Universal Resolver", "GoDiddy")
- `testNet`: Whether this is a test network implementation (default: false)

**Example with multiple implementations:**
```json
{
  "implementations": [
    {
      "name": "Universal Resolver",
      "driverUrl": "https://dev.uniresolver.io",
      "testNet": false
    },
    {
      "name": "GoDiddy",
      "driverUrl": "https://api.godiddy.com",
      "testNet": false
    }
  ]
}
```

**Implementation Selection:**
- Non-testnet implementations are preferred
- The first implementation with a `driverUrl` is used if no preference can be determined
- Protocol adapter is automatically determined from implementation name or URL (e.g., "godiddy" in URL → GoDiddy adapter)

## How It Works

1. **Registry Format**: You provide the official registry format with `implementations[].driverUrl`
2. **Automatic Mapping**: Trustweave automatically:
   - Extracts the resolver URL from `driverUrl`
   - Determines the protocol adapter (standard or godiddy)
   - Creates an `HttpDidMethod` that uses the resolver for DID resolution
3. **Capabilities**: Resolution is automatically enabled if a `driverUrl` is provided. Other capabilities (create, update, deactivate) require native implementations.

## Loading Methods

Methods can be loaded programmatically:

```kotlin
val loader = JsonDidMethodLoader(kms)
val methods = loader.loadFromClasspath("did-methods")
methods.forEach { registry.register(it) }
```

Or via the `JsonDidMethodProvider` SPI:

```kotlin
val provider = JsonDidMethodProvider.fromClasspath(kms)
// Methods are automatically available via DidMethodProvider SPI
```

## JSON Schema

The JSON structure is defined by `schema.json` in this directory. This schema validates:
- Required fields (name, driver)
- Field types and formats
- Enum values (driver.type, protocolAdapter)
- Field constraints (name pattern, timeout range)

## Documentation

For detailed information on how JSON maps to `DidMethod` implementations, see:
- `SPECIFICATION.md` - Complete specification with code examples
- `QUICK_REFERENCE.md` - Quick reference guide with mappings
- `schema.json` - JSON Schema definition

