package com.trustweave.did

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for type-safe DidCreationOptions.
 */
class DidCreationOptionsTest {

    @Test
    fun `test default options`() {
        val options = DidCreationOptions()

        assertEquals(DidCreationOptions.KeyAlgorithm.ED25519, options.algorithm)
        assertEquals(1, options.purposes.size)
        assertTrue(options.purposes.contains(DidCreationOptions.KeyPurpose.AUTHENTICATION))
        assertTrue(options.additionalProperties.isEmpty())
    }

    @Test
    fun `test options with custom algorithm`() {
        val options = DidCreationOptions(
            algorithm = DidCreationOptions.KeyAlgorithm.SECP256K1
        )

        assertEquals(DidCreationOptions.KeyAlgorithm.SECP256K1, options.algorithm)
    }

    @Test
    fun `test options with custom purposes`() {
        val options = DidCreationOptions(
            purposes = listOf(
                DidCreationOptions.KeyPurpose.AUTHENTICATION,
                DidCreationOptions.KeyPurpose.KEY_AGREEMENT
            )
        )

        assertEquals(2, options.purposes.size)
        assertTrue(options.purposes.contains(DidCreationOptions.KeyPurpose.AUTHENTICATION))
        assertTrue(options.purposes.contains(DidCreationOptions.KeyPurpose.KEY_AGREEMENT))
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
            algorithm = DidCreationOptions.KeyAlgorithm.ED25519,
            purposes = listOf(DidCreationOptions.KeyPurpose.AUTHENTICATION),
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

        assertEquals(DidCreationOptions.KeyAlgorithm.SECP256K1, options.algorithm)
        assertEquals(2, options.purposes.size)
        assertTrue(options.purposes.contains(DidCreationOptions.KeyPurpose.AUTHENTICATION))
        assertTrue(options.purposes.contains(DidCreationOptions.KeyPurpose.ASSERTION))
        assertEquals("value", options.additionalProperties["custom"])
    }

    @Test
    fun `test fromMap with fallback algorithm`() {
        val map = mapOf(
            "purposes" to listOf("authentication")
        )

        val options = DidCreationOptions.fromMap(map)

        assertEquals(DidCreationOptions.KeyAlgorithm.ED25519, options.algorithm)
    }

    @Test
    fun `test builder`() {
        val options = didCreationOptions {
            algorithm = DidCreationOptions.KeyAlgorithm.SECP256K1
            purpose(DidCreationOptions.KeyPurpose.AUTHENTICATION)
            purpose(DidCreationOptions.KeyPurpose.ASSERTION)
            property("custom", "value")
        }

        assertEquals(DidCreationOptions.KeyAlgorithm.SECP256K1, options.algorithm)
        assertEquals(2, options.purposes.size)
        assertTrue(options.purposes.contains(DidCreationOptions.KeyPurpose.AUTHENTICATION))
        assertTrue(options.purposes.contains(DidCreationOptions.KeyPurpose.ASSERTION))
        assertEquals("value", options.additionalProperties["custom"])
    }

    @Test
    fun `test KeyAlgorithm fromName`() {
        assertEquals(DidCreationOptions.KeyAlgorithm.ED25519,
            DidCreationOptions.KeyAlgorithm.fromName("Ed25519"))
        assertEquals(DidCreationOptions.KeyAlgorithm.ED25519,
            DidCreationOptions.KeyAlgorithm.fromName("ed25519"))
        assertEquals(DidCreationOptions.KeyAlgorithm.SECP256K1,
            DidCreationOptions.KeyAlgorithm.fromName("SECP256K1"))
        assertNull(DidCreationOptions.KeyAlgorithm.fromName("invalid"))
    }

    @Test
    fun `test KeyPurpose fromName`() {
        assertEquals(DidCreationOptions.KeyPurpose.AUTHENTICATION,
            DidCreationOptions.KeyPurpose.fromName("authentication"))
        assertEquals(DidCreationOptions.KeyPurpose.AUTHENTICATION,
            DidCreationOptions.KeyPurpose.fromName("AUTHENTICATION"))
        assertEquals(DidCreationOptions.KeyPurpose.ASSERTION,
            DidCreationOptions.KeyPurpose.fromName("assertionMethod"))
        assertNull(DidCreationOptions.KeyPurpose.fromName("invalid"))
    }
}
