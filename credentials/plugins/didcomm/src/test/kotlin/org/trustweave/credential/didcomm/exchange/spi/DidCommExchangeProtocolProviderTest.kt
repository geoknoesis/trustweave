package org.trustweave.credential.didcomm.exchange.spi

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
    fun `create uses placeholder when useProductionCrypto omitted`() {
        val protocol = provider.create(
            "didcomm",
            mapOf(
                "kms" to kms,
                "resolveDid" to resolveDid,
            ),
        )
        assertNotNull(protocol)
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
}
