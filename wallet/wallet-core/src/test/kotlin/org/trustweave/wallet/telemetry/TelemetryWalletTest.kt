package org.trustweave.wallet.telemetry

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.telemetry.Operation
import org.trustweave.core.telemetry.Outcome
import org.trustweave.core.telemetry.Telemetry
import org.trustweave.core.telemetry.TelemetryEvent
import org.trustweave.core.telemetry.TelemetrySink
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.CredentialFilter
import org.trustweave.wallet.CredentialQueryBuilder
import org.trustweave.wallet.Wallet
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TelemetryWalletTest {
    private val recorded = CopyOnWriteArrayList<TelemetryEvent>()
    private val sink = TelemetrySink { recorded += it }

    @AfterTest
    fun tearDown() {
        Telemetry.uninstall()
    }

    private fun credential() =
        VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri("did:example:subject"), claims = emptyMap()),
        )

    /** A wallet that answers, so the decorator is the only thing under test. */
    private class FakeWallet(
        private val stored: VerifiableCredential? = null,
        private val deletes: Boolean = true,
        private val failure: Throwable? = null,
    ) : Wallet {
        override val walletId: String = "wallet-1"

        override suspend fun store(credential: VerifiableCredential): String {
            failure?.let { throw it }
            return "credential-1"
        }

        override suspend fun get(credentialId: String): VerifiableCredential? = stored

        override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = listOfNotNull(stored)

        override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = listOfNotNull(stored)

        override suspend fun delete(credentialId: String): Boolean = deletes

        override fun close() = Unit
    }

    private fun eventsFor(operation: Operation) = recorded.filter { it.operation == operation }

    @Test
    fun `nothing is recorded until a host installs a sink`() =
        runTest {
            val wallet = FakeWallet().withTelemetry()
            wallet.store(credential())
            assertTrue(recorded.isEmpty())
        }

    @Test
    fun `each storage operation reports its own operation`() =
        runTest {
            Telemetry.install(sink)
            val wallet = FakeWallet(stored = credential()).withTelemetry()
            wallet.store(credential())
            wallet.get("credential-1")
            wallet.list(null)
            wallet.query { }
            wallet.delete("credential-1")

            assertEquals(1, eventsFor(Operation.WALLET_STORE).size)
            assertEquals(1, eventsFor(Operation.WALLET_GET).size)
            assertEquals(2, eventsFor(Operation.WALLET_QUERY).size, "list and query are both reads")
            assertEquals(1, eventsFor(Operation.WALLET_DELETE).size)
            assertTrue(recorded.all { it.outcome == Outcome.SUCCESS }, recorded.toString())
        }

    @Test
    fun `the two read shapes are distinguishable`() =
        runTest {
            Telemetry.install(sink)
            val wallet = FakeWallet(stored = credential()).withTelemetry()
            wallet.list(null)
            wallet.query { }
            assertEquals(
                listOf("list", "query"),
                eventsFor(Operation.WALLET_QUERY).map { it.attributes["wallet.read"] },
            )
        }

    @Test
    fun `a miss is reported as rejected rather than a failure`() =
        runTest {
            Telemetry.install(sink)
            val wallet = FakeWallet(stored = null, deletes = false).withTelemetry()
            wallet.get("absent")
            wallet.delete("absent")
            val outcomes = recorded.filter { it.outcome == Outcome.REJECTED }
            assertEquals(2, outcomes.size, recorded.toString())
            assertTrue(outcomes.all { it.reason == "NotFound" })
            assertTrue(recorded.none { it.outcome == Outcome.FAILURE }, "a miss is not a failure")
        }

    @Test
    fun `a thrown storage error is reported as a failure and rethrown`() =
        runTest {
            Telemetry.install(sink)
            val wallet = FakeWallet(failure = IllegalStateException("disk gone")).withTelemetry()
            assertFailsWith<IllegalStateException> { wallet.store(credential()) }
            val event = eventsFor(Operation.WALLET_STORE).single()
            assertEquals(Outcome.FAILURE, event.outcome)
            assertEquals("IllegalStateException", event.reason, "the reason must be a class name, never a message")
        }

    @Test
    fun `nothing that identifies a subject reaches an event`() =
        runTest {
            Telemetry.install(sink)
            val wallet = FakeWallet(stored = credential()).withTelemetry()
            wallet.store(credential())
            wallet.get("did:example:subject")
            val values = recorded.flatMap { it.attributes.values + listOfNotNull(it.reason) }
            assertTrue(values.none { "did:example" in it }, "attributes carried a DID: $values")
            assertTrue(values.none { "credential-1" in it }, "attributes carried a credential id: $values")
        }

    @Test
    fun `wrapping twice does not double-report`() =
        runTest {
            Telemetry.install(sink)
            val once = FakeWallet().withTelemetry()
            val twice = once.withTelemetry()
            assertSame(once, twice)
            twice.store(credential())
            assertEquals(1, eventsFor(Operation.WALLET_STORE).size)
        }
}
