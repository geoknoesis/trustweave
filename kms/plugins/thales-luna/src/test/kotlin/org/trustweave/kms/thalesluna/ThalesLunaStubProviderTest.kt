package org.trustweave.kms.thalesluna

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThalesLunaStubProviderTest {
    private val options = mapOf("hsmAddress" to "hsm.invalid:1500", "partitionId" to "p", "partitionPassword" to "PW-SECRET")

    @Test
    fun `the stub is not advertised as a working provider`() {
        val provider = ThalesLunaKeyManagementServiceProvider()
        assertTrue(provider.supportedAlgorithms.isEmpty())
        assertFalse(provider.supportsAlgorithm(org.trustweave.kms.Algorithm.P256))
    }

    @Test
    fun `create fails loudly with a clear message`() {
        val ex = assertThrows<UnsupportedOperationException> { ThalesLunaKeyManagementServiceProvider().create(options) }
        assertTrue("stub" in ex.message.orEmpty(), ex.message)
        assertTrue("Thales Luna" in ex.message.orEmpty(), ex.message)
    }

    @Test
    fun `create still reports invalid configuration precisely`() {
        assertThrows<IllegalArgumentException> { ThalesLunaKeyManagementServiceProvider().create(emptyMap()) }
        assertThrows<IllegalArgumentException> { ThalesLunaKeyManagementServiceProvider().create(options + ("partitionId" to " ")) }
    }

    @Test
    fun `config toString redacts the partition password`() {
        val text = ThalesLunaKmsConfig.fromMap(options).toString()
        assertFalse("PW-SECRET" in text, text)
        assertTrue("<redacted>" in text, text)
        assertEquals("hsm.invalid:1500", ThalesLunaKmsConfig.fromMap(options).hsmAddress)
    }
}
