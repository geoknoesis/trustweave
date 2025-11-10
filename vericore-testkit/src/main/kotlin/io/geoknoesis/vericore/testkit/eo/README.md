# EO Test Integration with TestContainers

This module provides reusable utilities for Earth Observation (EO) integration testing with TestContainers support.

## Overview

The EO test integration utilities allow you to easily create and test complete EO data integrity workflows using any blockchain adapter with TestContainers. This eliminates the need to manually set up blockchain nodes for testing.

## Features

- **Reusable EO Test Scenarios**: Pre-built test scenarios for EO data integrity verification
- **TestContainers Support**: Automatic container management for blockchain nodes
- **Base Test Class**: Abstract base class for easy test implementation
- **Complete Workflow**: Handles DID creation, artifact generation, Linkset creation, VC issuance, anchoring, and verification

## Components

### EoTestIntegration

Main utility object providing:
- `createScenario()`: Creates a complete EO test scenario with all components
- `executeScenario()`: Executes a scenario (anchors and verifies)
- `runCompleteScenario()`: Convenience method that creates and executes in one call

### BaseEoIntegrationTest

Abstract base class for EO integration tests:
- Handles common setup/teardown
- Provides `runEoTestScenario()` method
- Subclasses implement `createAnchorClient()` for blockchain-specific setup

### EoTestScenario

Data class containing all components of an EO test scenario:
- Artifacts (metadata, provenance, quality report)
- Linkset
- Verifiable Credential
- Digest payload for anchoring

### EoTestResult

Result of executing an EO test scenario:
- Anchor result (blockchain transaction)
- Verification result (integrity chain verification)
- Original scenario

## Usage

### Basic Usage with TestContainers

```kotlin
class MyBlockchainEoIntegrationTest : BaseEoIntegrationTest() {
    
    companion object {
        @JvmStatic
        lateinit var blockchainContainer: BlockchainContainer
        
        @JvmStatic
        @BeforeAll
        fun startContainer() {
            blockchainContainer = BlockchainContainer().apply {
                start()
            }
        }
        
        @JvmStatic
        @AfterAll
        fun stopContainer() {
            blockchainContainer.stop()
        }
    }
    
    override fun createAnchorClient(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient {
        val rpcUrl = blockchainContainer.getRpcUrl()
        val privateKey = blockchainContainer.getPrivateKey()
        
        return MyBlockchainAnchorClient(
            chainId = chainId,
            options = mapOf(
                "rpcUrl" to rpcUrl,
                "privateKey" to privateKey
            )
        )
    }
    
    override fun getChainId(): String = "myblockchain:testnet"
    
    @Test
    fun `test EO scenario`() = runBlocking {
        val result = runEoTestScenario()
        assertTrue(result.verificationResult.valid)
    }
}
```

### Step-by-Step Usage

```kotlin
@Test
fun `test EO scenario step by step`() = runBlocking {
    // Setup DID method
    val kms = InMemoryKeyManagementService()
    val didMethod = DidKeyMockMethod(kms)
    didRegistry.register(didMethod)
    
    // Create issuer DID
    val issuerDoc = didMethod.createDid(mapOf("algorithm" to "Ed25519"))
    val issuerDid = issuerDoc.id
    
    // Create anchor client
    val chainId = "testnet:local"
    val anchorClient = createAnchorClient(chainId, getAnchorClientOptions())
    blockchainRegistry.register(chainId, anchorClient)
    
    // Create scenario
    val scenario = EoTestIntegration.createScenario(
        issuerDid = issuerDid,
        anchorClient = anchorClient,
        chainId = chainId,
        datasetId = "my-dataset",
        metadataTitle = "My EO Dataset"
    )
    
    // Execute scenario
    val result = EoTestIntegration.executeScenario(scenario, blockchainRegistry)
    
    // Verify results
    assertTrue(result.verificationResult.valid)
    assertEquals(5, result.verificationResult.steps.size) // VC + Linkset + 3 artifacts
}
```

### Custom Dataset

```kotlin
@Test
fun `test with custom dataset`() = runBlocking {
    val result = runEoTestScenario(
        datasetId = "custom-dataset-123",
        metadataTitle = "Custom EO Dataset",
        metadataDescription = "A custom dataset for testing"
    )
    
    assertTrue(result.verificationResult.valid)
    
    // Verify custom dataset ID
    val vcSubject = result.scenario.vc["credentialSubject"]?.jsonObject
    assertEquals("custom-dataset-123", vcSubject?.get("id")?.jsonPrimitive?.content)
}
```

## Example: Ganache Integration

See `GanacheEoTestIntegrationExample` for a complete example using Ganache with TestContainers:

```kotlin
class GanacheEoTestIntegrationExample : BaseEoIntegrationTest() {
    
    companion object {
        @JvmStatic
        lateinit var ganacheContainer: GanacheContainer
        
        @JvmStatic
        @BeforeAll
        fun startGanache() {
            ganacheContainer = GanacheContainer().apply {
                start()
            }
        }
        
        @JvmStatic
        @AfterAll
        fun stopGanache() {
            ganacheContainer.stop()
        }
    }
    
    override fun createAnchorClient(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient {
        val rpcUrl = ganacheContainer.getRpcUrl()
        val privateKey = ganacheContainer.getFirstAccountPrivateKey()
        
        return GanacheBlockchainAnchorClient(
            chainId = chainId,
            options = mapOf(
                "rpcUrl" to rpcUrl,
                "privateKey" to privateKey
            )
        )
    }
    
    override fun getChainId(): String = GanacheBlockchainAnchorClient.LOCAL
}
```

## Test Workflow

The EO test integration follows this workflow:

1. **Setup**: Create DID method and issuer DID
2. **Create Artifacts**: Generate metadata, provenance, and quality report artifacts
3. **Create Linkset**: Build Linkset linking all artifacts
4. **Create VC**: Build Verifiable Credential referencing Linkset
5. **Anchor**: Anchor VC digest to blockchain
6. **Verify**: Verify complete integrity chain (Blockchain → VC → Linkset → Artifacts)

## Dependencies

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    testImplementation(project(":vericore-testkit"))
    testImplementation(project(":vericore-ganache")) // For Ganache example
    // Add your blockchain adapter module here
}
```

## Benefits

- **Reusable**: Write once, use with any blockchain adapter
- **Automatic**: TestContainers handles container lifecycle
- **Complete**: Tests entire EO workflow end-to-end
- **Flexible**: Customize datasets, metadata, and test scenarios
- **Isolated**: Each test runs in its own container instance

## See Also

- [Earth Observation Scenario Documentation](../../docs/getting-started/earth-observation-scenario.md)
- [TestContainers Documentation](https://www.testcontainers.org/)
- [Ganache Integration Example](../../vericore-ganache/README.md)

