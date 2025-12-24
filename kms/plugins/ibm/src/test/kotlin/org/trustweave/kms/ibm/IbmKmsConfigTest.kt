package org.trustweave.kms.ibm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class IbmKmsConfigTest {

    @Test
    fun `test builder creates valid config`() {
        val config = IbmKmsConfig.builder()
            .apiKey("test-api-key")
            .instanceId("test-instance-id")
            .region("us-south")
            .build()

        assertEquals("test-api-key", config.apiKey)
        assertEquals("test-instance-id", config.instanceId)
        assertEquals("us-south", config.region)
    }

    @Test
    fun `test builder without api key throws exception`() {
        assertThrows<IllegalArgumentException> {
            IbmKmsConfig.builder()
                .instanceId("test-instance-id")
                .build()
        }
    }

    @Test
    fun `test builder without instance id throws exception`() {
        assertThrows<IllegalArgumentException> {
            IbmKmsConfig.builder()
                .apiKey("test-api-key")
                .build()
        }
    }

    @Test
    fun `test from map creates valid config`() {
        val config = IbmKmsConfig.fromMap(mapOf(
            "apiKey" to "test-key",
            "instanceId" to "test-instance",
            "region" to "eu-gb"
        ))

        assertEquals("test-key", config.apiKey)
        assertEquals("test-instance", config.instanceId)
        assertEquals("eu-gb", config.region)
    }

    @Test
    fun `test from map uses default region`() {
        val config = IbmKmsConfig.fromMap(mapOf(
            "apiKey" to "test-key",
            "instanceId" to "test-instance"
        ))

        assertEquals("us-south", config.region)
    }

    @Test
    fun `test from map with snake_case keys`() {
        val config = IbmKmsConfig.fromMap(mapOf(
            "api_key" to "test-key",
            "instance_id" to "test-instance"
        ))

        assertEquals("test-key", config.apiKey)
        assertEquals("test-instance", config.instanceId)
    }
}

