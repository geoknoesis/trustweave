package io.geoknoesis.vericore.kms

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeyManagementServiceTest {

    @Test
    fun `KeyHandle should store key information`() {
        val handle = KeyHandle(
            id = "key-1",
            algorithm = "Ed25519",
            publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519")
        )

        assertEquals("key-1", handle.id)
        assertEquals("Ed25519", handle.algorithm)
        assertNotNull(handle.publicKeyJwk)
    }

    @Test
    fun `KeyNotFoundException should be throwable`() {
        val exception = KeyNotFoundException("Key not found: test-key")
        assertEquals("Key not found: test-key", exception.message)
    }
}

