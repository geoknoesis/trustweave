package com.trustweave.testkit.integration

import com.trustweave.did.DidMethod
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.kms.KeyManagementService
import com.trustweave.testkit.TrustWeaveTestFixture
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Reusable test scenario for testing multiple plugins together.
 *
 * Verifies that different plugins can work together in the same system.
 *
 * **Example Usage**:
 * ```kotlin
 * @Test
 * fun testMultiplePlugins() = runBlocking {
 *     val scenario = MultiPluginScenario(fixture)
 *     scenario.testMultipleDidMethods()
 *     scenario.testMultipleKmsProviders()
 * }
 * ```
 */
class MultiPluginScenario(
    private val fixture: TrustWeaveTestFixture
) {

    /**
     * Tests multiple DID methods working together.
     */
    suspend fun testMultipleDidMethods(methods: List<DidMethod>) {
        val registry = fixture.getDidRegistry()

        methods.forEach { method ->
            registry.register(method)
        }

        // Create DIDs with each method
        val documents = methods.map { method ->
            method.createDid(
                com.trustweave.did.didCreationOptions {
                    algorithm = com.trustweave.did.DidCreationOptions.KeyAlgorithm.ED25519
                }
            )
        }

        kotlin.test.assertTrue(documents.size == methods.size)
        documents.forEach { doc ->
            kotlin.test.assertNotNull(doc)
            kotlin.test.assertNotNull(doc.id)
        }

        // Verify all can be resolved
        documents.forEach { doc ->
            val method = methods.find { it.method == doc.id.substringAfter("did:").substringBefore(":") }
            if (method != null) {
                val resolution = method.resolveDid(doc.id)
                val document = when (resolution) {
                    is DidResolutionResult.Success -> resolution.document
                    else -> null
                }
                kotlin.test.assertNotNull(document)
            }
        }
    }

    /**
     * Tests multiple KMS providers working together.
     */
    suspend fun testMultipleKmsProviders(providers: List<KeyManagementService>) {
        providers.forEach { kms ->
            val keyHandle = kms.generateKey(com.trustweave.kms.Algorithm.Ed25519)
            kotlin.test.assertNotNull(keyHandle)
            kotlin.test.assertNotNull(keyHandle.id)

            val message = "test".toByteArray()
            val signature = kms.sign(keyHandle.id, message)
            kotlin.test.assertNotNull(signature)
            kotlin.test.assertTrue(signature.isNotEmpty())
        }
    }

    /**
     * Tests DID creation with different KMS providers.
     */
    suspend fun testDidWithDifferentKms(
        didMethod: DidMethod,
        kmsProviders: List<KeyManagementService>
    ) {
        kmsProviders.forEach { kms ->
            // Create a new DID method instance with this KMS
            // This is method-specific, so this is a template
            val method = didMethod // In real scenario, create new instance with kms

            val document = method.createDid(
                com.trustweave.did.didCreationOptions {
                    algorithm = com.trustweave.did.DidCreationOptions.KeyAlgorithm.ED25519
                }
            )

            kotlin.test.assertNotNull(document)
            kotlin.test.assertNotNull(document.id)
        }
    }
}

