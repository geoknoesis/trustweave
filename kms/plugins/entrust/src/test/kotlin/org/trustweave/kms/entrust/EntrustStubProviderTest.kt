package org.trustweave.kms.entrust

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntrustStubProviderTest {
    private val options = mapOf("hsmAddress" to "hsm.invalid:1500", "partitionId" to "p", "partitionPassword" to "PW-SECRET")

    @Test
    fun `the stub is not advertised as a working provider`() {
        val provider = EntrustKeyManagementServiceProvider()
        assertTrue(provider.supportedAlgorithms.isEmpty())
        assertFalse(provider.supportsAlgorithm(org.trustweave.kms.Algorithm.P256))
    }

    @Test
    fun `create fails loudly with a clear message`() {
        val ex = assertThrows<UnsupportedOperationException> { EntrustKeyManagementServiceProvider().create(options) }
        assertTrue("stub" in ex.message.orEmpty(), ex.message)
        assertTrue("Entrust nShield" in ex.message.orEmpty(), ex.message)
    }

    @Test
    fun `create still reports invalid configuration precisely`() {
        assertThrows<IllegalArgumentException> { EntrustKeyManagementServiceProvider().create(emptyMap()) }
        assertThrows<IllegalArgumentException> { EntrustKeyManagementServiceProvider().create(options + ("partitionId" to " ")) }
    }

    @Test
    fun `config toString redacts the partition password`() {
        val text = EntrustKmsConfig.fromMap(options).toString()
        assertFalse("PW-SECRET" in text, text)
        assertTrue("<redacted>" in text, text)
        assertEquals("hsm.invalid:1500", EntrustKmsConfig.fromMap(options).hsmAddress)
    }
}
