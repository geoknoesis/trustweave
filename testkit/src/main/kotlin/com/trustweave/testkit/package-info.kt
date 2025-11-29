package com.trustweave.testkit

/**
 * Test utilities and in-memory implementations for trustweave.
 *
 * This module provides:
 *
 * ## In-Memory Implementations
 * - [InMemoryBlockchainAnchorClient]: In-memory blockchain anchor client for testing
 * - [InMemoryKeyManagementService]: In-memory key management service for testing
 * - [DidKeyMockMethod]: Mock DID method implementation for testing
 *
 * ## Test Fixtures
 * - [TrustWeaveTestFixture]: Comprehensive test fixture builder for setting up complete test environments
 *
 * ## EO Test Integration
 * - [EoTestIntegration]: Reusable EO test scenarios with TestContainers support
 * - [BaseEoIntegrationTest]: Base class for EO integration tests
 *
 * ## Integrity Verification
 * - [IntegrityVerifier]: Utilities for verifying integrity chains
 * - [TestDataBuilders]: Builders for creating VC, Linkset, and artifact structures
 *
 * ## Usage Examples
 *
 * ### Basic Test Fixture
 * ```
 * val fixture = TrustWeaveTestFixture.builder()
 *     .withInMemoryBlockchainClient("algorand:testnet")
 *     .build()
 *
 * val issuerDoc = fixture.createIssuerDid()
 * ```
 *
 * ### EO Integration Test
 * ```
 * class MyEoTest : BaseEoIntegrationTest() {
 *     override fun createAnchorClient(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient {
 *         return InMemoryBlockchainAnchorClient(chainId)
 *     }
 *
 *     @Test
 *     fun testEoScenario() = runBlocking {
 *         val result = runEoTestScenario()
 *         assertTrue(result.verificationResult.valid)
 *     }
 * }
 * ```
 */

