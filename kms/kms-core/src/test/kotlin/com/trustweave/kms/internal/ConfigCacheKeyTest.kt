package com.trustweave.kms.internal

import com.trustweave.kms.KmsCreationOptions
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.Test

/**
 * Tests for ConfigCacheKey utility.
 */
class ConfigCacheKeyTest {

    @Test
    fun `should generate same key for same map configuration`() {
        val map1 = mapOf("region" to "us-east-1", "key" to "value")
        val map2 = mapOf("region" to "us-east-1", "key" to "value")

        val key1 = ConfigCacheKey.create("aws", map1)
        val key2 = ConfigCacheKey.create("aws", map2)

        assertEquals(key1, key2, "Same configuration should generate same key")
    }

    @Test
    fun `should generate same key for map configuration with different key order`() {
        val map1 = mapOf("key1" to "value1", "key2" to "value2")
        val map2 = mapOf("key2" to "value2", "key1" to "value1")

        val key1 = ConfigCacheKey.create("aws", map1)
        val key2 = ConfigCacheKey.create("aws", map2)

        assertEquals(key1, key2, "Configuration with keys in different order should generate same key")
    }

    @Test
    fun `should generate different keys for different configurations`() {
        val map1 = mapOf("region" to "us-east-1")
        val map2 = mapOf("region" to "us-west-2")

        val key1 = ConfigCacheKey.create("aws", map1)
        val key2 = ConfigCacheKey.create("aws", map2)

        assertNotEquals(key1, key2, "Different configurations should generate different keys")
    }

    @Test
    fun `should generate different keys for different providers`() {
        val config = mapOf("region" to "us-east-1")

        val key1 = ConfigCacheKey.create("aws", config)
        val key2 = ConfigCacheKey.create("azure", config)

        assertNotEquals(key1, key2, "Different providers should generate different keys")
    }

    @Test
    fun `should generate same key for same typed configuration`() {
        val options1 = KmsCreationOptions(enabled = true, priority = 10)
        val options2 = KmsCreationOptions(enabled = true, priority = 10)

        val key1 = ConfigCacheKey.create("aws", options1)
        val key2 = ConfigCacheKey.create("aws", options2)

        assertEquals(key1, key2, "Same typed configuration should generate same key")
    }

    @Test
    fun `should generate different keys for different typed configurations`() {
        val options1 = KmsCreationOptions(enabled = true, priority = 10)
        val options2 = KmsCreationOptions(enabled = false, priority = 20)

        val key1 = ConfigCacheKey.create("aws", options1)
        val key2 = ConfigCacheKey.create("aws", options2)

        assertNotEquals(key1, key2, "Different typed configurations should generate different keys")
    }

    @Test
    fun `should handle empty map configuration`() {
        val key1 = ConfigCacheKey.create("aws", emptyMap())
        val key2 = ConfigCacheKey.create("aws", mapOf<String, Any?>())

        assertEquals(key1, key2, "Empty maps should generate same key")
    }

    @Test
    fun `should handle null values`() {
        val map1 = mapOf("key1" to null, "key2" to "value")
        val map2 = mapOf("key1" to null, "key2" to "value")

        val key1 = ConfigCacheKey.create("aws", map1)
        val key2 = ConfigCacheKey.create("aws", map2)

        assertEquals(key1, key2, "Same configuration with nulls should generate same key")
    }

    @Test
    fun `should handle nested maps`() {
        val map1 = mapOf(
            "config" to mapOf("nested" to "value"),
            "other" to "data"
        )
        val map2 = mapOf(
            "other" to "data",
            "config" to mapOf("nested" to "value")
        )

        val key1 = ConfigCacheKey.create("aws", map1)
        val key2 = ConfigCacheKey.create("aws", map2)

        assertEquals(key1, key2, "Nested maps should generate same key regardless of order")
    }

    @Test
    fun `should handle collections in configuration`() {
        val map1 = mapOf("tags" to listOf("tag1", "tag2"))
        val map2 = mapOf("tags" to listOf("tag1", "tag2"))

        val key1 = ConfigCacheKey.create("aws", map1)
        val key2 = ConfigCacheKey.create("aws", map2)

        assertEquals(key1, key2, "Same collections should generate same key")
    }

    @Test
    fun `should handle typed configuration with additional properties`() {
        val options1 = KmsCreationOptions(
            enabled = true,
            priority = 10,
            additionalProperties = mapOf("region" to "us-east-1")
        )
        val options2 = KmsCreationOptions(
            enabled = true,
            priority = 10,
            additionalProperties = mapOf("region" to "us-east-1")
        )

        val key1 = ConfigCacheKey.create("aws", options1)
        val key2 = ConfigCacheKey.create("aws", options2)

        assertEquals(key1, key2, "Same typed configuration with additional properties should generate same key")
    }

    @Test
    fun `should generate stable keys across multiple calls`() {
        val config = mapOf("region" to "us-east-1", "cacheTtl" to 300)

        val key1 = ConfigCacheKey.create("aws", config)
        val key2 = ConfigCacheKey.create("aws", config)
        val key3 = ConfigCacheKey.create("aws", config)

        assertEquals(key1, key2, "Keys should be stable across multiple calls")
        assertEquals(key2, key3, "Keys should be stable across multiple calls")
    }
}

