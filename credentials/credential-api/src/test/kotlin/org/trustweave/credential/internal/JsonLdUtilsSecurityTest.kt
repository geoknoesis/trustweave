package org.trustweave.credential.internal

import kotlinx.serialization.json.*
import org.trustweave.core.serialization.SerializationModule
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Security-focused tests for JsonLdUtils.
 * 
 * Tests edge cases and potential security vulnerabilities in JSON-LD operations,
 * including DoS attack scenarios and boundary conditions.
 */
class JsonLdUtilsSecurityTest {
    
    private val json = Json {
        serializersModule = SerializationModule.default
    }
    
    @Test
    fun `test canonicalization with extremely large document`() {
        // Create a document that would exceed the size limit
        val largeString = "x".repeat(SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES + 1)
        val document = buildJsonObject {
            put("largeField", largeString)
        }
        
        // This should throw an IllegalArgumentException if the size limit is exceeded
        val exception = assertFailsWith<IllegalArgumentException> {
            JsonLdUtils.canonicalizeDocument(document, json)
        }
        
        assertTrue(exception.message?.contains("exceeds maximum size") == true ||
                  exception.message?.contains("size limit") == true,
            "Exception should mention size limit violation")
    }
    
    @Test
    fun `test canonicalization with document at size boundary`() {
        // Create a document just under the size limit
        val boundaryString = "x".repeat(SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES / 2)
        val document = buildJsonObject {
            put("field1", boundaryString)
            put("field2", boundaryString)
        }
        
        // This should succeed (or fail gracefully, not crash)
        val result = try {
            JsonLdUtils.canonicalizeDocument(document, json)
            "success"
        } catch (e: IllegalArgumentException) {
            if (e.message?.contains("exceeds maximum size") == true) {
                "size_exceeded"
            } else {
                throw e
            }
        } catch (e: Exception) {
            "other_error"
        }
        
        // Should either succeed or fail with size limit error, not crash
        assertTrue(result == "success" || result == "size_exceeded",
            "Canonicalization should handle boundary case gracefully")
    }
    
    @Test
    fun `test jsonObjectToMap with deeply nested structure`() {
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
        val result = JsonLdUtils.jsonObjectToMap(nested)
        assertNotNull(result)
        assertTrue(result.containsKey("nested"))
    }
    
    @Test
    fun `test jsonObjectToMap with very large array`() {
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
        val result = JsonLdUtils.jsonObjectToMap(document)
        assertNotNull(result)
        val items = result["items"]
        assertTrue(items is List<*>)
        assertTrue((items as List<*>).size == 1000)
    }
    
    @Test
    fun `test jsonObjectToMap with many fields`() {
        // Create a JSON object with many fields
        val document = buildJsonObject {
            repeat(100) {
                put("field_$it", "value_$it")
            }
        }
        
        // Should handle many fields
        val result = JsonLdUtils.jsonObjectToMap(document)
        assertNotNull(result)
        assertTrue(result.size == 100)
    }
    
    @Test
    fun `test canonicalization with special characters`() {
        // Test with various special characters that might cause issues
        val document = buildJsonObject {
            put("normal", "value")
            put("unicode", "测试")
            put("special", "!@#$%^&*()")
            put("newline", "line1\nline2")
            put("tab", "col1\tcol2")
            put("quotes", "\"quoted\"")
        }
        
        // Should handle special characters
        val result = JsonLdUtils.canonicalizeDocument(document, json)
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }
    
    @Test
    fun `test canonicalization with empty document`() {
        val document = buildJsonObject {}
        
        // Should handle empty document
        val result = JsonLdUtils.canonicalizeDocument(document, json)
        assertNotNull(result)
        // Empty JSON object serializes to "{}" which is not blank
        assertTrue(result.isNotBlank() || result == "{}")
    }
    
    @Test
    fun `test canonicalization with null values`() {
        val document = buildJsonObject {
            put("nullValue", JsonNull)
            put("normalValue", "test")
        }
        
        // Should handle null values
        val result = JsonLdUtils.canonicalizeDocument(document, json)
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }
}



