package org.trustweave.kms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmsCreationOptionsTest {
    @Test
    fun `test KmsCreationOptions default values`() {
        val options = KmsCreationOptions()
        assertTrue(options.enabled, "Default enabled should be true")
        assertNull(options.priority, "Default priority should be null")
        assertTrue(options.additionalProperties.isEmpty(), "Default additionalProperties should be empty")
    }

    @Test
    fun `test KmsCreationOptions with all values`() {
        val options = KmsCreationOptions(
            enabled = false,
            priority = 10,
            additionalProperties = mapOf("region" to "us-east-1", "key" to "value")
        )
        assertFalse(options.enabled)
        assertEquals(10, options.priority)
        assertEquals(2, options.additionalProperties.size)
        assertEquals("us-east-1", options.additionalProperties["region"])
    }

    @Test
    fun `test KmsCreationOptions toMap with all values`() {
        val options = KmsCreationOptions(
            enabled = false,
            priority = 10,
            additionalProperties = mapOf("region" to "us-east-1")
        )
        val map = options.toMap()
        assertTrue(map.containsKey("enabled"))
        assertEquals(false, map["enabled"])
        assertTrue(map.containsKey("priority"))
        assertEquals(10, map["priority"])
        assertTrue(map.containsKey("region"))
        assertEquals("us-east-1", map["region"])
    }

    @Test
    fun `test KmsCreationOptions toMap with default enabled`() {
        val options = KmsCreationOptions(
            priority = 10,
            additionalProperties = mapOf("region" to "us-east-1")
        )
        val map = options.toMap()
        // enabled=true is not included in map (only false is included)
        assertFalse(map.containsKey("enabled"))
        assertTrue(map.containsKey("priority"))
        assertTrue(map.containsKey("region"))
    }

    @Test
    fun `test KmsCreationOptionsBuilder`() {
        val builder = KmsCreationOptionsBuilder()
        builder.enabled = false
        builder.priority = 5
        builder.property("region", "us-east-1")
        builder.property("key", "value")
        
        val options = builder.build()
        assertFalse(options.enabled)
        assertEquals(5, options.priority)
        assertEquals(2, options.additionalProperties.size)
        assertEquals("us-east-1", options.additionalProperties["region"])
    }

    @Test
    fun `test kmsCreationOptions DSL`() {
        val options = kmsCreationOptions {
            enabled = false
            priority = 10
            property("region", "us-east-1")
            property("key", "value")
        }
        
        assertFalse(options.enabled)
        assertEquals(10, options.priority)
        assertEquals(2, options.additionalProperties.size)
    }

    @Test
    fun `test KmsCreationOptionsBuilder with multiple properties`() {
        val builder = KmsCreationOptionsBuilder()
        builder.property("key1", "value1")
        builder.property("key2", "value2")
        builder.property("key3", null)
        
        val options = builder.build()
        assertEquals(3, options.additionalProperties.size)
        assertEquals("value1", options.additionalProperties["key1"])
        assertEquals("value2", options.additionalProperties["key2"])
        assertTrue(options.additionalProperties.containsKey("key3"))
    }
}

