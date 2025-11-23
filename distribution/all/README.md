# TrustWeave All-in-One Module

This module provides all essential TrustWeave dependencies in a single dependency declaration.

## Usage

Instead of adding multiple dependencies:

```kotlin
dependencies {
    implementation("com.trustweave:TrustWeave-core:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-json:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-kms:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-did:1.0.0-SNAPSHOT")
    implementation("com.trustweave:TrustWeave-anchor:1.0.0-SNAPSHOT")
    testImplementation("com.trustweave:TrustWeave-testkit:1.0.0-SNAPSHOT")
}
```

You can simply use:

```kotlin
dependencies {
    implementation("com.trustweave:TrustWeave-all:1.0.0-SNAPSHOT")
}
```

## What's Included

- ✅ `TrustWeave-core` - Core types and utilities
- ✅ `TrustWeave-json` - JSON canonicalization
- ✅ `TrustWeave-kms` - Key management abstraction
- ✅ `TrustWeave-did` - DID management
- ✅ `TrustWeave-anchor` - Blockchain anchoring
- ✅ `TrustWeave-testkit` - Testing utilities

## What's NOT Included

Optional modules must be added explicitly if needed:

- ❌ `TrustWeave-waltid` - walt.id integration
- ❌ `TrustWeave-godiddy` - GoDiddy integration
- ❌ `TrustWeave-algorand` - Algorand blockchain adapter
- ❌ `TrustWeave-polygon` - Polygon blockchain adapter
- ❌ `TrustWeave-ganache` - Ganache local blockchain
- ❌ `TrustWeave-indy` - Hyperledger Indy adapter

### Adding Optional Modules

```kotlin
dependencies {
    implementation("com.trustweave:TrustWeave-all:1.0.0-SNAPSHOT")
    
    // Add blockchain adapters as needed
    implementation("com.trustweave:TrustWeave-algorand:1.0.0-SNAPSHOT")
    
    // Add integration modules as needed
    implementation("com.trustweave:TrustWeave-waltid:1.0.0-SNAPSHOT")
}
```

## Quick Start

```kotlin
import com.trustweave.TrustWeave
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val TrustWeave = trustweave.create()
    val did = trustweave.createDid().getOrThrow()
    println("Created DID: ${did.id}")
}
```

See the main [README](../README.md) for more examples and documentation.

