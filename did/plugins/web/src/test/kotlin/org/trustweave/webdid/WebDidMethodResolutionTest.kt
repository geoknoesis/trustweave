package org.trustweave.webdid

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.representation.DidDocumentJsonProducer
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyHandle
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.io.IOException
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * did:web resolution through [WebDidMethod]: redirects are off by default, enabled redirects are
 * checked hop by hop, and the offline fallback only applies when a max stale age is configured.
 */
class WebDidMethodResolutionTest {
    private companion object {
        // Public IPv4 literals: no DNS needed, and not on the SSRF deny-list.
        const val HOST = "8.8.8.8"
        const val OTHER_HOST = "8.8.4.4"
        const val DID = "did:web:$HOST"
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

    private fun client(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(Interceptor { chain -> handler(chain.request()) })
            .build()

    private fun response(
        request: Request,
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

    private fun redirectingTo(target: String) =
        client {
            if (it.url.host == HOST) response(it, 302, location = target) else response(it, 200, body = documentJson(DID))
        }

    @Test
    fun `redirects are not followed by default`() =
        runBlocking<Unit> {
            assertFalse(WebDidConfig.default().followRedirects)
            val method = WebDidMethod(InMemoryKeyManagementService(), redirectingTo("https://$OTHER_HOST/did.json"))

            val result = method.resolveDid(Did(DID))

            val failure = assertIs<DidResolutionResult.Failure.ResolutionError>(result)
            assertTrue(failure.reason.contains("redirect"), failure.reason)
        }

    @Test
    fun `an enabled redirect to a public host is followed`() =
        runBlocking<Unit> {
            val method =
                WebDidMethod(
                    InMemoryKeyManagementService(),
                    redirectingTo("https://$OTHER_HOST/did.json"),
                    WebDidConfig(followRedirects = true),
                )

            assertIs<DidResolutionResult.Success>(method.resolveDid(Did(DID)))
        }

    @Test
    fun `an enabled redirect to an internal address is refused`() =
        runBlocking<Unit> {
            val method =
                WebDidMethod(
                    InMemoryKeyManagementService(),
                    redirectingTo("https://169.254.169.254/latest/meta-data"),
                    WebDidConfig(followRedirects = true),
                )

            val failure = assertIs<DidResolutionResult.Failure.ResolutionError>(method.resolveDid(Did(DID)))
            assertTrue(failure.reason.contains("SSRF guard"), failure.reason)
        }

    @Test
    fun `the offline fallback is used only when a max stale age is configured`() =
        runBlocking<Unit> {
            var online = true
            val http =
                client {
                    if (!online) throw IOException("offline")
                    response(it, 200, body = documentJson(DID))
                }
            val strict = WebDidMethod(InMemoryKeyManagementService(), http)
            val lenient =
                WebDidMethod(
                    InMemoryKeyManagementService(),
                    http,
                    WebDidConfig(additionalProperties = mapOf(WebDidMethod.MAX_STALE_CACHE_SECONDS to 3600)),
                )
            assertIs<DidResolutionResult.Success>(strict.resolveDid(Did(DID)))
            assertIs<DidResolutionResult.Success>(lenient.resolveDid(Did(DID)))

            online = false
            assertIs<DidResolutionResult.Failure>(strict.resolveDid(Did(DID)))
            assertIs<DidResolutionResult.Success>(lenient.resolveDid(Did(DID)))
        }
}
