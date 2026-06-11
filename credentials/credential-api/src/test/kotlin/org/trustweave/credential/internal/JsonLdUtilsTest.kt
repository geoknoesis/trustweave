package org.trustweave.credential.internal

import kotlinx.serialization.json.*
import org.trustweave.core.exception.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Comprehensive tests for JsonLdUtils.
 *
 * Canonicalization is JSON-LD 1.1 expansion + toRdf (titanium-json-ld) followed by
 * RDFC-1.0/URDNA2015 canonical N-Quads (titanium-rdfc) over the official W3C contexts.
 *
 * Canonicalization is fail-closed: documents that cannot be canonicalized (missing or
 * unresolvable @context, no RDF statements, dropped credentialSubject claims) throw
 * [SerializationException] instead of silently falling back to plain JSON serialization.
 */
class JsonLdUtilsTest {

    @Test
    fun `test toJakartaObject with simple object`() {
        val jsonObject = buildJsonObject {
            put("name", "John")
            put("age", 30)
            put("active", true)
        }

        val result = JsonLdUtils.toJakartaObject(jsonObject)

        assertEquals(3, result.size)
        assertEquals("John", result.getString("name"))
        assertEquals(30, result.getInt("age"))
        assertEquals(true, result.getBoolean("active"))
    }

    @Test
    fun `test toJakartaObject with nested object`() {
        val jsonObject = buildJsonObject {
            put("person", buildJsonObject {
                put("name", "John")
                put("age", 30)
            })
        }

        val result = JsonLdUtils.toJakartaObject(jsonObject)

        val personObject = result.getJsonObject("person")
        assertEquals("John", personObject.getString("name"))
        assertEquals(30, personObject.getInt("age"))
    }

    @Test
    fun `test toJakartaObject with array preserves element types`() {
        val jsonObject = buildJsonObject {
            put("items", buildJsonArray {
                add("item1")
                add("item2")
                add(42)
            })
        }

        val result = JsonLdUtils.toJakartaObject(jsonObject)

        val itemsArray = result.getJsonArray("items")
        assertEquals(3, itemsArray.size)
        assertEquals("item1", itemsArray.getString(0))
        assertEquals("item2", itemsArray.getString(1))
        assertEquals(42, itemsArray.getInt(2))
    }

    @Test
    fun `test toJakartaObject with number types`() {
        val jsonObject = buildJsonObject {
            put("int", 42)
            put("long", 123456789L)
            put("double", 3.14)
            put("stringNumber", "123")
        }

        val result = JsonLdUtils.toJakartaObject(jsonObject)

        assertEquals(42, result.getInt("int"))
        assertEquals(123456789L, result.getJsonNumber("long").longValue())
        assertEquals(3.14, result.getJsonNumber("double").doubleValue())
        assertEquals("123", result.getString("stringNumber"))
    }

