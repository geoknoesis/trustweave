package org.trustweave.did.orb

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertIs

class OrbDidMethodDeactivatedTest {
    private lateinit var server: MockWebServer
    private lateinit var method: OrbDidMethod

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        method =
            OrbDidMethod(
                InMemoryKeyManagementService(),
                OrbDidConfig(baseUrl = server.url("/").toString().trimEnd('/'), timeoutSeconds = 5),
                OkHttpClient(),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a node that reports the DID deactivated resolves as Deactivated`() =
        runBlocking<Unit> {
            val id = "did:orb:uAAA:EiAbc"
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"didDocument":{"@context":["https://www.w3.org/ns/did/v1"],"id":"$id"},""" +
                        """"didDocumentMetadata":{"deactivated":true}}""",
                ),
            )
            assertIs<DidResolutionResult.Deactivated>(method.resolveDid(Did(id)))
        }
}
