# vericore-core

The `vericore-core` module provides shared types, exceptions, and common utilities used across all VeriCore modules.

## Overview

This module has no dependencies and serves as the foundation for all other VeriCore modules.

## Key Components

### Exceptions

#### VeriCoreException

Base exception class for all VeriCore exceptions.

```kotlin
class VeriCoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

#### NotFoundException

Thrown when a requested resource is not found.

```kotlin
class NotFoundException(message: String, cause: Throwable? = null) : VeriCoreException(message, cause)
```

#### InvalidOperationException

Thrown when an invalid operation is attempted.

```kotlin
class InvalidOperationException(message: String, cause: Throwable? = null) : VeriCoreException(message, cause)
```

### Constants

#### VeriCoreConstants

Common constants used throughout VeriCore.

```kotlin
object VeriCoreConstants {
    const val DEFAULT_TIMEOUT = 30000L
    const val DEFAULT_ALGORITHM = "Ed25519"
    // ... more constants
}
```

## Usage

```kotlin
import io.geoknoesis.vericore.core.VeriCoreException
import io.geoknoesis.vericore.core.NotFoundException

try {
    // Your code
} catch (e: NotFoundException) {
    // Handle not found
} catch (e: VeriCoreException) {
    // Handle other VeriCore exceptions
}
```

## Dependencies

None - this is the base module.

## Next Steps

- Learn about [vericore-json](vericore-json.md)
- Explore [API Reference](../api-reference/core-api.md)

