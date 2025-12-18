# TrustWeave Distribution Module

This module provides all essential TrustWeave dependencies in a single dependency declaration.

## Usage

Instead of adding multiple dependencies individually, use the distribution module:

```kotlin
dependencies {
    implementation("com.trustweave:distribution-all:1.0.0-SNAPSHOT")
}
```

## What's Included

- ✅ Core types and utilities
- ✅ JSON canonicalization
- ✅ Key management abstraction
- ✅ DID management
- ✅ Blockchain anchoring
- ✅ Testing utilities (testkit)

## What's NOT Included

Optional modules must be added explicitly if needed:

- ❌ Blockchain adapters (Algorand, Polygon, etc.)
- ❌ Integration modules (walt.id, GoDiddy)

### Adding Optional Modules

```kotlin
dependencies {
    implementation("com.trustweave:distribution-all:1.0.0-SNAPSHOT")

    // Add blockchain adapters as needed
    implementation("com.trustweave.chains:algorand:1.0.0-SNAPSHOT")
}
```

## Quick Start

```kotlin
import com.trustweave.trust.dsl.trustWeave
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    trustWeave {
        keys { provider("inMemory"); algorithm("Ed25519") }
        did { method("key") { algorithm("Ed25519") } }
    }.run {
        val (did, _) = createDid { method("key") }.getOrThrow()
        println("Created DID: ${did.value}")
    }
}
```

See the main [README](../../README.md) for more examples and documentation.

