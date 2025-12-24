package org.trustweave.testkit.integration

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.didCreationOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.BaseIntegrationTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Base class for DID method integration tests.
 *
 * Provides common test scenarios for DID methods including:
 * - Tests with real DID registries
 * - Cross-method compatibility tests
 * - Resolution across networks
 *
 * **Example Usage**:
 * ```kotlin
 * @Testcontainers
 * class KeyDidMethodIntegrationTest : DidMethodIntegrationTest() {
 *     override fun getDidMethod(): DidMethod {
 *         return KeyDidMethod(fixture.getKms())
 *     }
 *
 *     @Test
 *     fun testCreateAndResolve() = runBlocking {
 *         testCreateAndResolveDid()
 *     }
 * }
 * ```
 */
abstract class DidMethodIntegrationTest : BaseIntegrationTest() {

    /**
     * Gets the DID method to test.
     * Must be implemented by subclasses.
     */
    abstract override fun getDidMethod(): DidMethod

    /**
     * Gets the DID method name.
     * Defaults to the method property of the DID method.
     */
    open fun getMethodName(): String {
        return getDidMethod().method
    }

    /**
     * Tests creating and resolving a DID.
     */
    protected suspend fun testCreateAndResolveDid() {
        val method = getDidMethod()
        val registry = fixture.getDidRegistry()
        registry.register(method)

        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        kotlin.test.assertNotNull(document)
        kotlin.test.assertTrue(document.id.value.startsWith("did:${getMethodName()}:"))

        val resolution = method.resolveDid(document.id)
        val resolvedDocument = when (resolution) {
            is DidResolutionResult.Success -> resolution.document
            else -> null
        }
        kotlin.test.assertNotNull(resolvedDocument)
        kotlin.test.assertEquals(document.id, resolvedDocument?.id)
    }

    /**
     * Tests updating a DID.
     */
    protected suspend fun testUpdateDid() {
        val method = getDidMethod()
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        val updated = method.updateDid(document.id) { doc ->
            doc.copy(
                verificationMethod = doc.verificationMethod + VerificationMethod(
                    id = VerificationMethodId.parse("${doc.id.value}#key-2"),
                    type = "Ed25519VerificationKey2020",
                    controller = doc.id,
                    publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
                )
            )
        }

        kotlin.test.assertNotNull(updated)
        kotlin.test.assertTrue(updated.verificationMethod.size > document.verificationMethod.size)
    }

    /**
     * Tests deactivating a DID.
     */
    protected suspend fun testDeactivateDid() {
        val method = getDidMethod()
        val registry = fixture.getDidRegistry()
        registry.register(method)

        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        val deactivated = method.deactivateDid(document.id)
        kotlin.test.assertTrue(deactivated)

        // After deactivation, resolution should indicate deactivated status
        val resolution = method.resolveDid(document.id)

        // Verify deactivation status - methods may handle this differently:
        // 1. Some return null document with deactivated metadata
        // 2. Some return document with deactivated flag in metadata
        // 3. Some return error resolution result
        kotlin.test.assertNotNull(resolution, "Resolution result should not be null after deactivation")

        // Check if deactivation is indicated in metadata or by null document
        // W3C DID Core spec allows methods to indicate deactivation via:
        // - null document with deactivated=true in metadata
        // - document with deactivated flag
        val resolutionMetadata = when (resolution) {
            is DidResolutionResult.Success -> resolution.resolutionMetadata
            is DidResolutionResult.Failure.NotFound -> resolution.resolutionMetadata
            is DidResolutionResult.Failure.InvalidFormat -> resolution.resolutionMetadata
            is DidResolutionResult.Failure.MethodNotRegistered -> resolution.resolutionMetadata
            is DidResolutionResult.Failure.ResolutionError -> resolution.resolutionMetadata
        }
        val resolvedDocument = when (resolution) {
            is DidResolutionResult.Success -> resolution.document
            else -> null
        }
        val isDeactivated = resolutionMetadata["deactivated"] == true ||
                           resolutionMetadata["deactivated"] == "true" ||
                           resolvedDocument == null

        kotlin.test.assertTrue(
            isDeactivated,
            "Resolution should indicate deactivated status. Metadata: $resolutionMetadata, Document: $resolvedDocument"
        )
    }

    /**
     * Tests cross-method compatibility.
     * Verifies that DIDs created with one method can coexist with others.
     */
    protected suspend fun testCrossMethodCompatibility(otherMethod: DidMethod) {
        val method1 = getDidMethod()
        val method2 = otherMethod

        val registry = fixture.getDidRegistry()
        registry.register(method1)
        registry.register(method2)

        val doc1 = method1.createDid(
            org.trustweave.did.didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        val doc2 = method2.createDid(
            org.trustweave.did.didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        kotlin.test.assertNotNull(doc1)
        kotlin.test.assertNotNull(doc2)
        kotlin.test.assertTrue(doc1.id != doc2.id)
        kotlin.test.assertTrue(doc1.id.value.startsWith("did:${method1.method}:"))
        kotlin.test.assertTrue(doc2.id.value.startsWith("did:${method2.method}:"))
    }

}

