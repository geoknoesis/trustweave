package org.trustweave.credential.internal

import kotlinx.serialization.json.*
import org.trustweave.core.serialization.SerializationModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Comprehensive tests for JsonLdUtils.
 */
class JsonLdUtilsTest {
    
    private val json = Json {
        serializersModule = SerializationModule.default
    }
    
    @Test
    fun `test jsonObjectToMap with simple object`() {
        val jsonObject = buildJsonObject {
            put("name", "John")
            put("age", 30)
            put("active", true)
        }
        
        val result = JsonLdUtils.jsonObjectToMap(jsonObject)
        
        assertEquals(3, result.size)
        assertEquals("John", result["name"])
        assertEquals(30L, result["age"]) // Numbers become Long
        assertEquals(true, result["active"])
    }
    
    @Test
    fun `test jsonObjectToMap with nested object`() {
        val jsonObject = buildJsonObject {
            put("person", buildJsonObject {
                put("name", "John")
                put("age", 30)
            })
        }
        
        val result = JsonLdUtils.jsonObjectToMap(jsonObject)
        
        assertTrue(result["person"] is Map<*, *>)
        val personMap = result["person"] as Map<*, *>
        assertEquals("John", personMap["name"])
        assertEquals(30L, personMap["age"])
    }
    
    @Test
    fun `test jsonObjectToMap with array`() {
        val jsonObject = buildJsonObject {
            put("items", buildJsonArray {
                add("item1")
                add("item2")
                add(42)
            })
        }
        
        val result = JsonLdUtils.jsonObjectToMap(jsonObject)
        
        assertTrue(result["items"] is List<*>)
        val itemsList = result["items"] as List<*>
        assertEquals(3, itemsList.size)
        assertEquals("item1", itemsList[0])
        assertEquals("item2", itemsList[1])
        assertEquals("42", itemsList[2]) // Numbers in arrays become strings
    }
    
    @Test
    fun `test jsonObjectToMap with number types`() {
        val jsonObject = buildJsonObject {
            put("int", 42)
            put("long", 123456789L)
            put("double", 3.14)
            put("stringNumber", "123")
        }
        
        val result = JsonLdUtils.jsonObjectToMap(jsonObject)
        
        assertEquals(42L, result["int"])
        assertEquals(123456789L, result["long"])
        assertEquals(3.14, result["double"])
        assertEquals("123", result["stringNumber"])
    }
    
    @Test
    fun `test canonicalizeDocument with valid document`() {
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
        
        val result = JsonLdUtils.canonicalizeDocument(document, json)
        
        assertNotNull(result)
        assertTrue(result.isNotBlank())
        // Canonicalization should produce a result (either N-Quads or JSON fallback)
    }
    
    @Test
    fun `test canonicalizeDocument with simple document`() {
        val document = buildJsonObject {
            put("name", "test")
            put("value", 123)
        }
        
        val result = JsonLdUtils.canonicalizeDocument(document, json)
        
        assertNotNull(result)
        // JSON-LD normalization might fail for documents without proper context,
        // but fallback JSON serialization should produce a non-blank result
        // The result should either be N-Quads (non-blank) or JSON fallback ("{...}")
        assertTrue(result.isNotBlank(), "Result should not be blank. Got: '$result'")
    }
    
    @Test
    fun `test canonicalizeDocument with empty object`() {
        val document = buildJsonObject { }
        
        val result = JsonLdUtils.canonicalizeDocument(document, json)
        
        assertNotNull(result)
        // Empty JSON object serializes to "{}" which is not blank
        // If JSON-LD normalization produces empty string, fallback should be "{}"
        assertTrue(result.isNotBlank() || result == "{}", "Result should be '{}' or non-blank. Got: '$result'")
    }
    
    @Test
    fun `test canonicalizeDocument respects size limit`() {
        // Create a document that would exceed the size limit after canonicalization
        // We create a document that's definitely larger than the limit
        val largeString = "x".repeat(SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES + 1)
        val document = buildJsonObject {
            put("largeField", largeString)
        }
        
        // This should throw an IllegalArgumentException if the size limit is exceeded
        // Note: The canonicalized or fallback JSON must exceed the limit
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonLdUtils.canonicalizeDocument(document, json)
        }
        assertTrue(exception.message?.contains("exceeds maximum size") == true)
    }
    
    @Test
    fun `test canonicalizeDocument with complex nested structure`() {
        val document = buildJsonObject {
            put("@context", buildJsonArray {
                add("https://www.w3.org/2018/credentials/v1")
                add("https://w3id.org/security/suites/ed25519-2020/v1")
            })
            put("type", buildJsonArray {
                add("VerifiableCredential")
                add("EducationCredential")
            })
            put("issuer", buildJsonObject {
                put("id", "did:key:issuer")
                put("name", "Test University")
            })
            put("credentialSubject", buildJsonObject {
                put("id", "did:key:subject")
                put("degree", buildJsonObject {
                    put("type", "BachelorDegree")
                    put("name", "Bachelor of Science")
                    put("field", "Computer Science")
                })
            })
        }
        
        val result = JsonLdUtils.canonicalizeDocument(document, json)
        
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }
    
    @Test
    fun `test jsonObjectToMap with boolean values`() {
        val jsonObject = buildJsonObject {
            put("trueValue", true)
            put("falseValue", false)
        }
        
        val result = JsonLdUtils.jsonObjectToMap(jsonObject)
        
        assertEquals(true, result["trueValue"])
        assertEquals(false, result["falseValue"])
    }
    
    @Test
    fun `test jsonObjectToMap with null handling`() {
        val jsonObject = buildJsonObject {
            put("nullValue", JsonNull)
            put("stringValue", "test")
        }
        
        val result = JsonLdUtils.jsonObjectToMap(jsonObject)
        
        // JsonNull should be handled (converted to string or handled appropriately)
        assertNotNull(result)
        assertEquals(2, result.size)
        assertEquals("test", result["stringValue"])
    }
    
    @Test
    fun `test jsonObjectToMap with deep nesting`() {
        val jsonObject = buildJsonObject {
            put("level1", buildJsonObject {
                put("level2", buildJsonObject {
                    put("level3", buildJsonObject {
                        put("value", "deep")
                    })
                })
            })
        }
        
        val result = JsonLdUtils.jsonObjectToMap(jsonObject)
        
        assertTrue(result["level1"] is Map<*, *>)
        val level1 = result["level1"] as Map<*, *>
        assertTrue(level1["level2"] is Map<*, *>)
        val level2 = level1["level2"] as Map<*, *>
        assertTrue(level2["level3"] is Map<*, *>)
        val level3 = level2["level3"] as Map<*, *>
        assertEquals("deep", level3["value"])
    }
}

