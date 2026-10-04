package org.trustweave.signatures.tsa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class TsaModelsTest {
    @Test
    fun `a blank endpoint is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TsaConfig(endpointUrl = "  ") }
    }

    @Test
    fun `a non-positive timeout is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TsaConfig(endpointUrl = "https://tsa.example", requestTimeoutMs = 0) }
    }

    @Test
    fun `credentials must be supplied together`() {
        assertThrows(IllegalArgumentException::class.java) { TsaConfig(endpointUrl = "https://tsa.example", username = "u") }
        assertThrows(IllegalArgumentException::class.java) { TsaConfig(endpointUrl = "https://tsa.example", password = "p") }
        TsaConfig(endpointUrl = "https://tsa.example", username = "u", password = "p")
    }

    @Test
    fun `nonce and pin defaults favour the safe choice`() {
        val config = TsaConfig(endpointUrl = "https://tsa.example")
        assertEquals(true, config.includeNonce)
        assertEquals(emptyList<ByteArray>(), config.trustedSignerCertificates)
    }

    private fun token(bytes: ByteArray) =
        TimeStampToken(
            encoded = bytes,
            genTime = Instant.fromEpochSeconds(1_000),
            tsaSubject = "CN=TSA",
            messageImprintAlgorithm = TsaHashAlgorithm.SHA_256,
            messageImprint = ByteArray(32),
            serialNumber = byteArrayOf(1),
            policyOid = null,
        )

    @Test
    fun `tokens are equal exactly when their DER encodings are equal`() {
        assertEquals(token(byteArrayOf(1, 2, 3)), token(byteArrayOf(1, 2, 3)))
        assertEquals(token(byteArrayOf(1, 2, 3)).hashCode(), token(byteArrayOf(1, 2, 3)).hashCode())
        assertNotEquals(token(byteArrayOf(1, 2, 3)), token(byteArrayOf(1, 2, 4)))
    }
}
