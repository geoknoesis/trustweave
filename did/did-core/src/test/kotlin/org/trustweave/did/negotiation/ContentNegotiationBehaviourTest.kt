package org.trustweave.did.negotiation

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.representation.DidMediaTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentNegotiationBehaviourTest {
    private val service = DefaultContentNegotiationService()
    private val did = Did("did:example:alice")

    private val document =
        DidDocument(
            id = did,
            context = listOf("https://www.w3.org/ns/did/v1", "https://example.org/ctx"),
            controller = listOf(Did("did:example:bob")),
            verificationMethod =
                listOf(
                    VerificationMethod(
                        id = VerificationMethodId.parse("did:example:alice#key-1"),
                        type = "Ed25519VerificationKey2020",
                        controller = did,
                        publicKeyMultibase = "z6MkExample",
                    ),
                ),
            authentication = listOf(VerificationMethodId.parse("did:example:alice#key-1")),
        )

    @Test
    fun `no Accept header yields the supplied default`() =
        runTest {
            assertEquals(DidMediaTypes.DID, service.negotiateContentType(null))
            assertEquals(DidMediaTypes.DID_JSON, service.negotiateContentType(null, DidMediaTypes.DID_JSON))
        }

    @Test
    fun `a single supported type is returned as is`() =
        runTest {
            assertEquals(DidMediaTypes.DID_LD_JSON, service.negotiateContentType("application/did+ld+json"))
        }

    @Test
    fun `unsupported or empty or wildcard Accept falls back to the default`() =
        runTest {
            assertEquals(DidMediaTypes.DID, service.negotiateContentType("text/html"))
            assertEquals(DidMediaTypes.DID, service.negotiateContentType(""))
            assertEquals(DidMediaTypes.DID, service.negotiateContentType("*/*"))
            assertEquals(DidMediaTypes.DID, service.negotiateContentType("application/did+cbor"))
            assertEquals(
                DidMediaTypes.JSON,
                service.negotiateContentType("application/did+cbor", defaultType = DidMediaTypes.JSON),
            )
        }

    @Test
    fun `unsupported entries are skipped in favour of a later supported one`() =
        runTest {
            assertEquals(
                DidMediaTypes.DID_LD_JSON,
                service.negotiateContentType("text/html, application/did+cbor;q=0.9, application/did+ld+json;q=0.5"),
            )
        }

    @Test
    fun `server preference order wins over client listing order`() =
        runTest {
            // application/did+json outranks application/json in SUPPORTED_DOCUMENT_TYPES.
            assertEquals(
                DidMediaTypes.DID_JSON,
                service.negotiateContentType("application/json, application/did+json"),
            )
            assertEquals(
                DidMediaTypes.DID,
                service.negotiateContentType("application/json, application/did+ld+json, application/did"),
            )
        }

    @Test
    fun `media type parameters and case are normalised away`() =
        runTest {
            assertEquals(
                DidMediaTypes.DID_JSON,
                service.negotiateContentType("Application/DID+JSON; charset=utf-8"),
            )
            assertEquals(
                DidMediaTypes.JSON,
                service.negotiateContentType("  application/json ; q=0.8  "),
            )
        }

    @Test
    fun `every supported type round-trips a document`() =
        runTest {
            for (type in DefaultContentNegotiationService.SUPPORTED_TYPES) {
                val bytes = service.serializeDocument(document, type)
                assertEquals(document, service.deserializeDocument(bytes, type), "round trip via $type")
            }
        }

    @Test
    fun `serialized form is compact UTF-8 JSON carrying the document id`() =
        runTest {
            val text = service.serializeDocument(document, DidMediaTypes.DID).toString(Charsets.UTF_8)
            assertTrue(text.contains("\"id\":\"did:example:alice\""), text)
            assertTrue(!text.contains('\n'), "compact output expected")
            assertTrue(text.contains("https://example.org/ctx"), "non-default context must be kept")
        }

    @Test
    fun `serialize accepts a content type carrying parameters`() =
        runTest {
            val bytes = service.serializeDocument(document, "application/did+json; charset=utf-8")
            assertEquals(document, service.deserializeDocument(bytes, "APPLICATION/DID+JSON"))
        }

    @Test
    fun `default context is restored when absent from the serialized form`() =
        runTest {
            val plain = DidDocument(id = did)
            val text = service.serializeDocument(plain, DidMediaTypes.DID).toString(Charsets.UTF_8)
            assertTrue(!text.contains("@context"), "defaults are not encoded: $text")
            assertEquals(plain, service.deserializeDocument(text.toByteArray(), DidMediaTypes.DID))
        }

    @Test
    fun `unsupported content type is rejected on both paths with a helpful message`() =
        runTest {
            val ser = assertFailsWith<UnsupportedContentTypeException> { service.serializeDocument(document, "application/did+cbor") }
            assertTrue(ser.message!!.contains("application/did+cbor"))
            assertTrue(ser.message!!.contains(DidMediaTypes.DID_LD_JSON), "lists the supported types")
            assertFailsWith<UnsupportedContentTypeException> { service.deserializeDocument(ByteArray(0), "text/plain") }
            assertTrue(UnsupportedContentTypeException("x") is IllegalArgumentException)
        }

    @Test
    fun `malformed payloads fail deserialization rather than yielding a document`() =
        runTest {
            assertFailsWith<SerializationException> { service.deserializeDocument("not json".toByteArray(), DidMediaTypes.DID) }
            assertFailsWith<SerializationException> { service.deserializeDocument("{}".toByteArray(), DidMediaTypes.DID) }
            assertFailsWith<IllegalArgumentException> {
                service.deserializeDocument("""{"id":"not-a-did"}""".toByteArray(), DidMediaTypes.DID)
            }
        }

    @Test
    fun `a higher q-value beats server preference`() =
        runTest {
            assertEquals(
                DidMediaTypes.JSON,
                service.negotiateContentType("application/did;q=0.5, application/json;q=0.9"),
            )
        }

    @Test
    fun `q=0 marks a type as not acceptable`() =
        runTest {
            assertEquals(
                DidMediaTypes.DID_JSON,
                service.negotiateContentType("application/did;q=0, application/did+json"),
            )
            assertEquals(
                DidMediaTypes.JSON,
                service.negotiateContentType("application/did;q=0.0", DidMediaTypes.JSON),
            )
        }

    @Test
    fun `equal q-values keep the server preference`() =
        runTest {
            assertEquals(
                DidMediaTypes.DID_LD_JSON,
                service.negotiateContentType("application/json;q=0.8, application/did+ld+json;q=0.8"),
            )
        }

    @Test
    fun `a malformed q-value counts as one, an oversized one is clamped to one`(): Unit =
        runTest {
            assertEquals(
                DidMediaTypes.DID_JSON,
                service.negotiateContentType("application/did+ld+json;q=0.5, application/did+json;q=abc"),
            )
            assertEquals(
                DidMediaTypes.DID_LD_JSON,
                service.negotiateContentType("application/did+json;q=0.5, application/did+ld+json;q=5"),
            )
        }

    @Test
    fun `the q parameter is case insensitive and found among other parameters`(): Unit =
        runTest {
            assertEquals(
                DidMediaTypes.DID_LD_JSON,
                service.negotiateContentType("application/did+json;Q=0.1, application/did+ld+json;profile=x;q=0.9"),
            )
        }

    @Test
    fun `empty list elements are ignored`(): Unit =
        runTest {
            assertEquals(DidMediaTypes.DID_JSON, service.negotiateContentType(", ,application/did+json"))
        }
}
