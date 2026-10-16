package org.trustweave.credential.exchange.exception

import org.trustweave.core.exception.TrustWeaveException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExchangeExceptionBehaviorTest {
    @Test
    fun `protocol not registered lists available protocols or says none are available`() {
        val none = ExchangeException.ProtocolNotRegistered("didcomm")
        assertEquals("PROTOCOL_NOT_REGISTERED", none.code)
        assertEquals("Protocol 'didcomm' is not registered. No protocols available.", none.message)
        assertEquals(emptyList<String>(), none.context["availableProtocols"])

        val some = ExchangeException.ProtocolNotRegistered("didcomm", listOf("oidc4vci", "chapi"))
        assertEquals(
            "Protocol 'didcomm' is not registered. Available protocols: oidc4vci, chapi",
            some.message,
        )
        assertEquals("didcomm", some.context["protocolName"])
        assertEquals(listOf("oidc4vci", "chapi"), some.context["availableProtocols"])
    }

    @Test
    fun `operation not supported names protocol operation and supported operations`() {
        val e = ExchangeException.OperationNotSupported("chapi", "issue", listOf("present", "verify"))
        assertEquals("OPERATION_NOT_SUPPORTED", e.code)
        assertEquals(
            "Protocol 'chapi' does not support operation 'issue'. Supported operations: present, verify",
            e.message,
        )
    }

    @Test
    fun `missing required option includes the protocol only when given`() {
        val bare = ExchangeException.MissingRequiredOption("endpoint")
        assertEquals("Missing required option 'endpoint'", bare.message)
        assertEquals(mapOf<String, Any?>("optionName" to "endpoint"), bare.context)

        val scoped = ExchangeException.MissingRequiredOption("endpoint", "oidc4vci")
        assertEquals("Missing required option 'endpoint' for protocol 'oidc4vci'", scoped.message)
        assertEquals("oidc4vci", scoped.context["protocolName"])
    }

    @Test
    fun `invalid request formats message with and without protocol and keeps cause`() {
        val cause = IllegalStateException("root")
        val bare = ExchangeException.InvalidRequest("holder", "must not be blank", cause = cause)
        assertEquals("Invalid request field 'holder': must not be blank", bare.message)
        assertSame(cause, bare.cause)

        val scoped = ExchangeException.InvalidRequest("holder", "bad", protocolName = "didcomm")
        assertEquals("Invalid request field 'holder' for protocol 'didcomm': bad", scoped.message)
        assertNull(scoped.cause)
        assertEquals("INVALID_REQUEST", scoped.code)
    }

    @Test
    fun `not found variants format ids with optional protocol`() {
        assertEquals("Offer 'o1' not found", ExchangeException.OfferNotFound("o1").message)
        assertEquals(
            "Offer 'o1' not found for protocol 'chapi'",
            ExchangeException.OfferNotFound("o1", "chapi").message,
        )
        assertEquals("o1", ExchangeException.OfferNotFound("o1", "chapi").context["offerId"])
        assertEquals("OFFER_NOT_FOUND", ExchangeException.OfferNotFound("o1").code)

        assertEquals("Request 'r1' not found", ExchangeException.RequestNotFound("r1").message)
        assertEquals(
            "Request 'r1' not found for protocol 'chapi'",
            ExchangeException.RequestNotFound("r1", "chapi").message,
        )
        assertEquals("REQUEST_NOT_FOUND", ExchangeException.RequestNotFound("r1").code)

        assertEquals("Proof request 'p1' not found", ExchangeException.ProofRequestNotFound("p1").message)
        assertEquals(
            "Proof request 'p1' not found for protocol 'chapi'",
            ExchangeException.ProofRequestNotFound("p1", "chapi").message,
        )
        assertEquals("PROOF_REQUEST_NOT_FOUND", ExchangeException.ProofRequestNotFound("p1").code)

        assertEquals("Message 'm1' not found", ExchangeException.MessageNotFound("m1").message)
        assertEquals(
            "Message 'm1' not found for protocol 'chapi'",
            ExchangeException.MessageNotFound("m1", "chapi").message,
        )
        assertEquals("MESSAGE_NOT_FOUND", ExchangeException.MessageNotFound("m1").code)
    }

    @Test
    fun `unknown error includes error type when present`() {
        val cause = RuntimeException("x")
        val typed = ExchangeException.Unknown("timeout", "NETWORK", cause)
        assertEquals("Unknown exchange error (NETWORK): timeout", typed.message)
        assertEquals("NETWORK", typed.context["errorType"])
        assertSame(cause, typed.cause)

        val untyped = ExchangeException.Unknown("timeout")
        assertEquals("Unknown exchange error: timeout", untyped.message)
        assertTrue("errorType" !in untyped.context)
        assertEquals("EXCHANGE_UNKNOWN_ERROR", untyped.code)
    }

    @Test
    fun `all variants are trustweave exceptions and data classes compare structurally`() {
        val e: Throwable = ExchangeException.OfferNotFound("o1")
        assertIs<ExchangeException>(e)
        assertIs<TrustWeaveException>(e)
        assertEquals(ExchangeException.OfferNotFound("o1", "x"), ExchangeException.OfferNotFound("o1", "x"))
        assertTrue(ExchangeException.OfferNotFound("o1") != ExchangeException.OfferNotFound("o2"))
    }
}
