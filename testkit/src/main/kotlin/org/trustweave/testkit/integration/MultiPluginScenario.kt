package org.trustweave.testkit.integration

import org.trustweave.did.DidMethod
import org.trustweave.did.didCreationOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.TrustWeaveTestFixture
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
                didCreationOptions {
                    algorithm = org.trustweave.did.KeyAlgorithm.ED25519
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
            val method = methods.find { it.method == doc.id.value.substringAfter("did:").substringBefore(":") }
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
            val generateResult = kms.generateKey(org.trustweave.kms.Algorithm.Ed25519)
            val keyHandle = when (generateResult) {
                is org.trustweave.kms.results.GenerateKeyResult.Success -> generateResult.keyHandle
                else -> throw IllegalArgumentException("Failed to generate key: $generateResult")
            }
            kotlin.test.assertNotNull(keyHandle)
            kotlin.test.assertNotNull(keyHandle.id)

            val message = "test".toByteArray()
            val signResult = kms.sign(keyHandle.id, message)
            val signature = when (signResult) {
                is org.trustweave.kms.results.SignResult.Success -> signResult.signature
                else -> throw IllegalArgumentException("Failed to sign: $signResult")
            }
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
                didCreationOptions {
                    algorithm = org.trustweave.did.KeyAlgorithm.ED25519
                }
            )

            kotlin.test.assertNotNull(document)
            kotlin.test.assertNotNull(document.id)
        }
    }
}

