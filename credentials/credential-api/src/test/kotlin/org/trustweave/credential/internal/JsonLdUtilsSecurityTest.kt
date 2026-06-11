package org.trustweave.credential.internal

import kotlinx.serialization.json.*
import org.trustweave.core.exception.SerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Security-focused tests for JsonLdUtils.
 *
 * Tests edge cases and potential security vulnerabilities in JSON-LD operations,
 * including DoS attack scenarios, fail-closed canonicalization, and boundary conditions.
 */
class JsonLdUtilsSecurityTest {

    /** Inline context that defines terms via a vocabulary mapping. */
    private fun vocabContext(): JsonObject = buildJsonObject {
        put("@vocab", "https://example.org/vocab#")
    }

    @Test
    fun `test canonicalization with extremely large document`() {
        // Create a document that would exceed the size limit after canonicalization.
        // Terms are defined via @vocab so the literal reaches the canonical form.
        val largeString = "x".repeat(SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES + 1)
        val document = buildJsonObject {
            put("@context", vocabContext())
            put("largeField", largeString)
        }

        // This should throw an IllegalArgumentException if the size limit is exceeded
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonLdUtils.canonicalizeDocument(document)
        }

        assertTrue(exception.message?.contains("exceeds maximum size") == true ||
                  exception.message?.contains("size limit") == true,
            "Exception should mention size limit violation")
    }

    @Test
    fun `test canonicalization with document at size boundary`() {
        // Create a document just under the size limit
        val boundaryString = "x".repeat(SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES / 4)
        val document = buildJsonObject {
            put("@context", vocabContext())
            put("field1", boundaryString)
            put("field2", boundaryString)
        }

        // This should succeed (or fail gracefully with the size error, not crash)
        val result = try {
            JsonLdUtils.canonicalizeDocument(document)
            "success"
        } catch (e: IllegalArgumentException) {
            if (e.message?.contains("exceeds maximum size") == true) {
                "size_exceeded"
            } else {
                throw e
            }
        }

        // Should either succeed or fail with size limit error, not crash
        assertTrue(result == "success" || result == "size_exceeded",
            "Canonicalization should handle boundary case gracefully")
    }

    @Test
    fun `test canonicalization without context fails closed - no plain JSON fallback`() {
        // A document without @context produces no RDF statements. The previous behaviour
        // silently fell back to plain JSON serialization, which makes the signing input
        // non-deterministic and masks context failures. It must throw.
        val document = buildJsonObject {
            put("name", "value")
        }

        assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
    }

    @Test
    fun `test canonicalization with unresolvable context fails closed`() {
        // Remote context fetching is disabled by default: an unknown context URL must
        // throw rather than fall back.
        val document = buildJsonObject {
            put("@context", "https://attacker.example.com/poisoned-context/v1")
            put("name", "value")
        }

        assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
    }

    @Test
    fun `test dropped credentialSubject claims are rejected`() {
        // credentials/v1 does not define "secretClearance"; JSON-LD would silently drop it
        // from the canonical form, leaving the claim unsigned and tamperable.
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
            })
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
            put("credentialSubject", buildJsonObject {
                put("id", "did:key:subject")
                put("secretClearance", "TOP-SECRET")
            })
        }

        val exception = assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
        assertTrue(
            exception.message?.contains("secretClearance") == true,
            "Error should name the dropped claim, got: ${exception.message}"
        )
        assertTrue(
            exception.message?.contains("@context") == true,
            "Error should instruct the caller to declare a proper @context"
        )
    }

    @Test
    fun `test nested undefined credentialSubject claim is rejected`() {
        // "degree" and "name" are defined, but the nested "undefinedField" is not:
        // JSON-LD expansion would silently drop it, leaving it unsigned. The guard must
        // recurse into nested objects and fail closed.
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
                add(buildJsonObject {
                    put("degree", "https://example.org/vocab#degree")
                    put("name", "https://example.org/vocab#name")
                })
            })
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
            put("credentialSubject", buildJsonObject {
                put("id", "did:key:subject")
                put("degree", buildJsonObject {
                    put("name", "Bachelor of Science")
                    put("undefinedField", "silently-dropped")
                })
            })
        }

        val exception = assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
        assertTrue(
            exception.message?.contains("undefinedField") == true,
            "Error should name the dropped nested claim, got: ${exception.message}"
        )
        assertTrue(
            exception.message?.contains("@context") == true,
            "Error should instruct the caller to declare a proper @context, got: ${exception.message}"
        )
    }

    @Test
    fun `test fully defined nested credentialSubject claims canonicalize successfully`() {
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
                add(buildJsonObject {
                    put("degree", "https://example.org/vocab#degree")
                    put("name", "https://example.org/vocab#name")
                })
            })
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
            put("credentialSubject", buildJsonObject {
                put("id", "did:key:subject")
                put("degree", buildJsonObject {
                    put("name", "Bachelor of Science")
                })
            })
        }

        val result = JsonLdUtils.canonicalizeDocument(document)
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `test undefined claim inside credentialSubject array is rejected`() {
        // The guard must also recurse into arrays of subjects.
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
            })
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
            put("credentialSubject", buildJsonArray {
                add(buildJsonObject {
                    put("id", "did:key:subject")
                    put("secretClearance", "TOP-SECRET")
                })
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
    fun `test dropped claim is caught even when an extra expanding term masks the property count`() {
        // Masking attempt against a count-based guard: the context maps "type" to a REGULAR
        // property (not the @type keyword), so it expands to a counted property even though
        // the declared-claim collector ignores "type" (and "id"). Under a property-count
        // comparison the extra expanded property would compensate for the dropped "secret"
        // claim (2 declared names: degree+secret vs 2 expanded properties: customType+degree).
        // The name-based guard must still catch the drop.
        val document = buildJsonObject {
            put("@context", buildJsonObject {
                put("credentialSubject", "https://www.w3.org/2018/credentials#credentialSubject")
                put("type", "https://example.org/vocab#customType")
                put("degree", "https://example.org/vocab#degree")
            })
            put("credentialSubject", buildJsonObject {
                put("type", "Masking")
                put("degree", "Bachelor of Science")
                put("secret", "silently-dropped")
            })
        }

        val exception = assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
        assertTrue(
            exception.message?.contains("secret") == true,
            "Error should name the dropped claim despite the masking term, got: ${exception.message}"
        )
        assertTrue(
            exception.message?.contains("@context") == true,
            "Error should instruct the caller to declare a proper @context"
        )
    }

    @Test
    fun `test context dropping the credentialSubject term entirely fails closed`() {
        // The context defines the subject's claims but NOT credentialSubject itself: the
        // whole subject node is dropped at expansion and cannot be located after the
        // round-trip. The guard must fail closed rather than conclude nothing is missing.
        val document = buildJsonObject {
            put("@context", buildJsonObject {
                put("degree", "https://example.org/vocab#degree")
            })
            put("degree", "outer")
            put("credentialSubject", buildJsonObject {
                put("degree", "Bachelor of Science")
            })
        }

        val exception = assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
        assertTrue(
            exception.message?.contains("credentialSubject") == true,
            "Error should mention credentialSubject, got: ${exception.message}"
        )
    }

    @Test
    fun `test non-object credentialSubject fails closed`() {
        // A credentialSubject that is not a JSON object (or array of objects) cannot be
        // checked for dropped claims; the guard must throw rather than return silently.
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
            })
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
            put("credentialSubject", "did:key:subject")
        }

        val exception = assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
        assertTrue(
            exception.message?.contains("credentialSubject") == true,
            "Error should mention credentialSubject, got: ${exception.message}"
        )
    }

    @Test
    fun `test toJakartaObject with deeply nested structure`() {
        // Create a deeply nested JSON object
        var nested = buildJsonObject {
            put("value", "leaf")
        }

        // Nest it 10 levels deep
        repeat(10) {
            nested = buildJsonObject {
                put("nested", nested)
            }
        }

        // Should handle deep nesting without stack overflow
        val result = JsonLdUtils.toJakartaObject(nested)
        assertNotNull(result)
        assertTrue(result.containsKey("nested"))
    }

    @Test
    fun `test toJakartaObject with very large array`() {
        // Create a JSON object with a very large array
        val largeArray = buildJsonArray {
            repeat(1000) {
                add("item_$it")
            }
        }

        val document = buildJsonObject {
            put("items", largeArray)
        }

        // Should handle large arrays
        val result = JsonLdUtils.toJakartaObject(document)
        assertNotNull(result)
        assertTrue(result.getJsonArray("items").size == 1000)
    }

    @Test
    fun `test toJakartaObject with many fields`() {
        // Create a JSON object with many fields
        val document = buildJsonObject {
            repeat(100) {
                put("field_$it", "value_$it")
            }
        }

        // Should handle many fields
        val result = JsonLdUtils.toJakartaObject(document)
        assertNotNull(result)
        assertTrue(result.size == 100)
    }

    @Test
    fun `test canonicalization with special characters`() {
        // Test with various special characters that might cause issues
        val document = buildJsonObject {
            put("@context", vocabContext())
            put("normal", "value")
            put("unicode", "测试")
            put("special", "!@#$%^&*()")
            put("newline", "line1\nline2")
            put("tab", "col1\tcol2")
            put("quotes", "\"quoted\"")
        }

        // Should handle special characters
        val result = JsonLdUtils.canonicalizeDocument(document)
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `test canonicalization with empty document fails closed`() {
        val document = buildJsonObject {}

        // An empty document yields an empty canonical form — nothing would be signed.
        assertFailsWith<SerializationException> {
            JsonLdUtils.canonicalizeDocument(document)
        }
    }
}
