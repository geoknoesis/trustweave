package org.trustweave.did.registrar.client

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.adapter.StandardUniversalRegistrarAdapter
import org.trustweave.did.registrar.adapter.UniversalRegistrarProtocolAdapter
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.UpdateDidOptions
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RegistrarApiKeyTest {
    private val finished = """{"didState":{"state":"finished","did":"did:example:1"}}"""

    @Test
    fun `api key is sent as a bearer Authorization header`() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200).setBody(finished))
                server.enqueue(MockResponse().setResponseCode(200).setBody(finished))
                server.start()
                val registrar = DefaultUniversalRegistrar(baseUrl = server.url("").toString().trimEnd('/'), apiKey = "secret-key")
                registrar.createDid("example", CreateDidOptions())
                registrar.getOperationStatus("job-1")
                assertEquals("Bearer secret-key", server.takeRequest().getHeader("Authorization"))
                assertEquals("Bearer secret-key", server.takeRequest().getHeader("Authorization"))
            }
        }

    @Test
    fun `no Authorization header without an api key`() =
        runTest {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200).setBody(finished))
                server.start()
                DefaultUniversalRegistrar(baseUrl = server.url("").toString().trimEnd('/'))
                    .createDid("example", CreateDidOptions())
                assertNull(server.takeRequest().getHeader("Authorization"))
            }
        }

    @Test
    fun `adapter refuses cleartext http to a public host when it holds a key`() =
        runTest {
            val adapter = StandardUniversalRegistrarAdapter(apiKey = "k")
            assertFailsWith<IllegalArgumentException> {
                adapter.createDid("http://registrar.example.org", "example", CreateDidOptions())
            }
        }

    @Test
    fun `http with api key to a non-loopback host is refused at construction`() {
        assertFailsWith<IllegalArgumentException> {
            DefaultUniversalRegistrar(baseUrl = "http://registrar.example.org", apiKey = "k")
        }
    }

    @Test
    fun `custom adapter that cannot send the key fails loudly`() {
        val noAuth =
            object : UniversalRegistrarProtocolAdapter {
                override suspend fun createDid(
                    baseUrl: String,
                    method: String,
                    options: CreateDidOptions,
                ): DidRegistrationResponse = error("unused")

                override suspend fun updateDid(
                    baseUrl: String,
                    did: String,
                    document: DidDocument,
                    options: UpdateDidOptions,
                ): DidRegistrationResponse = error("unused")

                override suspend fun deactivateDid(
                    baseUrl: String,
                    did: String,
                    options: DeactivateDidOptions,
                ): DidRegistrationResponse = error("unused")

                override suspend fun getOperationStatus(
                    baseUrl: String,
                    jobId: String,
                ): DidRegistrationResponse = error("unused")
            }
        assertFailsWith<UnsupportedOperationException> {
            DefaultUniversalRegistrar(baseUrl = "https://r.example.org", apiKey = "k", protocolAdapter = noAuth)
        }
    }
}
