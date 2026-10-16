package org.trustweave.kms.ibm

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.SignResult
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Key-id path hardening and transport policy for the IBM plugin. */
class IbmKeyIdAndTransportTest {
    @Test
    fun `key ids that could alter the URL path are rejected before any request`() =
        runBlocking<Unit> {
            val server = MockWebServer().apply { start() }
            try {
                val kms =
                    IbmKeyManagementService(
                        IbmKmsConfig(apiKey = "k", instanceId = "i", serviceUrl = server.url("/").toString().trimEnd('/')),
                        OkHttpClient(),
                    )
                for (bad in listOf("../x", "a/b", "a?b", "a#b", "a\u0001b", "a%2fb", "..")) {
                    assertIs<SignResult.Failure.Error>(kms.sign(KeyId(bad), "d".toByteArray(), Algorithm.P256), "'$bad'")
                    assertIs<DeleteKeyResult.Failure.Error>(kms.deleteKey(KeyId(bad)), "'$bad'")
                }
                assertEquals(0, server.requestCount)
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun `plaintext http to a public host is refused`() {
        assertThrows<IllegalArgumentException> { IbmKmsConfig(apiKey = "k", instanceId = "i", serviceUrl = "http://8.8.8.8") }
    }
}
