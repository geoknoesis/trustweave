package org.trustweave.credential.identifiers

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ProtocolIdentifiersTest {
    @Test
    fun `issue and presentation ids are value types`() {
        assertEquals("i-1", IssueId("i-1").toString())
        assertEquals(IssueId("i-1"), IssueId("i-1"))
        assertNotEquals(IssueId("i-1"), IssueId("i-2"))
        assertEquals(IssueId("i-1").hashCode(), IssueId("i-1").hashCode())
        assertEquals("p-1", PresentationId("p-1").value)
        assertEquals(PresentationId("p-1"), PresentationId("p-1"))
        assertNotEquals(PresentationId("p-1"), PresentationId("p-2"))
        assertEquals(PresentationId("p-1").hashCode(), PresentationId("p-1").hashCode())
    }

    @Test
    fun `different identifier kinds never compare equal even with the same text`() {
        assertNotEquals<Any>(OfferId("x"), RequestId("x"))
        assertNotEquals<Any>(IssueId("x"), PresentationId("x"))
        assertNotEquals<Any?>(OfferId("x"), null)
    }

    @Test
    fun `blank issue and presentation ids are rejected`() {
        assertFailsWith<IllegalArgumentException> { IssueId("") }
        assertFailsWith<IllegalArgumentException> { PresentationId("   ") }
    }

    @Test
    fun `serializers round-trip every identifier and reject blank values`() {
        assertEquals("\"i-1\"", Json.encodeToString(IssueIdSerializer, IssueId("i-1")))
        assertEquals(IssueId("i-1"), Json.decodeFromString(IssueIdSerializer, "\"i-1\""))
        assertEquals("\"p-1\"", Json.encodeToString(PresentationIdSerializer, PresentationId("p-1")))
        assertEquals(PresentationId("p-1"), Json.decodeFromString(PresentationIdSerializer, "\"p-1\""))
        assertEquals("\"o-1\"", Json.encodeToString(OfferIdSerializer, OfferId("o-1")))
        assertEquals("\"r-1\"", Json.encodeToString(RequestIdSerializer, RequestId("r-1")))
        assertEquals("\"didcomm\"", Json.encodeToString(ExchangeProtocolNameSerializer, ExchangeProtocolName.DidComm))
        assertFailsWith<SerializationException> { Json.decodeFromString(IssueIdSerializer, "\"\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(PresentationIdSerializer, "\" \"") }
    }

    @Test
    fun `well-known protocol names are valid and distinct`() {
        val names =
            listOf(
                ExchangeProtocolName.DidComm,
                ExchangeProtocolName.Oidc4Vci,
                ExchangeProtocolName.Oidc4Vp,
                ExchangeProtocolName.Chapi,
                ExchangeProtocolName.SiopV2,
                ExchangeProtocolName.Mdl,
            )
        assertEquals(names.size, names.map { it.value }.toSet().size)
        assertEquals("oidc4vp", ExchangeProtocolName.Oidc4Vp.value)
        assertEquals("chapi", ExchangeProtocolName.Chapi.toString())
        assertNotEquals<Any?>(ExchangeProtocolName.Chapi, "chapi")
    }
}
