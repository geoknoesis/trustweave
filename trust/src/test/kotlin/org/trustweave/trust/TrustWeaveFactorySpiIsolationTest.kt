package org.trustweave.trust

import org.trustweave.trust.spi.SPI_ISOLATION_ANCHOR_PROVIDER
import org.trustweave.trust.spi.SPI_ISOLATION_CHAIN_ID
import org.trustweave.trust.spi.SPI_ISOLATION_DID_METHOD
import org.trustweave.trust.spi.SpiIsolationDidMethod
import org.trustweave.trust.types.getOrThrowDid
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies SPI error isolation in TrustWeaveFactory: a provider whose `create(...)` throws
 * must be logged and skipped so a later working provider can still satisfy the request.
 *
 * The synthetic broken/working provider pairs are registered via
 * `src/test/resources/META-INF/services` with the broken provider listed FIRST (ServiceLoader
 * preserves file order within one services file), so the factory always hits the broken one
 * before the working one.
 *
 * **Coverage note:** the [java.util.ServiceConfigurationError] path (unresolvable class name
 * in a services file) is guarded in `loadProvidersIsolated` but intentionally NOT exercised
 * here — a malformed services entry poisons the classpath for the whole module's test suite,
 * because `DidMethodRegistry.autoRegister` (did-core) iterates the same ServiceLoader and
 * only catches `Exception`, not `Error`.
 */
class TrustWeaveFactorySpiIsolationTest {

    @Test
    fun `broken DidMethodProvider is skipped and the working provider resolves the method`() = runBlocking<Unit> {
        val trustWeave = TrustWeave.build {
            keys {
                provider("inMemory")
                algorithm("Ed25519")
            }
            did {
                method(SPI_ISOLATION_DID_METHOD) { algorithm("Ed25519") }
            }
        }
        try {
            // Build succeeded despite the broken provider being encountered first; the
            // registered method must come from the working provider.
            val method = trustWeave.getDidMethod(SPI_ISOLATION_DID_METHOD)
            assertNotNull(method, "Working provider should have resolved '$SPI_ISOLATION_DID_METHOD'")
            assertIs<SpiIsolationDidMethod>(method)

            val did = trustWeave.createDid(method = SPI_ISOLATION_DID_METHOD).getOrThrowDid()
            assertTrue(did.value.startsWith("did:"), "Unexpected DID: ${did.value}")
        } finally {
            trustWeave.close()
        }
    }

    @Test
    fun `broken BlockchainAnchorClientProvider is skipped and the working provider resolves the chain`() =
        runBlocking<Unit> {
            val trustWeave = TrustWeave.build {
                keys {
                    provider("inMemory")
                    algorithm("Ed25519")
                }
                did {
                    method("key") { algorithm("Ed25519") }
                }
                anchor {
                    chain(SPI_ISOLATION_CHAIN_ID) { provider(SPI_ISOLATION_ANCHOR_PROVIDER) }
                }
            }
            try {
                val client = trustWeave.configuration.blockchainRegistry.get(SPI_ISOLATION_CHAIN_ID)
                assertNotNull(client, "Working provider should have resolved '$SPI_ISOLATION_CHAIN_ID'")
            } finally {
                trustWeave.close()
            }
        }
}
