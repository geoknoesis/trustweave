package org.trustweave.revocation.publishing

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalFilePathSafetyTest {
    private lateinit var root: java.nio.file.Path
    private lateinit var served: java.nio.file.Path
    private lateinit var publisher: LocalFileStatusListPublisher

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("status-list-safety")
        served = Files.createDirectory(root.resolve("served"))
        publisher = LocalFileStatusListPublisher(LocalFilePublisherConfig(served, "https://example.com/sl/{id}"))
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    private val hostile =
        listOf("../escaped", "..", ".", "a/b", "a\\b", "/etc/passwd", "..\\x", "x/../../y", "", "a\u0000b", "C:\\x", ".hidden")

    @Test
    fun `publish refuses ids that could leave the directory`() =
        runTest {
            for (id in hostile) {
                assertFailsWith<IllegalArgumentException>("publish id '$id'") { publisher.publish(id, "x".toByteArray(), "text/plain") }
            }
            assertTrue(Files.list(root).use { it.count() } == 1L, "nothing may be written outside the served directory")
            assertNull(runCatching { Files.list(served).use { it.findFirst().orElse(null) } }.getOrNull())
        }

    @Test
    fun `delete refuses ids that could leave the directory and leaves outside files alone`() =
        runTest {
            val outside = Files.writeString(root.resolve("keep.txt"), "precious")
            for (id in listOf("../keep.txt", "..", "a/../../keep.txt")) {
                assertFailsWith<IllegalArgumentException>("delete id '$id'") { publisher.delete(id) }
            }
            assertEquals("precious", Files.readString(outside))
        }

    @Test
    fun `getUrl refuses hostile ids`() =
        runTest {
            assertFailsWith<IllegalArgumentException> { publisher.getUrl("../keep.txt") }
        }

    @Test
    fun `ordinary identifiers still work`() =
        runTest {
            val url = publisher.publish("sl_001-A.v2", "x".toByteArray(), "text/plain")
            assertEquals("https://example.com/sl/sl_001-A.v2", url)
        }
}
