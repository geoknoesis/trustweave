# DID Method Registration - Quick Reference

## JSON Structure → DidMethod Implementation

```
┌─────────────────────────────────────────────────────────────┐
│                    JSON Registration File                   │
│                                                             │
│  {                                                          │
│    "name": "web",          ───────────────┐                 │
│    "driver": {                            │                 │
│      "type": "universal-resolver",        │                 │
│      "baseUrl": "https://...",            │                 │
│      "protocolAdapter": "standard"        │                 │
│    },                                     │                 │
│    "capabilities": {                      │                 │
│      "resolve": true                      │                 │
│    }                                      │                 │
│  }                                        │                 │
└───────────────────────────────────────────┼─────────────────┘
                                            │
                                            │ Parsed by
                                            │ DidRegistrationSpecParser
                                            ▼
┌─────────────────────────────────────────────────────────────┐
│              DidRegistrationSpec (Data Class)                │
│                                                               │
│  - name: String                    → method property        │
│  - driver: DriverConfig            → UniversalResolver       │
│  - capabilities: MethodCapabilities → Method implementations│
└───────────────────────────────────────────┼─────────────────┘
                                            │
                                            │ Used to create
                                            ▼
┌─────────────────────────────────────────────────────────────┐
│              HttpDidMethod (DidMethod impl)                  │
│                                                               │
│  class HttpDidMethod(                                         │
│    registrationSpec: DidRegistrationSpec,                    │
│    kms: KeyManagementService                                 │
│  ) : AbstractDidMethod(                                      │
│    registrationSpec.name  ←─── "web"                         │
│  ) {                                                         │
│                                                               │
│    override val method = "web"                               │
│                                                               │
│    private val universalResolver =                           │
│      DefaultUniversalResolver(                               │
│        baseUrl = spec.driver.baseUrl,                       │
│        protocolAdapter = createAdapter(...)                   │
│      )                                                       │
│                                                               │
│    override suspend fun resolveDid(did: String) {            │
│      if (capabilities.resolve) {                            │
│        universalResolver.resolveDid(did)                     │
│      }                                                       │
│    }                                                         │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
```

## Field Mappings

| JSON Field | Path | Maps To | Type |
|------------|------|---------|------|
| `name` | `root.name` | `DidMethod.method` | String |
| `driver.type` | `root.driver.type` | Driver selection | "universal-resolver" |
| `driver.baseUrl` | `root.driver.baseUrl` | `DefaultUniversalResolver.baseUrl` | URI String |
| `driver.protocolAdapter` | `root.driver.protocolAdapter` | Protocol adapter instance | "standard" \| "godiddy" |
| `driver.timeout` | `root.driver.timeout` | `DefaultUniversalResolver.timeout` | Integer (seconds) |
| `capabilities.resolve` | `root.capabilities.resolve` | `resolveDid()` implementation | Boolean |
| `capabilities.create` | `root.capabilities.create` | `createDid()` implementation | Boolean |
| `capabilities.update` | `root.capabilities.update` | `updateDid()` implementation | Boolean |
| `capabilities.deactivate` | `root.capabilities.deactivate` | `deactivateDid()` implementation | Boolean |

## Code Flow

```kotlin
// 1. Load JSON
val json = """
{
  "name": "web",
  "driver": { "type": "universal-resolver", "baseUrl": "https://..." },
  "capabilities": { "resolve": true }
}
"""

// 2. Parse to Data Class
val spec = DidRegistrationSpecParser.parse(json)
// → DidRegistrationSpec(name="web", driver=..., capabilities=...)

// 3. Create DidMethod
val method = HttpDidMethod(spec, kms)
// → HttpDidMethod with method="web"

// 4. Register
registry.register(method)

// 5. Use
registry.resolve("did:web:example.com")
// → method.resolveDid("did:web:example.com")
// → universalResolver.resolveDid("did:web:example.com")
// → HTTP GET https://.../1.0/identifiers/did:web:example.com
```

## Minimal JSON Example

```json
{
  "name": "example",
  "driver": {
    "type": "universal-resolver",
    "baseUrl": "https://dev.uniresolver.io"
  }
}
```

This creates a `DidMethod` that:
- Has method name "example"
- Resolves DIDs via Universal Resolver at `https://dev.uniresolver.io`
- Uses standard protocol adapter
- Only supports `resolveDid()` (default capabilities)

## Full JSON Example

```json
{
  "name": "web",
  "status": "implemented",
  "specification": "https://w3c-ccg.github.io/did-method-web/",
  "contact": {
    "name": "W3C CCG",
    "email": "contact@example.com"
  },
  "driver": {
    "type": "universal-resolver",
    "baseUrl": "https://dev.uniresolver.io",
    "protocolAdapter": "standard",
    "timeout": 30,
    "apiKey": "optional-key"
  },
  "capabilities": {
    "create": false,
    "resolve": true,
    "update": false,
    "deactivate": false
  }
}
```

## Validation Rules

- `name`: Required, must match pattern `^[a-z0-9]+$`, 1-50 characters
- `driver.type`: Required, must be "universal-resolver" (for JSON registration)
- `driver.baseUrl`: Required if `type` is "universal-resolver", must be valid URI
- `driver.protocolAdapter`: Optional, defaults to "standard"
- `driver.timeout`: Optional, 1-300 seconds, defaults to 30
- `capabilities`: Optional, defaults to `{ "resolve": true }` only

## See Also

- `SPECIFICATION.md` - Detailed specification
- `schema.json` - JSON Schema definition
- `README.md` - Usage guide

