package org.trustweave.credential.model.vc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IssuerSerializerTest {
    private val json = Json

    @Test
    fun aStringIssuerDecodesToIriIssuerAndEncodesBackToAString() {
        val issuer = json.decodeFromString(IssuerSerializer, "\"did:web:example.com\"")

        assertIs<Issuer.IriIssuer>(issuer)
        assertEquals("did:web:example.com", issuer.id.value)
        assertTrue(issuer.isDid)
        assertEquals("\"did:web:example.com\"", json.encodeToString(IssuerSerializer, issuer))
    }

    @Test
    fun anObjectIssuerKeepsNameAndUnmodelledMembers() {
        val issuer =
            json.decodeFromString(
                IssuerSerializer,
                """{"id":"https://issuer.example","name":"Example","image":"https://issuer.example/logo.png","extra":{"a":1}}""",
            )

        assertIs<Issuer.ObjectIssuer>(issuer)
        assertEquals("Example", issuer.name)
        assertEquals(setOf("image", "extra"), issuer.additionalProperties.keys)
        assertFalse(issuer.isDid)

        val encoded = json.parseToJsonElement(json.encodeToString(IssuerSerializer, issuer)).jsonObject
        assertEquals(JsonPrimitive("https://issuer.example"), encoded["id"])
        assertEquals(JsonPrimitive("Example"), encoded["name"])
        assertEquals(JsonPrimitive("https://issuer.example/logo.png"), encoded["image"])
        assertTrue("extra" in encoded)
    }

    @Test
    fun additionalPropertiesCannotOverrideIdOrName() {
        val issuer =
            Issuer.ObjectIssuer(
                id = Iri("did:key:real"),
                name = "Real",
                additionalProperties = mapOf("id" to JsonPrimitive("did:key:forged"), "name" to JsonPrimitive("Forged")),
            )

        val encoded = json.parseToJsonElement(json.encodeToString(IssuerSerializer, issuer)).jsonObject

        assertEquals(JsonPrimitive("did:key:real"), encoded["id"])
        assertEquals(JsonPrimitive("Real"), encoded["name"])
    }

    @Test
    fun anObjectWithoutNameHasNullName() {
        val issuer = json.decodeFromString(IssuerSerializer, """{"id":"urn:uuid:1"}""")
        assertIs<Issuer.ObjectIssuer>(issuer)
        assertNull(issuer.name)
    }

    @Test
    fun malformedIssuersAreRejected() {
        assertFailsWith<IllegalArgumentException> { json.decodeFromString(IssuerSerializer, """{"name":"no id"}""") }
        assertFailsWith<IllegalArgumentException> { json.decodeFromString(IssuerSerializer, "[\"did:key:a\"]") }
        assertFailsWith<IllegalArgumentException> { json.decodeFromString(IssuerSerializer, "null") }
        assertFailsWith<IllegalArgumentException> { json.decodeFromString(IssuerSerializer, "\"not an iri\"") }
        assertFailsWith<IllegalArgumentException> { json.decodeFromString(IssuerSerializer, """{"id":"has space"}""") }
    }

    @Test
    fun factories() {
        assertEquals("did:key:a", Issuer.from("did:key:a").id.value)
        assertEquals(Issuer.IriIssuer(Iri("https://e.com")), Issuer.from(Iri("https://e.com")))
        assertTrue(Issuer.fromDid(Did("did:key:a")).isDid)
    }
}
