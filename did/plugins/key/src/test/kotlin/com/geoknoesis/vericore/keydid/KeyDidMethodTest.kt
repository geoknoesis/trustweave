package com.geoknoesis.vericore.keydid

import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.did.DidCreationOptions.KeyAlgorithm
import com.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyDidMethodTest {

    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var method: KeyDidMethod

    @BeforeEach
    fun setup() {
        kms = InMemoryKeyManagementService()
        method = KeyDidMethod(kms)
    }

    @Test
    fun `test method name is key`() {
        assertEquals("key", method.method)
    }

    @Test
    fun `test create DID with Ed25519`() = runBlocking {
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        assertNotNull(document)
        assertTrue(document.id.startsWith("did:key:z"))
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

        assertNotNull(result.document)
        assertEquals(document.id, result.document?.id)
        assertEquals("key", result.resolutionMetadata["method"])
    }

    @Test
    fun `test DID format includes multibase prefix`() = runBlocking {
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )

        // did:key should start with z (base58btc multibase prefix)
        assertTrue(document.id.matches(Regex("^did:key:z[a-zA-Z0-9]+$")))
    }
}

