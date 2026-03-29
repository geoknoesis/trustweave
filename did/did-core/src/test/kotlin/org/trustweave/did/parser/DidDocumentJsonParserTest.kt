package org.trustweave.did.parser

import kotlinx.serialization.json.*
import org.trustweave.did.exception.DidException
import kotlin.test.*

/**
 * Tests for shared DID document JSON parser (DID 1.1 conforming consumer).
 */
class DidDocumentJsonParserTest {

    @Test
    fun `parse minimal document with id only`() {
        val json = buildJsonObject {
            put("id", "did:example:123")
        }
        val doc = DidDocumentJsonParser.parse(json)
        assertEquals("did:example:123", doc.id.value)
        assertEquals(1, doc.context.size)
        assertTrue(doc.context[0].contains("did/v1"))
        assertTrue(doc.verificationMethod.isEmpty())
        assertTrue(doc.authentication.isEmpty())
        assertTrue(doc.service.isEmpty())
    }

    @Test
    fun `parse document with controller and alsoKnownAs`() {
        val json = buildJsonObject {
            put("id", "did:example:123")
            put("controller", "did:key:z6Mkf")
            put("alsoKnownAs", JsonArray(listOf(
                JsonPrimitive("https://example.com/id"),
                JsonPrimitive("did:web:example.com")
            )))
        }
        val doc = DidDocumentJsonParser.parse(json)
        assertEquals(1, doc.controller.size)
        assertEquals("did:key:z6Mkf", doc.controller[0].value)
        assertEquals(2, doc.alsoKnownAs.size)
    }

    @Test
    fun `parse document with embedded VM in authentication`() {
        val json = buildJsonObject {
            put("id", "did:example:123")
            put("authentication", JsonArray(listOf(
                buildJsonObject {
                    put("id", "did:example:123#key-1")
                    put("type", "Ed25519VerificationKey2020")
                    put("controller", "did:example:123")
                }
            )))
        }
        val doc = DidDocumentJsonParser.parse(json)
        assertEquals(1, doc.authentication.size)
        assertEquals("did:example:123#key-1", doc.authentication[0].value)
        assertEquals(1, doc.verificationMethod.size)
        assertEquals("Ed25519VerificationKey2020", doc.verificationMethod[0].type)
    }

    @Test
    fun `parse document with relative ref in authentication`() {
        val json = buildJsonObject {
            put("id", "did:example:456")
            put("verificationMethod", JsonArray(listOf(
                buildJsonObject {
                    put("id", "did:example:456#key-1")
                    put("type", "JsonWebKey2020")
                    put("controller", "did:example:456")
                }
            )))
            put("authentication", JsonArray(listOf(JsonPrimitive("#key-1"))))
        }
        val doc = DidDocumentJsonParser.parse(json)
        assertEquals(1, doc.authentication.size)
        assertEquals("did:example:456#key-1", doc.authentication[0].value)
        assertEquals(1, doc.verificationMethod.size)
    }

    @Test
    fun `parse throws when id missing`() {
        val json = buildJsonObject {
            put("@context", "https://www.w3.org/ns/did/v1")
        }
        assertFailsWith<DidException.InvalidDidFormat> {
            DidDocumentJsonParser.parse(json)
        }
    }
}
