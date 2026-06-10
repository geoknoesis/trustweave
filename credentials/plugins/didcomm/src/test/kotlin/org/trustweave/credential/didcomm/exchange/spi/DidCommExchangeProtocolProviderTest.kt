package org.trustweave.credential.didcomm.exchange.spi

import org.trustweave.credential.didcomm.crypto.interop.MapSecretResolver
import org.trustweave.did.model.DidDocument
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DidCommExchangeProtocolProviderTest {

    private val provider = DidCommExchangeProtocolProvider()
    private val kms = InMemoryKeyManagementService()
    private val resolveDid: suspend (String) -> DidDocument? = { null }

    @Test
    fun `create throws when secretResolver omitted`() {
        // Placeholder crypto has been removed; without real key material creation must fail.
        val ex = assertFailsWith<IllegalArgumentException> {
            provider.create(
                "didcomm",
                mapOf(
                    "kms" to kms,
                    "resolveDid" to resolveDid,
                ),
            )
        }
        assertTrue(ex.message.orEmpty().contains("secretResolver", ignoreCase = true))
    }

    @Test
    fun `create throws when useProductionCrypto explicitly false`() {
        val ex = assertFailsWith<UnsupportedOperationException> {
            provider.create(
                "didcomm",
                mapOf(
                    "kms" to kms,
                    "resolveDid" to resolveDid,
                    "useProductionCrypto" to false,
                    "secretResolver" to MapSecretResolver(),
                ),
            )
        }
        assertTrue(ex.message.orEmpty().contains("placeholder", ignoreCase = true))
    }

    @Test
    fun `create throws when useProductionCrypto true without secretResolver`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            provider.create(
                "didcomm",
                mapOf(
                    "kms" to kms,
                    "resolveDid" to resolveDid,
                    "useProductionCrypto" to true,
                ),
            )
        }
        assertTrue(ex.message.orEmpty().contains("secretResolver", ignoreCase = true))
    }

    @Test
    fun `create succeeds with secretResolver`() {
        val protocol = provider.create(
            "didcomm",
            mapOf(
                "kms" to kms,
                "resolveDid" to resolveDid,
                "secretResolver" to MapSecretResolver(),
            ),
        )
        assertNotNull(protocol)
    }
}
