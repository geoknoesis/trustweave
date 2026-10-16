package org.trustweave.credential.exchange

import kotlinx.coroutines.runBlocking
import org.trustweave.credential.CredentialService
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.model.CredentialPreview
import org.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import org.trustweave.credential.exchange.request.ExchangeRequest
import org.trustweave.credential.exchange.request.ProofExchangeRequest
import org.trustweave.credential.exchange.result.ExchangeResult
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExchangeRegistryAndServicesTest {
    private class StubProtocol(
        override val protocolName: ExchangeProtocolName,
        operations: Set<ExchangeOperation>,
    ) : CredentialExchangeProtocol {
        override val capabilities = ExchangeProtocolCapabilities(supportedOperations = operations)

        private fun unreachable(): Nothing = throw UnsupportedOperationException("not exercised")

        override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope = unreachable()

        override suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope = unreachable()

        override suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope> = unreachable()

        override suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope = unreachable()

        override suspend fun presentProof(
            request: ProofExchangeRequest.Presentation,
        ): Pair<VerifiablePresentation, ExchangeMessageEnvelope> = unreachable()
    }

    private val a = ExchangeProtocolName("proto-a")
    private val b = ExchangeProtocolName("proto-b")
    private val protoA = StubProtocol(a, setOf(ExchangeOperation.OFFER_CREDENTIAL))
    private val protoB = StubProtocol(b, emptySet())

    @Test
    fun `registry returns what was registered and nothing for unknown names`() {
        val registry = ExchangeProtocolRegistries.default()
        assertNull(registry.get(a))
        assertFalse(registry.isRegistered(a))

        registry.register(protoA)
        registry.register(protoB)

        assertSame(protoA, registry.get(a))
        assertTrue(registry.isRegistered(b))
        assertEquals(setOf(a, b), registry.getSupportedProtocols().toSet())
        assertEquals(setOf(a, b), registry.getAll().keys)
        assertSame(protoA.capabilities, registry.getCapabilities(a))
        assertNull(registry.getCapabilities(ExchangeProtocolName("unknown")))
    }

    @Test
    fun `registering the same name replaces the earlier protocol`() {
        val registry = ExchangeProtocolRegistries.default()
        registry.register(protoA)
        val replacement = StubProtocol(a, emptySet())
        registry.register(replacement)
        assertSame(replacement, registry.get(a))
        assertEquals(1, registry.getAll().size)
    }

    @Test
    fun `supports reflects the protocol operations and is false for unregistered protocols`() {
        val registry = ExchangeProtocolRegistries.default()
        registry.register(protoA)
        assertTrue(registry.supports(a, ExchangeOperation.OFFER_CREDENTIAL))
        assertFalse(registry.supports(a, ExchangeOperation.ISSUE_CREDENTIAL))
        assertFalse(registry.supports(b, ExchangeOperation.OFFER_CREDENTIAL))
    }

    @Test
    fun `getAll returns a snapshot and unregister and clear remove protocols`() {
        val registry = ExchangeProtocolRegistries.default()
        registry.register(protoA)
        val snapshot = registry.getAll()
        registry.register(protoB)
        assertEquals(setOf(a), snapshot.keys)

        assertSame(protoA, registry.unregister(a))
        assertNull(registry.unregister(a))
        assertFalse(registry.isRegistered(a))

        registry.clear()
        assertTrue(registry.getSupportedProtocols().isEmpty())
    }

    private fun offer(protocol: ExchangeProtocolName) =
        ExchangeRequest.Offer(
            protocolName = protocol,
            issuerDid = Did("did:key:issuer"),
            holderDid = Did("did:key:holder"),
            credentialPreview = CredentialPreview(attributes = emptyList()),
        )

    private val resolver = DidResolver { DidResolutionResult.Failure.NotFound(it) }

    private fun unusedCredentialService(): CredentialService = org.trustweave.credential.credentialService(didResolver = resolver)

    @Test
    fun `vararg factory registers the given protocols with the service`() =
        runBlocking<Unit> {
            val service = ExchangeServices.createExchangeService(unusedCredentialService(), resolver, protoA, protoB)
            val failure = assertIs<ExchangeResult.Failure.ProtocolNotSupported>(service.offer(offer(ExchangeProtocolName("other"))))
            assertEquals(setOf(a, b), failure.availableProtocols.toSet())
        }

    @Test
    fun `auto discovery factories start empty when no provider is on the classpath`() =
        runBlocking<Unit> {
            val unknown = offer(ExchangeProtocolName("other"))
            val all = ExchangeServices.createExchangeServiceWithAutoDiscovery(unusedCredentialService(), resolver)
            assertTrue(assertIs<ExchangeResult.Failure.ProtocolNotSupported>(all.offer(unknown)).availableProtocols.isEmpty())

            val named =
                ExchangeServices.createExchangeServiceWithAutoDiscovery(unusedCredentialService(), resolver, listOf(a))
            assertTrue(assertIs<ExchangeResult.Failure.ProtocolNotSupported>(named.offer(unknown)).availableProtocols.isEmpty())
        }
}
