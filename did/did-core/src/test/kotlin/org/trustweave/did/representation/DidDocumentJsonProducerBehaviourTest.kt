package org.trustweave.did.representation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidOrUrl
import org.trustweave.did.model.DidService
import org.trustweave.did.model.ServiceEndpoint
import org.trustweave.did.model.VerificationMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidDocumentJsonProducerBehaviourTest {
    private val did = Did("did:example:alice")
    private val key1 = VerificationMethodId.parse("did:example:alice#key-1")
    private val key2 = VerificationMethodId.parse("did:example:alice#key-2")

    private fun vm(
        id: VerificationMethodId = key1,
        jwk: Map<String, Any?>? = null,
        multibase: String? = null,
    ) = VerificationMethod(id, "JsonWebKey2020", did, publicKeyJwk = jwk, publicKeyMultibase = multibase)

    @Test
    fun `minimal document has only context and id`() {
        val json = DidDocumentJsonProducer.toJsonObject(DidDocument(did))
        assertEquals(setOf("@context", "id"), json.keys)
        assertEquals("did:example:alice", json.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun `v1_1 context comes first, replaces v1 and keeps extras without duplicates`() {
        val doc = DidDocument(did, context = listOf(DID_1_0_CONTEXT, "https://example.org/ctx", DID_1_1_CONTEXT))
        val context = DidDocumentJsonProducer.toJsonObject(doc, useV1_1Context = true).getValue("@context")
        assertEquals(listOf(DID_1_1_CONTEXT, "https://example.org/ctx"), context.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `a single context is a string not an array`() {
        val context = DidDocumentJsonProducer.toJsonObject(DidDocument(did)).getValue("@context")
        assertEquals(JsonPrimitive(DID_1_1_CONTEXT), context)
    }

    @Test
    fun `without v1_1 the document context is kept as is`() {
        val doc = DidDocument(did, context = listOf(DID_1_0_CONTEXT, "https://example.org/ctx"))
        val context = DidDocumentJsonProducer.toJsonObject(doc, useV1_1Context = false).getValue("@context")
        assertEquals(listOf(DID_1_0_CONTEXT, "https://example.org/ctx"), context.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `without v1_1 an empty context falls back to v1`() {
        val doc = DidDocument(did, context = emptyList())
        val context = DidDocumentJsonProducer.toJsonObject(doc, useV1_1Context = false).getValue("@context")
        assertEquals(JsonPrimitive(DID_1_0_CONTEXT), context)
    }

    @Test
    fun `controller is a string for one and an array for many`() {
        val one = DidDocumentJsonProducer.toJsonObject(DidDocument(did, controller = listOf(Did("did:example:bob"))))
        assertEquals(JsonPrimitive("did:example:bob"), one.getValue("controller"))

        val many =
            DidDocumentJsonProducer.toJsonObject(
                DidDocument(did, controller = listOf(Did("did:example:bob"), Did("did:example:eve"))),
            )
        assertEquals(listOf("did:example:bob", "did:example:eve"), many.getValue("controller").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `alsoKnownAs mixes DIDs and URLs`() {
        val doc = DidDocument(did, alsoKnownAs = listOf(DidOrUrl.parse("did:web:example.com"), DidOrUrl.parse("https://example.com/me")))
        val aka = DidDocumentJsonProducer.toJsonObject(doc).getValue("alsoKnownAs").jsonArray
        assertEquals(listOf("did:web:example.com", "https://example.com/me"), aka.map { it.jsonPrimitive.content })
    }

    @Test
    fun `verification methods emit only the key material they have`() {
        val doc =
            DidDocument(
                did,
                verificationMethod =
                    listOf(
                        vm(key1, multibase = "z6MkExample"),
                        vm(key2, jwk = mapOf("kty" to "OKP", "crv" to "Ed25519", "x" to "abc")),
                    ),
            )
        val methods =
            DidDocumentJsonProducer
                .toJsonObject(doc)
                .getValue("verificationMethod")
                .jsonArray
                .map { it.jsonObject }

        assertEquals("did:example:alice#key-1", methods[0].getValue("id").jsonPrimitive.content)
        assertEquals("z6MkExample", methods[0].getValue("publicKeyMultibase").jsonPrimitive.content)
        assertFalse("publicKeyJwk" in methods[0])
        assertFalse("publicKeyMultibase" in methods[1])
        assertEquals(
            "Ed25519",
            methods[1]
                .getValue("publicKeyJwk")
                .jsonObject
                .getValue("crv")
                .jsonPrimitive.content,
        )
        assertEquals("did:example:alice", methods[1].getValue("controller").jsonPrimitive.content)
    }

    @Test
    fun `JWK values are converted by type including nesting and null`() {
        val jwk =
            mapOf(
                "s" to "text",
                "flag" to true,
                "n" to null,
                "list" to listOf("a", 2.5),
                "nested" to mapOf("k" to "v"),
                "other" to java.net.URI("urn:x:y"),
            )
        val out =
            DidDocumentJsonProducer
                .toJsonObject(DidDocument(did, verificationMethod = listOf(vm(jwk = jwk))))
                .getValue("verificationMethod")
                .jsonArray[0]
                .jsonObject
                .getValue("publicKeyJwk")
                .jsonObject

        assertEquals("text", out.getValue("s").jsonPrimitive.content)
        assertTrue(out.getValue("flag").jsonPrimitive.boolean)
        assertEquals(kotlinx.serialization.json.JsonNull, out.getValue("n"))
        assertEquals(listOf("a", "2.5"), out.getValue("list").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            "v",
            out
                .getValue("nested")
                .jsonObject
                .getValue("k")
                .jsonPrimitive.content,
        )
        assertEquals("urn:x:y", out.getValue("other").jsonPrimitive.content)
    }

    @Test
    fun `relationships are arrays and omitted when empty`() {
        val doc =
            DidDocument(
                did,
                authentication = listOf(key1),
                assertionMethod = listOf(key1, key2),
                keyAgreement = listOf(key2),
                capabilityInvocation = listOf(key1),
                capabilityDelegation = listOf(key2),
            )
        val json = DidDocumentJsonProducer.toJsonObject(doc)

        assertEquals(listOf("did:example:alice#key-1"), json.getValue("authentication").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(2, json.getValue("assertionMethod").jsonArray.size)
        assertTrue(listOf("keyAgreement", "capabilityInvocation", "capabilityDelegation").all { it in json })
        assertNull(DidDocumentJsonProducer.toJsonObject(DidDocument(did, authentication = listOf(key1)))["assertionMethod"])
    }

    @Test
    fun `service type is a string for one and an array for several, endpoints keep their shape`() {
        val doc =
            DidDocument(
                did,
                service =
                    listOf(
                        DidService("#hub", listOf("LinkedDomains"), ServiceEndpoint.Url("https://example.com")),
                        DidService(
                            "#multi",
                            listOf("A", "B"),
                            ServiceEndpoint.ArrayEndpoint(
                                listOf(
                                    ServiceEndpoint.Url("https://a.example"),
                                    ServiceEndpoint.ObjectEndpoint(
                                        mapOf(
                                            "uri" to "https://b.example",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
            )
        val services =
            DidDocumentJsonProducer
                .toJsonObject(doc)
                .getValue("service")
                .jsonArray
                .map { it.jsonObject }

        assertEquals(JsonPrimitive("LinkedDomains"), services[0].getValue("type"))
        assertEquals(JsonPrimitive("https://example.com"), services[0].getValue("serviceEndpoint"))
        assertEquals(listOf("A", "B"), services[1].getValue("type").jsonArray.map { it.jsonPrimitive.content })
        val endpoints = assertIs<JsonArray>(services[1].getValue("serviceEndpoint"))
        assertEquals(JsonPrimitive("https://a.example"), endpoints[0])
        assertIs<JsonObject>(endpoints[1])
    }

    @Test
    fun `bytes are UTF-8 JSON paired with the application did media type`() {
        val (bytes, mediaType) = DidDocumentJsonProducer.toBytesWithMediaType(DidDocument(did))
        assertEquals(DidMediaTypes.DID, mediaType)
        val parsed = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        assertEquals("did:example:alice", parsed.getValue("id").jsonPrimitive.content)
        assertEquals(JsonPrimitive(DID_1_1_CONTEXT), parsed.getValue("@context"))

        val legacy = DidDocumentJsonProducer.toBytesWithMediaType(DidDocument(did), useV1_1Context = false).first
        assertEquals(
            JsonPrimitive(DID_1_0_CONTEXT),
            Json.parseToJsonElement(legacy.toString(Charsets.UTF_8)).jsonObject.getValue("@context"),
        )
    }

    @Test
    fun `integer JWK members are emitted as integers not doubles`() {
        val jwk = mapOf("kty" to "oct", "n" to 1000, "big" to 12345678901L, "frac" to 1.5)
        val json = DidDocumentJsonProducer.toJsonObject(DidDocument(did, verificationMethod = listOf(vm(jwk = jwk))))
        val text = Json.encodeToString(JsonObject.serializer(), json)
        assertFalse(text.contains("1.0E3"), text)
        assertTrue(text.contains("\"n\":1000"), text)
        assertTrue(text.contains("\"big\":12345678901"), text)
        assertTrue(text.contains("\"frac\":1.5"), text)
    }
}
