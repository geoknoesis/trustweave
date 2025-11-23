# Testing Guide for walt.id Integration

## Running Tests

### Run All Tests
```bash
gradle test
```

### Run Only walt.id Adapter Tests
```bash
gradle :TrustWeave-waltid:test
```

### Run Specific Test Classes
```bash
# SPI discovery tests
gradle :TrustWeave-waltid:test --tests "SpiDiscoveryTest"

# End-to-end integration tests
gradle :TrustWeave-waltid:test --tests "WaltIdEndToEndTest"

# Error handling tests
gradle :TrustWeave-waltid:test --tests "WaltIdErrorHandlingTest"

# KMS adapter tests
gradle :TrustWeave-waltid:test --tests "WaltIdKeyManagementServiceTest"

# DID method tests
gradle :TrustWeave-waltid:test --tests "WaltIdDidMethodTest"
```

## Test Coverage

### 1. SPI Discovery Tests (`SpiDiscoveryTest`)
- Verifies walt.id providers are discoverable via Java ServiceLoader
- Tests META-INF/services files are correctly configured
- Validates provider creation and configuration

### 2. Unit Tests (`WaltIdKeyManagementServiceTest`, `WaltIdDidMethodTest`)
- Tests individual adapter implementations
- Verifies key generation, signing, DID creation, resolution
- Tests error handling for invalid inputs

### 3. Integration Tests (`WaltIdIntegrationTest`)
- Tests `WaltIdIntegration.setup()` and `discoverAndRegister()`
- Verifies DID methods are registered correctly
- Tests end-to-end workflows

### 4. End-to-End Tests (`WaltIdEndToEndTest`)
- Complete workflows using walt.id adapters
- DID creation and resolution
- Multiple DID methods coexistence
- Integration with TrustWeave registries

### 5. Error Handling Tests (`WaltIdErrorHandlingTest`)
- Tests exception handling
- Invalid input validation
- Missing resource handling

## Manual Testing

### Test SPI Discovery
```kotlin
import com.trustweave.waltid.WaltIdIntegration
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val result = WaltIdIntegration.discoverAndRegister()
    println("KMS: ${result.kms}")
    println("Registered methods: ${result.registeredDidMethods}")
}
```

### Test DID Creation
```kotlin
import com.trustweave.waltid.WaltIdIntegration
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val result = WaltIdIntegration.discoverAndRegister()
    val registry = result.registry

    val keyMethod = registry.get("key")
    val document = keyMethod!!.createDid()
    println("Created DID: ${document.id}")

    val resolved = registry.resolve(document.id)
    println("Resolved document: ${resolved.document?.id}")
}
```

## Testing with Real walt.id Libraries

Once walt.id dependencies are configured:

1. **Uncomment dependencies** in `TrustWeave-waltid/build.gradle.kts`
2. **Update adapter implementations** to use real walt.id APIs
3. **Run tests** to verify integration:
   ```bash
   gradle :TrustWeave-waltid:test
   ```

## Test Results

Current test status: ✅ All tests passing

- SPI Discovery: ✅
- Unit Tests: ✅
- Integration Tests: ✅
- End-to-End Tests: ✅
- Error Handling: ✅

