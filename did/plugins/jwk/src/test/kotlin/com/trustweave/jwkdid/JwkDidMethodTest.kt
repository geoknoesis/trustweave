package com.trustweave.jwkdid

import com.trustweave.did.*
import com.trustweave.did.KeyAlgorithm
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwkDidMethodTest {

    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var method: JwkDidMethod

    @BeforeEach
    fun setup() {
        kms = InMemoryKeyManagementService()
        method = JwkDidMethod(kms)
    }

    @Test
    fun `test method name is jwk`() {
        assertEquals("jwk", method.method)
    }

    @Test
    fun `test create DID with Ed25519`() = runBlocking {
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        assertNotNull(document)
        assertTrue(document.id.value.startsWith("did:jwk:"))
        assertEquals(1, document.verificationMethod.size)
        assertEquals(1, document.authentication.size)
    }

    @Test
    fun `test resolve DID after creation`() = runBlocking {
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        val result = method.resolveDid(document.id)

        assertTrue(result is DidResolutionResult.Success)
        val successResult = result as DidResolutionResult.Success
        assertEquals(document.id, successResult.document.id)
        assertEquals("jwk", successResult.resolutionMetadata["method"])
    }

    @Test
    fun `test DID format is base64url encoded`() = runBlocking {
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        // did:jwk should be base64url encoded (no padding)
        val encoded = document.id.value.substringAfter("did:jwk:")
        assertTrue(encoded.matches(Regex("^[A-Za-z0-9_-]+$")))
    }
}

