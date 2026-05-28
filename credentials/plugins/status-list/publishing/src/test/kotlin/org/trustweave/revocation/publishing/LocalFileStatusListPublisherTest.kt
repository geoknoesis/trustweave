package org.trustweave.revocation.publishing

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFileStatusListPublisherTest {

    private lateinit var tempDir: java.nio.file.Path
    private lateinit var publisher: LocalFileStatusListPublisher

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("status-list-publishing-test")
        val config = LocalFilePublisherConfig(
            directory = tempDir,
            publicUrlPattern = "https://example.com/status-lists/{id}",
        )
        publisher = LocalFileStatusListPublisher(config)
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `publish writes file and returns public URL`() = runTest {
        val content = """{"type":"StatusList2021"}""".encodeToByteArray()
        val url = publisher.publish("sl-001", content, "application/json")

        assertEquals("https://example.com/status-lists/sl-001", url)
        assertTrue(Files.exists(tempDir.resolve("sl-001")))
        val written = Files.readAllBytes(tempDir.resolve("sl-001"))
        assertTrue(written.contentEquals(content))
    }

    @Test
    fun `publish overwrites existing file`() = runTest {
        val first = "first".encodeToByteArray()
        val second = "second".encodeToByteArray()

        publisher.publish("sl-overwrite", first, "application/json")
        publisher.publish("sl-overwrite", second, "application/json")

        val written = Files.readAllBytes(tempDir.resolve("sl-overwrite"))
        assertTrue(written.contentEquals(second))
    }

    @Test
    fun `getUrl returns URL for published status list`() = runTest {
        val content = "payload".encodeToByteArray()
        publisher.publish("sl-002", content, "application/json")

        val url = publisher.getUrl("sl-002")
        assertNotNull(url)
        assertEquals("https://example.com/status-lists/sl-002", url)
    }

    @Test
    fun `getUrl returns null for unpublished status list`() = runTest {
        val url = publisher.getUrl("sl-does-not-exist")
        assertNull(url)
    }

    @Test
    fun `delete removes published file`() = runTest {
        val content = "to-delete".encodeToByteArray()
        publisher.publish("sl-003", content, "application/json")

        assertTrue(Files.exists(tempDir.resolve("sl-003")))

        publisher.delete("sl-003")

        assertNull(publisher.getUrl("sl-003"))
        assertTrue(Files.notExists(tempDir.resolve("sl-003")))
    }

    @Test
    fun `delete is idempotent for non-existent file`() = runTest {
        // Should not throw
        publisher.delete("sl-nonexistent")
    }

    @Test
    fun `publish creates directory if it does not exist`() = runTest {
        val nestedDir = tempDir.resolve("nested/deep")
        val config = LocalFilePublisherConfig(
            directory = nestedDir,
            publicUrlPattern = "https://example.com/{id}",
        )
        val nestedPublisher = LocalFileStatusListPublisher(config)

        val url = nestedPublisher.publish("sl-nested", "data".encodeToByteArray(), "application/json")
        assertEquals("https://example.com/sl-nested", url)
        assertTrue(Files.exists(nestedDir.resolve("sl-nested")))
    }
}
