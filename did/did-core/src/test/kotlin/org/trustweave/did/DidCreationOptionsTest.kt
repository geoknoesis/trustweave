package org.trustweave.did

import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.KeyPurpose
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for type-safe DidCreationOptions.
 */
class DidCreationOptionsTest {

    @Test
    fun `test default options`() {
        val options = DidCreationOptions()

        assertEquals(KeyAlgorithm.ED25519, options.algorithm)
        assertEquals(1, options.purposes.size)
        assertTrue(options.purposes.contains(KeyPurpose.AUTHENTICATION))
        assertTrue(options.additionalProperties.isEmpty())
    }

    @Test
    fun `test options with custom algorithm`() {
        val options = DidCreationOptions(
            algorithm = KeyAlgorithm.SECP256K1
        )

        assertEquals(KeyAlgorithm.SECP256K1, options.algorithm)
    }

    @Test
    fun `test options with custom purposes`() {
        val options = DidCreationOptions(
            purposes = listOf(
                KeyPurpose.AUTHENTICATION,
                KeyPurpose.KEY_AGREEMENT
            )
        )

        assertEquals(2, options.purposes.size)
        assertTrue(options.purposes.contains(KeyPurpose.AUTHENTICATION))
        assertTrue(options.purposes.contains(KeyPurpose.KEY_AGREEMENT))
    }

    @Test
    fun `test options with additional properties`() {
        val options = DidCreationOptions(
            additionalProperties = mapOf(
                "custom" to "value",
                "number" to 42
            )
        )

        assertEquals(2, options.additionalProperties.size)
        assertEquals("value", options.additionalProperties["custom"])
        assertEquals(42, options.additionalProperties["number"])
    }

    @Test
    fun `test toMap conversion`() {
        val options = DidCreationOptions(
            algorithm = KeyAlgorithm.ED25519,
            purposes = listOf(KeyPurpose.AUTHENTICATION),
            additionalProperties = mapOf("custom" to "value")
        )

        val map = options.toMap()

        assertEquals("Ed25519", map["algorithm"])
        assertTrue(map.containsKey("purposes"))
        assertEquals("value", map["custom"])
    }

    @Test
    fun `test fromMap conversion`() {
        val map = mapOf(
            "algorithm" to "secp256k1",
            "purposes" to listOf("authentication", "assertionMethod"),
            "custom" to "value"
        )

        val options = DidCreationOptions.fromMap(map)

        assertEquals(KeyAlgorithm.SECP256K1, options.algorithm)
        assertEquals(2, options.purposes.size)
        assertTrue(options.purposes.contains(KeyPurpose.AUTHENTICATION))
        assertTrue(options.purposes.contains(KeyPurpose.ASSERTION))
        assertEquals("value", options.additionalProperties["custom"])
    }

    @Test
    fun `test fromMap with fallback algorithm`() {
        val map = mapOf(
            "purposes" to listOf("authentication")
        )

        val options = DidCreationOptions.fromMap(map)

        assertEquals(KeyAlgorithm.ED25519, options.algorithm)
    }

    @Test
    fun `test builder`() {
        val options = didCreationOptions {
            algorithm = KeyAlgorithm.SECP256K1
            purpose(KeyPurpose.AUTHENTICATION)
            purpose(KeyPurpose.ASSERTION)
            property("custom", "value")
        }

        assertEquals(KeyAlgorithm.SECP256K1, options.algorithm)
        assertEquals(2, options.purposes.size)
        assertTrue(options.purposes.contains(KeyPurpose.AUTHENTICATION))
        assertTrue(options.purposes.contains(KeyPurpose.ASSERTION))
        assertEquals("value", options.additionalProperties["custom"])
    }

    @Test
    fun `test KeyAlgorithm fromName`() {
        assertEquals(KeyAlgorithm.ED25519,
            KeyAlgorithm.fromName("Ed25519"))
        assertEquals(KeyAlgorithm.ED25519,
            KeyAlgorithm.fromName("ed25519"))
        assertEquals(KeyAlgorithm.SECP256K1,
            KeyAlgorithm.fromName("SECP256K1"))
        assertNull(KeyAlgorithm.fromName("invalid"))
    }

    @Test
    fun `test KeyPurpose fromName`() {
        assertEquals(KeyPurpose.AUTHENTICATION,
            KeyPurpose.fromName("authentication"))
        assertEquals(KeyPurpose.AUTHENTICATION,
            KeyPurpose.fromName("AUTHENTICATION"))
        assertEquals(KeyPurpose.ASSERTION,
            KeyPurpose.fromName("assertionMethod"))
        assertNull(KeyPurpose.fromName("invalid"))
    }
}