    @Test
    fun `test canonicalizeDocument with valid document and defined claim terms`() {
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
                add(buildJsonObject {
                    put("name", "https://schema.org/name")
                })
            })
            put("type", buildJsonArray {
                add("VerifiableCredential")
            })
            put("issuer", "did:key:test")
            put("credentialSubject", buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            })
        }

        val result = JsonLdUtils.canonicalizeDocument(document)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        // The claim must be covered by the canonical N-Quads
        assertTrue(result.contains("https://schema.org/name"), "Claim term should appear in N-Quads")
        assertTrue(result.contains("John Doe"), "Claim value should appear in N-Quads")
        // RDFC-1.0 canonical blank node labels use the c14n prefix
        assertTrue(result.contains("_:c14n0"), "Blank nodes must carry canonical c14n labels")
    }

    @Test
    fun `test canonicalizeDocument is deterministic regardless of property order`() {
        val claimsContext = buildJsonObject { put("name", "https://schema.org/name") }
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
                add(claimsContext)
            })
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:test")
            put("credentialSubject", buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            })
        }
        val reordered = buildJsonObject {
            put("credentialSubject", buildJsonObject {
                put("name", "John Doe")
                put("id", "did:key:subject")
            })
            put("issuer", "did:key:test")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
                add(claimsContext)
            })
        }

        assertEquals(
            JsonLdUtils.canonicalizeDocument(document),
            JsonLdUtils.canonicalizeDocument(reordered),
            "URDNA2015 canonical N-Quads must be independent of JSON property order"
        )
    }

    @Test
    fun `test canonicalizeDocument drops undefined claims - fails closed`() {
        // "name" is not defined by the credentials/v1 context, so JSON-LD would silently
        // drop it from the canonical form — the claim would not be signed. Must throw.
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
            })
            put("type", buildJsonArray {
                add("VerifiableCredential")
            })
            put("issuer", "did:key:test")
            put("credentialSubject", buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            })
        }

        val exception = assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
        assertTrue(
            exception.message?.contains("@context") == true,
            "Error should instruct the caller to declare a proper @context, got: ${exception.message}"
        )
    }

    @Test
    fun `test canonicalizeDocument without context throws instead of falling back`() {
        val document = buildJsonObject {
            put("name", "test")
            put("value", 123)
        }

        // No @context: canonicalization produces no RDF statements. The old behaviour was
        // a silent fallback to plain JSON — that is forbidden (non-deterministic signing
        // input). Must throw.
        assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
    }

    @Test
    fun `test canonicalizeDocument with empty object throws`() {
        val document = buildJsonObject { }

        assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
    }

    @Test
    fun `test canonicalizeDocument with unresolvable remote context throws`() {
        // Remote context loading is disabled by default; an unknown context URL must fail
        // canonicalization rather than fall back to plain JSON.
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://unknown.example.com/never-registered/v1")
            })
            put("name", "test")
        }

        val exception = assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
        assertTrue(
            exception.message?.contains("canonicalization") == true ||
                exception.message?.contains("failed") == true,
            "Error should mention the canonicalization failure, got: ${exception.message}"
        )
    }

    @Test
    fun `test canonicalizeDocument respects size limit`() {
        // Use an inline context so the large literal actually reaches the canonical form.
        val largeString = "x".repeat(SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES + 1)
        val document = buildJsonObject {
            put("@context", buildJsonObject {
                put("largeField", "https://example.org/vocab#largeField")
            })
            put("largeField", largeString)
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
        assertTrue(exception.message?.contains("exceeds maximum size") == true)
    }

    @Test
    fun `test canonicalizeDocument with complex nested structure`() {
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
                add("https://w3id.org/security/suites/ed25519-2020/v1")
                add(buildJsonObject {
                    put("degree", "https://example.org/vocab#degree")
                    put("name", "https://schema.org/name")
                    put("field", "https://example.org/vocab#field")
                })
            })
            put("type", buildJsonArray {
                add("VerifiableCredential")
            })
            put("issuer", buildJsonObject {
                put("id", "did:key:issuer")
                put("name", "Test University")
            })
            put("credentialSubject", buildJsonObject {
                put("id", "did:key:subject")
                put("degree", buildJsonObject {
                    put("name", "Bachelor of Science")
                    put("field", "Computer Science")
                })
            })
        }

        val result = JsonLdUtils.canonicalizeDocument(document)

        assertNotNull(result)
        assertTrue(result.isNotBlank())
        assertTrue(result.contains("https://example.org/vocab#degree"))
    }

    @Test
    fun `test toJakartaObject with boolean values`() {
        val jsonObject = buildJsonObject {
            put("trueValue", true)
            put("falseValue", false)
        }

        val result = JsonLdUtils.toJakartaObject(jsonObject)

        assertEquals(true, result.getBoolean("trueValue"))
        assertEquals(false, result.getBoolean("falseValue"))
    }

    @Test
    fun `test toJakartaObject with null handling`() {
        val jsonObject = buildJsonObject {
            put("nullValue", JsonNull)
            put("stringValue", "test")
        }

        val result = JsonLdUtils.toJakartaObject(jsonObject)

        // JSON null must survive the conversion as a JSON null, not be dropped
        assertNotNull(result)
        assertEquals(2, result.size)
        assertTrue(result.isNull("nullValue"))
        assertEquals("test", result.getString("stringValue"))
    }

    @Test
    fun `test toJakartaObject with deep nesting`() {
        val jsonObject = buildJsonObject {
            put("level1", buildJsonObject {
                put("level2", buildJsonObject {
                    put("level3", buildJsonObject {
                        put("value", "deep")
                    })
                })
            })
        }

        val result = JsonLdUtils.toJakartaObject(jsonObject)

        val deep = result.getJsonObject("level1").getJsonObject("level2").getJsonObject("level3")
        assertEquals("deep", deep.getString("value"))
    }
}
