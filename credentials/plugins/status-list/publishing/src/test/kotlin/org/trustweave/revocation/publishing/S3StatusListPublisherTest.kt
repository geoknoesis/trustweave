package org.trustweave.revocation.publishing

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.services.s3.S3AsyncClient
import java.lang.reflect.Proxy

class S3StatusListPublisherTest {
    private val config =
        S3PublisherConfig(
            bucket = "test-bucket",
            region = "us-east-1",
            keyPrefix = "status-lists/",
            publicUrlPattern = "https://example.com/status-lists/{id}",
        )

    private fun stubS3Client(onClose: () -> Unit = {}): S3AsyncClient =
        Proxy.newProxyInstance(
            S3AsyncClient::class.java.classLoader,
            arrayOf(S3AsyncClient::class.java),
        ) { _, method, _ ->
            if (method.name == "close") {
                onClose()
                null
            } else {
                throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
        } as S3AsyncClient

    @Test
    fun `rejects path traversal and unsafe characters in statusListId`() =
        runTest {
            val publisher = S3StatusListPublisher(config, stubS3Client())
            val unsafeIds = listOf("", " ", ".", "..", "../secret", "a/b", "a\\b", "foo..bar", "id?x=1", "id#frag")
            for (badId in unsafeIds) {
                assertThrows<IllegalArgumentException>("Expected rejection for publish('$badId')") {
                    publisher.publish(badId, byteArrayOf(1), "application/json")
                }
                assertThrows<IllegalArgumentException>("Expected rejection for delete('$badId')") {
                    publisher.delete(badId)
                }
                assertThrows<IllegalArgumentException>("Expected rejection for getUrl('$badId')") {
                    publisher.getUrl(badId)
                }
            }
        }

    @Test
    fun `implements AutoCloseable and leaves caller-supplied S3AsyncClient open on close`() {
        var closed = false
        val publisher = S3StatusListPublisher(config, stubS3Client(onClose = { closed = true }))
        assertTrue(publisher is AutoCloseable)
        publisher.close()
        assertTrue(!closed, "Caller-supplied S3AsyncClient must not be closed by S3StatusListPublisher.close()")
    }
}
