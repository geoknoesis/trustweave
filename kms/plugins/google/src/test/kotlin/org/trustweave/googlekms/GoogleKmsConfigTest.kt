package org.trustweave.googlekms

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GoogleKmsConfigTest {

    @Test
    fun `test builder creates valid config`() {
        val config = GoogleKmsConfig.builder()
            .projectId("test-project")
            .location("us-east1")
            .keyRing("test-key-ring")
            .build()

        assertEquals("test-project", config.projectId)
        assertEquals("us-east1", config.location)
        assertEquals("test-key-ring", config.keyRing)
    }

    @Test
    fun `test builder requires project ID`() {
        assertThrows<IllegalArgumentException> {
            GoogleKmsConfig.builder()
                .location("us-east1")
                .build()
        }
    }

    @Test
    fun `test builder requires location`() {
        assertThrows<IllegalArgumentException> {
            GoogleKmsConfig.builder()
                .projectId("test-project")
                .build()
        }
    }

    @Test
    fun `test fromMap creates valid config`() {
        val options = mapOf(
            "projectId" to "test-project",
            "location" to "us-east1",
            "keyRing" to "test-key-ring",
            "credentialsPath" to "/path/to/creds.json"
        )

        val config = GoogleKmsConfig.fromMap(options)

        assertEquals("test-project", config.projectId)
        assertEquals("us-east1", config.location)
        assertEquals("test-key-ring", config.keyRing)
        assertEquals("/path/to/creds.json", config.credentialsPath)
    }

    @Test
    fun `test fromMap requires project ID`() {
        val options = mapOf(
            "location" to "us-east1"
        )

        assertThrows<IllegalArgumentException> {
            GoogleKmsConfig.fromMap(options)
        }
    }

    @Test
    fun `test fromMap requires location`() {
        val options = mapOf(
            "projectId" to "test-project"
        )

        assertThrows<IllegalArgumentException> {
            GoogleKmsConfig.fromMap(options)
        }
    }

    @Test
    fun `test config validation rejects blank project ID`() {
        assertThrows<IllegalArgumentException> {
            GoogleKmsConfig(
                projectId = "",
                location = "us-east1"
            )
        }
    }

    @Test
    fun `test config validation rejects blank location`() {
        assertThrows<IllegalArgumentException> {
            GoogleKmsConfig(
                projectId = "test-project",
                location = ""
            )
        }
    }
}

