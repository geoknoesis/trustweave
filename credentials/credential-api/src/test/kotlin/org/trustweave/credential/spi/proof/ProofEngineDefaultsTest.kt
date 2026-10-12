package org.trustweave.credential.spi.proof

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProofEngineDefaultsTest {
    private class MinimalEngine(
        override val capabilities: ProofEngineCapabilities,
    ) : ProofEngine {
        override val format = ProofSuiteId.VC_LD
        override val formatName = "minimal"
        override val formatVersion = "1"

        override suspend fun issue(request: IssuanceRequest): VerifiableCredential = throw UnsupportedOperationException()

        override suspend fun verify(
            credential: VerifiableCredential,
            options: VerificationOptions,
        ): VerificationResult = throw UnsupportedOperationException()
    }

    @Test
    fun `default createPresentation rejects engines that do not advertise presentations`() {
        val engine = MinimalEngine(ProofEngineCapabilities(presentation = false))
        val e =
            assertFailsWith<IllegalArgumentException> {
                runBlocking { engine.createPresentation(emptyList(), PresentationRequest()) }
            }
        assertEquals("Proof suite ${ProofSuiteId.VC_LD.value} does not support presentations", e.message)
    }

    @Test
    fun `default createPresentation reports not implemented when presentations are advertised`() {
        val engine = MinimalEngine(ProofEngineCapabilities(presentation = true))
        val e =
            assertFailsWith<UnsupportedOperationException> {
                runBlocking { engine.createPresentation(emptyList(), PresentationRequest()) }
            }
        assertTrue(e.message!!.contains(ProofSuiteId.VC_LD.value))
    }

    @Test
    fun `default lifecycle hooks are no-ops and the engine is ready`() =
        runBlocking {
            val engine = MinimalEngine(ProofEngineCapabilities())
            engine.initialize()
            engine.close()
            assertTrue(engine.isReady())
        }

    @Test
    fun `config prefers the explicit resolver then the properties entry then null`() {
        val explicit = DidResolver { DidResolutionResult.Failure.NotFound(it) }
        val fromProps = DidResolver { DidResolutionResult.Failure.NotFound(it) }

        assertSame(explicit, ProofEngineConfig(mapOf("didResolver" to fromProps), explicit).getDidResolver())
        assertSame(fromProps, ProofEngineConfig(mapOf("didResolver" to fromProps)).getDidResolver())
        assertNull(ProofEngineConfig().getDidResolver())
        assertNull(ProofEngineConfig(mapOf("didResolver" to "not a resolver")).getDidResolver())
    }
}
