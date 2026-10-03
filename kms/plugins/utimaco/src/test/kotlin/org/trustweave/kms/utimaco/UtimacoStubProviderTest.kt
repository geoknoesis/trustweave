package org.trustweave.kms.utimaco

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtimacoStubProviderTest {
    private val options = mapOf("hsmAddress" to "hsm.invalid:1500", "partitionId" to "p", "partitionPassword" to "PW-SECRET")

    @Test
    fun `the stub is not advertised as a working provider`() {
        val provider = UtimacoKeyManagementServiceProvider()
        assertTrue(provider.supportedAlgorithms.isEmpty())
        assertFalse(provider.supportsAlgorithm(org.trustweave.kms.Algorithm.P256))
    }

    @Test
    fun `create fails loudly with a clear message`() {
        val ex = assertThrows<UnsupportedOperationException> { UtimacoKeyManagementServiceProvider().create(options) }
        assertTrue("stub" in ex.message.orEmpty(), ex.message)
        assertTrue("Utimaco" in ex.message.orEmpty(), ex.message)
    }

    @Test
    fun `create still reports invalid configuration precisely`() {
        assertThrows<IllegalArgumentException> { UtimacoKeyManagementServiceProvider().create(emptyMap()) }
        assertThrows<IllegalArgumentException> { UtimacoKeyManagementServiceProvider().create(options + ("partitionId" to " ")) }
    }

    @Test
    fun `config toString redacts the partition password`() {
        val text = UtimacoKmsConfig.fromMap(options).toString()
        assertFalse("PW-SECRET" in text, text)
        assertTrue("<redacted>" in text, text)
        assertEquals("hsm.invalid:1500", UtimacoKmsConfig.fromMap(options).hsmAddress)
    }
}
