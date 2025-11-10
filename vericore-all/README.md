# VeriCore All-in-One Module

This module provides all essential VeriCore dependencies in a single dependency declaration.

## Usage

Instead of adding multiple dependencies:

```kotlin
dependencies {
    implementation("io.geoknoesis.vericore:vericore-core:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-json:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-kms:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-did:1.0.0-SNAPSHOT")
    implementation("io.geoknoesis.vericore:vericore-anchor:1.0.0-SNAPSHOT")
    testImplementation("io.geoknoesis.vericore:vericore-testkit:1.0.0-SNAPSHOT")
}
```

You can simply use:

```kotlin
dependencies {
    implementation("io.geoknoesis.vericore:vericore-all:1.0.0-SNAPSHOT")
}
```

## What's Included

- ✅ `vericore-core` - Core types and utilities
- ✅ `vericore-json` - JSON canonicalization
- ✅ `vericore-kms` - Key management abstraction
- ✅ `vericore-did` - DID management
- ✅ `vericore-anchor` - Blockchain anchoring
- ✅ `vericore-testkit` - Testing utilities

## What's NOT Included

Optional modules must be added explicitly if needed:

- ❌ `vericore-waltid` - walt.id integration
- ❌ `vericore-godiddy` - GoDiddy integration
- ❌ `vericore-algorand` - Algorand blockchain adapter
- ❌ `vericore-polygon` - Polygon blockchain adapter
- ❌ `vericore-ganache` - Ganache local blockchain
- ❌ `vericore-indy` - Hyperledger Indy adapter

### Adding Optional Modules

```kotlin
dependencies {
    implementation("io.geoknoesis.vericore:vericore-all:1.0.0-SNAPSHOT")
    
    // Add blockchain adapters as needed
    implementation("io.geoknoesis.vericore:vericore-algorand:1.0.0-SNAPSHOT")
    
    // Add integration modules as needed
    implementation("io.geoknoesis.vericore:vericore-waltid:1.0.0-SNAPSHOT")
}
```

## Quick Start

```kotlin
import io.geoknoesis.vericore.VeriCore
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val vericore = VeriCore.create()
    val did = vericore.createDid().getOrThrow()
    println("Created DID: ${did.id}")
}
```

See the main [README](../README.md) for more examples and documentation.

