package org.trustweave.did.base

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.representation.DidDocumentJsonProducer
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyHandle
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * did:web resolution hardening: redirects are never followed automatically, followed redirects
 * are re-checked hop by hop, resolved addresses are guarded, and an offline fallback only serves
 * a cached document within the configured max stale age.
 */
class AbstractWebDidMethodRedirectAndCacheTest {
    private companion object {
        // TEST-NET-3 (RFC 5737): parsed without DNS, and not on the SSRF deny-list.
        const val HOST = "203.0.113.10"
        const val OTHER_HOST = "203.0.113.11"
        const val DID = "did:web:$HOST"
    }

    private class TestWebDidMethod(
        httpClient: OkHttpClient,
        override val maxRedirects: Int = 0,
        override val maxStaleCacheAge: Duration = Duration.ZERO,
    ) : AbstractWebDidMethod("web", InMemoryKeyManagementService(), httpClient) {
        override fun getDocumentUrl(did: String): String = "https://${did.substringAfter("did:web:")}/.well-known/did.json"

        override suspend fun publishDocument(
            url: String,
            document: DidDocument,
        ): Boolean = true

        override suspend fun createDid(options: DidCreationOptions): DidDocument = throw UnsupportedOperationException()

        override suspend fun resolveDid(did: Did): DidResolutionResult = resolveFromHttp(did.value)
    }

    private fun documentJson(did: String): String {
        val doc =
            DidMethodUtils.buildDidDocument(
                did = did,
                verificationMethod =
                    listOf(
                        DidMethodUtils.createVerificationMethod(
                            did = did,
                            keyHandle = KeyHandle(id = KeyId("key-1"), algorithm = "Ed25519", publicKeyMultibase = "z6Mk"),
                            algorithm = "Ed25519",
                        ),
                    ),
            )
        val json: JsonElement = DidDocumentJsonProducer.toJsonObject(doc, useV1_1Context = true)
        return Json.encodeToString(JsonElement.serializer(), json)
    }

