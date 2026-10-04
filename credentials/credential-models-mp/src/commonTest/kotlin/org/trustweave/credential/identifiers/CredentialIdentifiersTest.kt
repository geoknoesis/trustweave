package org.trustweave.credential.identifiers

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CredentialIdentifiersTest {
    @Test
    fun credentialIdValidatesLikeAnIri() {
        assertEquals("urn:uuid:1234", CredentialId("urn:uuid:1234").value)
        assertEquals("https://example.com/credentials/1", CredentialId("https://example.com/credentials/1").toString())
        assertFailsWith<IllegalArgumentException> { CredentialId("") }
        assertFailsWith<IllegalArgumentException> { CredentialId("has space") }
    }

    @Test
    fun credentialIdAsDidOnlyForDidIris() {
        assertEquals("did:key:abc", CredentialId("did:key:abc").asDid()?.value)
        assertNull(CredentialId("urn:uuid:1").asDid())
        assertNull(CredentialId("https://example.com/x").asDid())
    }

    @Test
    fun factoriesPreserveTheValue() {
        assertEquals("https://e.com/a", CredentialId.fromIri(Iri("https://e.com/a")).value)
        assertEquals("did:key:abc", CredentialId.fromDid(Did("did:key:abc")).value)
        assertEquals("did:key:abc", IssuerId.fromDid(Did("did:key:abc")).value)
        assertEquals("https://issuer.example", IssuerId.fromIri(Iri("https://issuer.example")).value)
    }

    @Test
    fun issuerIdAsDid() {
        assertEquals("did:web:example.com", IssuerId("did:web:example.com").asDid()?.value)
        assertNull(IssuerId("https://issuer.example").asDid())
    }

    @Test
    fun idsAreEqualByValueThroughIri() {
        assertEquals(StatusListId("https://e.com/status/1"), StatusListId("https://e.com/status/1"))
        assertNotEquals(StatusListId("https://e.com/status/1"), StatusListId("https://e.com/status/2"))
    }

    @Test
    fun serializersRoundTripAsPlainStrings() {
        assertEquals("\"urn:uuid:1\"", Json.encodeToString(CredentialIdSerializer, CredentialId("urn:uuid:1")))
        assertEquals(CredentialId("urn:uuid:1"), Json.decodeFromString(CredentialIdSerializer, "\"urn:uuid:1\""))
        assertEquals("did:key:a", Json.decodeFromString(IssuerIdSerializer, "\"did:key:a\"").value)
        assertEquals("https://e.com/s", Json.decodeFromString(StatusListIdSerializer, "\"https://e.com/s\"").value)
        assertEquals("https://e.com/schema", Json.decodeFromString(SchemaIdSerializer, "\"https://e.com/schema\"").value)
    }

    @Test
    fun serializersRejectInvalidValuesWithSerializationException() {
        assertFailsWith<SerializationException> { Json.decodeFromString(CredentialIdSerializer, "\"not valid\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(IssuerIdSerializer, "\"\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(StatusListIdSerializer, "\"bad id\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(SchemaIdSerializer, "\"bad id\"") }
    }

    @Test
    fun subjectIdFactories() {
        val did = Did("did:key:abc")
        val fromDid = SubjectId.fromDid(did)
        assertIs<SubjectId.DidSubject>(fromDid)
        assertEquals("did:key:abc", fromDid.value)
        assertIs<SubjectId.UriSubject>(SubjectId.fromUri("https://e.com/me"))
        assertIs<SubjectId.OtherSubject>(SubjectId.fromString("alice"))
        assertEquals("alice", SubjectId.fromString("alice").value)
    }

    @Test
    fun exchangeProtocolNameEnforcesLowercaseHyphenated() {
        assertEquals("oidc4vci", ExchangeProtocolName("oidc4vci").toString())
        assertEquals(ExchangeProtocolName("didcomm"), ExchangeProtocolName.DidComm)
        assertEquals("siop-v2", ExchangeProtocolName.SiopV2.value)
        listOf("", " ", "DidComm", "did comm", "did_comm", "oidc4vci!").forEach {
            assertFailsWith<IllegalArgumentException>(it) { ExchangeProtocolName(it) }
        }
        assertEquals(ExchangeProtocolName("mdl").hashCode(), ExchangeProtocolName.Mdl.hashCode())
    }

    @Test
    fun exchangeProtocolNameSerializerRejectsInvalid() {
        assertEquals(ExchangeProtocolName.Chapi, Json.decodeFromString(ExchangeProtocolNameSerializer, "\"chapi\""))
        assertFailsWith<SerializationException> { Json.decodeFromString(ExchangeProtocolNameSerializer, "\"Bad Name\"") }
    }

    @Test
    fun offerAndRequestIdsRejectBlank() {
        assertEquals("o-1", OfferId("o-1").toString())
        assertEquals(OfferId("o-1"), OfferId("o-1"))
        assertEquals("r-1", RequestId("r-1").value)
        assertEquals(RequestId("r-1").hashCode(), RequestId("r-1").hashCode())
        assertFailsWith<IllegalArgumentException> { OfferId("  ") }
        assertFailsWith<IllegalArgumentException> { RequestId("") }
        assertFailsWith<SerializationException> { Json.decodeFromString(OfferIdSerializer, "\"\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(RequestIdSerializer, "\" \"") }
        assertEquals("o-2", Json.decodeFromString(OfferIdSerializer, "\"o-2\"").value)
    }
}