    private fun client(handler: (okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    private fun response(
        request: okhttp3.Request,
        code: Int,
        body: String = "",
        location: String? = null,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("x")
            .apply { if (location != null) header("Location", location) }
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun `a redirect is refused when redirects are not enabled`() =
        runBlocking<Unit> {
            val method =
                TestWebDidMethod(client { response(it, 302, location = "https://$OTHER_HOST/did.json") })

            val ex = assertFailsWith<TrustWeaveException> { method.resolveDid(Did(DID)) }
            assertContains(ex.message, "redirect limit 0")
        }

    @Test
    fun `a redirect to a cloud metadata address is rejected by the SSRF guard on that hop`() =
        runBlocking<Unit> {
            val method =
                TestWebDidMethod(
                    client { response(it, 302, location = "https://169.254.169.254/latest/meta-data") },
                    maxRedirects = 3,
                )

            val ex = assertFailsWith<TrustWeaveException> { method.resolveDid(Did(DID)) }
            assertContains(ex.message, "SSRF guard")
        }

    @Test
    fun `a redirect to a CGNAT address is rejected`() =
        runBlocking<Unit> {
            val method =
                TestWebDidMethod(client { response(it, 301, location = "https://100.64.0.1/x") }, maxRedirects = 3)

            val ex = assertFailsWith<TrustWeaveException> { method.resolveDid(Did(DID)) }
            assertContains(ex.message, "SSRF guard")
        }

    @Test
    fun `a redirect downgrading to http is rejected`() =
        runBlocking<Unit> {
            val method =
                TestWebDidMethod(client { response(it, 302, location = "http://$OTHER_HOST/did.json") }, maxRedirects = 3)

            val ex = assertFailsWith<Exception> { method.resolveDid(Did(DID)) }
            assertContains(ex.message, "HTTPS")
        }

    @Test
    fun `a redirect loop stops at the hop limit`() =
        runBlocking<Unit> {
            val calls = AtomicInteger()
            val method =
                TestWebDidMethod(
                    client {
                        calls.incrementAndGet()
                        response(it, 302, location = "https://$HOST/.well-known/did.json")
                    },
                    maxRedirects = 2,
                )

            val ex = assertFailsWith<TrustWeaveException> { method.resolveDid(Did(DID)) }
            assertContains(ex.message, "redirect limit 2")
            assertEquals(3, calls.get())
        }

    @Test
    fun `an allowed redirect to a public https host is followed`() =
        runBlocking<Unit> {
            val method =
                TestWebDidMethod(
                    client {
                        if (it.url.host == HOST) {
                            response(it, 302, location = "https://$OTHER_HOST/did.json")
                        } else {
                            response(it, 200, body = documentJson(DID))
                        }
                    },
                    maxRedirects = 1,
                )

            assertIs<DidResolutionResult.Success>(method.resolveDid(Did(DID)))
        }

    @Test
    fun `an unreachable endpoint does not serve a cached document by default`() =
        runBlocking<Unit> {
            var online = true
            val method =
                TestWebDidMethod(
                    client {
                        if (!online) throw IOException("offline")
                        response(it, 200, body = documentJson(DID))
                    },
                )
            assertIs<DidResolutionResult.Success>(method.resolveDid(Did(DID)))

            online = false
            val ex = assertFailsWith<TrustWeaveException> { method.resolveDid(Did(DID)) }
            assertContains(ex.message, "max stale age")
        }

    @Test
    fun `an unreachable endpoint serves a cached document within the max stale age`() =
        runBlocking<Unit> {
            var online = true
            val method =
                TestWebDidMethod(
                    client {
                        if (!online) throw IOException("offline")
                        response(it, 200, body = documentJson(DID))
                    },
                    maxStaleCacheAge = 1.hours,
                )
            assertIs<DidResolutionResult.Success>(method.resolveDid(Did(DID)))

            online = false
            assertIs<DidResolutionResult.Success>(method.resolveDid(Did(DID)))
        }

    @Test
    fun `the guarded DNS refuses an internal address returned by the resolver`() {
        val rebinding = fixedDns(byteArrayOf(10, 0, 0, 5))
        val ex = assertFailsWith<UnknownHostException> { ResolutionGuardedDns(rebinding).lookup("example.com") }
        assertContains(ex.message, "SSRF guard")
    }

    @Test
    fun `the guarded DNS passes a public address through`() {
        val public = fixedDns(byteArrayOf(8, 8, 8, 8))
        assertEquals("8.8.8.8", ResolutionGuardedDns(public).lookup("example.com").single().hostAddress)
    }

    private fun fixedDns(address: ByteArray): Dns =
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByAddress(hostname, address))
        }

    private fun assertContains(
        actual: String?,
        expected: String,
    ) = assertTrue(actual != null && actual.contains(expected), "expected '$expected' in: $actual")

    private fun ip(literal: String): InetAddress = InetAddress.getByName(literal)

    @Test
    fun `the deny-list covers ranges the generic guard misses`() {
        listOf(
            "0.0.0.0",
            "0.1.2.3",
            "100.64.0.1",
            "100.127.255.254",
            "192.0.0.8",
            "198.18.0.1",
            "240.0.0.1",
            "255.255.255.255",
            "127.0.0.1",
            "169.254.169.254",
            "::1",
            "fc00::1",
            "fd12:3456::1",
            "fe80::1",
            "::127.0.0.1",
            "::ffff:10.0.0.1",
            "64:ff9b::a00:1",
            "2002:a00:1::1",
        ).forEach { assertEquals(true, ResolutionNetworkGuard.isDisallowed(ip(it)), it) }
    }

    @Test
    fun `public addresses are allowed`() {
        listOf("8.8.8.8", "100.128.0.1", "203.0.113.10", "2001:4860:4860::8888", "2002:808:808::1")
            .forEach { assertEquals(false, ResolutionNetworkGuard.isDisallowed(ip(it)), it) }
    }
}
