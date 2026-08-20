package org.trustweave.did.base

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.representation.DidDocumentJsonProducer
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression test for did:web's local-deactivation-wins resolution rule (see the
 * [AbstractWebDidMethod.resolveFromHttp] KDoc): once TrustWeave has recorded a did:web DID as
 * deactivated, resolution must return [DidResolutionResult.Deactivated] even when the hosted
 * endpoint still serves a live HTTP 200 with the (still-published) document — local state is
 * authoritative for did:web, fail-safe.
 *
 * A test where the endpoint returns nothing (IOException) would only exercise the pre-existing
 * offline-fallback path; this test instead makes the "endpoint" return a valid 200 on every call,
 * to prove the primary HTTP-200 path itself now honours local deactivation state.
 */
class AbstractWebDidMethodDeactivationTest {
    private companion object {
        // TEST-NET-3 (RFC 5737): a public, non-routable-by-policy IPv4 literal. InetAddress
        // parses IP literals without a real DNS lookup, so PrivateNetworkGuard's resolution
        // check (loopback/private/link-local/multicast) passes offline and deterministically —
        // unlike a hostname, which would need real DNS, and unlike 127.0.0.1/10.x/169.254.x,
        // which the SSRF guard (see AbstractWebDidMethodSsrfTest) deliberately rejects.
        const val HOST = "203.0.113.10"
        const val DID = "did:web:$HOST"
    }

    /**
     * Same fixture shape as [AbstractWebDidMethodSsrfTest]'s private `TestWebDidMethod`: a
     * minimal concrete did:web method mapping `did:web:<host>` to
     * `https://<host>/.well-known/did.json`, with `publishDocument` always succeeding. This copy
     * additionally exposes `deactivateDocumentOnHttp` so the test can record local deactivation
     * state directly, and takes the `httpClient` as a constructor parameter so each test can
     * supply its own canned-response client.
     */
    private class TestWebDidMethod(
        kms: KeyManagementService,
        httpClient: OkHttpClient,
    ) : AbstractWebDidMethod("web", kms, httpClient) {
        override fun getDocumentUrl(did: String): String = "https://${did.substringAfter("did:web:")}/.well-known/did.json"

        override suspend fun publishDocument(
            url: String,
            document: DidDocument,
        ): Boolean = true

        override suspend fun createDid(options: DidCreationOptions): DidDocument =
            throw UnsupportedOperationException("not needed for this test")

        override suspend fun resolveDid(did: Did): DidResolutionResult = resolveFromHttp(did.value)

        suspend fun recordDeactivation(
            didString: String,
            deactivatedDocument: DidDocument,
        ): Boolean = deactivateDocumentOnHttp(didString, deactivatedDocument)
    }

    private fun document(did: String): DidDocument =
        DidMethodUtils.buildDidDocument(
            did = did,
            verificationMethod =
                listOf(
                    DidMethodUtils.createVerificationMethod(
                        did = did,
                        keyHandle =
                            KeyHandle(
                                id = KeyId("key-1"),
                                algorithm = "Ed25519",
                                publicKeyMultibase = "z6Mk",
                            ),
                        algorithm = "Ed25519",
                    ),
                ),
        )

    /**
     * An [OkHttpClient] that never touches the network: an application interceptor short-circuits
     * every call and always serves [document] as a live HTTP 200, proving the "endpoint" really is
     * still up regardless of what TrustWeave has locally recorded about deactivation.
     */
    private fun liveEndpointClient(document: DidDocument): OkHttpClient {
        val jsonElement: JsonElement = DidDocumentJsonProducer.toJsonObject(document, useV1_1Context = true)
        val body = Json.encodeToString(JsonElement.serializer(), jsonElement)
        return OkHttpClient
            .Builder()
            .addInterceptor(
                Interceptor { chain ->
                    Response
                        .Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            ).build()
    }

    @Test
    fun `deactivated did-web resolves to Deactivated even though the endpoint still serves 200`() =
        runBlocking {
            val doc = document(DID)
            val method = TestWebDidMethod(InMemoryKeyManagementService(), liveEndpointClient(doc))

            // Record local deactivation without ever fetching from the (live) endpoint first.
            // Bracket the call with wall-clock reads so the deactivation timestamp asserted below
            // can be verified against an independent window, not just against itself.
            val beforeDeactivation = Clock.System.now()
            val deactivated = method.recordDeactivation(DID, doc)
            val afterDeactivation = Clock.System.now()
            assertTrue(deactivated, "recordDeactivation should have succeeded")

            // The endpoint is still live and serves a valid 200 with the document — resolution must
            // still report Deactivated because local state is authoritative for did:web.
            val result = method.resolveDid(Did(DID))

            assertTrue(
                result is DidResolutionResult.Deactivated,
                "expected Deactivated even though the endpoint served a live 200, got $result",
            )
            assertTrue(result.documentMetadata.deactivated)
            val deactivationTimestamp = result.documentMetadata.updated
            assertNotNull(deactivationTimestamp, "Deactivated result must carry the deactivation `updated` timestamp")
            assertTrue(
                deactivationTimestamp in beforeDeactivation..afterDeactivation,
                "expected updated ($deactivationTimestamp) to fall within the deactivation call's " +
                    "wall-clock window [$beforeDeactivation, $afterDeactivation]",
            )

            // Regression guard: resolveFromHttp's success path used to re-cache the fetched document
            // via storeDocument(), which unconditionally overwrote the stored DidDocumentMetadata
            // with a fresh (non-deactivated) instance — silently clearing the recorded deactivation
            // after exactly one successful resolve. A second resolve against the still-live endpoint
            // must therefore keep returning Deactivated, not resurrect the DID as Success.
            delay(10)
            val secondResult = method.resolveDid(Did(DID))

            assertTrue(
                secondResult is DidResolutionResult.Deactivated,
                "expected Deactivated on a second resolve too, got $secondResult",
            )
            assertTrue(secondResult.documentMetadata.deactivated)

            // Deeper regression guard (timestamp fidelity): storeDocument() used to bump `updated`
            // to "now" on every cache-store, including a plain re-resolve — not just on a real
            // Update operation. Per DID Core §7.3, deactivation is terminal: no Update operation
            // can follow it, so `updated` must stay pinned at the deactivation time rather than
            // drifting forward to each resolve's fetch time. Resolving a THIRD time (spaced apart
            // in wall-clock time via the delays above/below) is what actually catches the drift —
            // resolving only twice was the previous round's regression test, and it is exactly why
            // this defect survived: the pre-storeDocument-fix workaround in resolveFromHttp already
            // made the *first* post-deactivation resolve report the correct timestamp, so a 2nd
            // resolve alone can't distinguish "pinned" from "drifted once".
            delay(10)
            val thirdResult = method.resolveDid(Did(DID))

            assertTrue(
                thirdResult is DidResolutionResult.Deactivated,
                "expected Deactivated on a third resolve too, got $thirdResult",
            )
            assertTrue(thirdResult.documentMetadata.deactivated)

            assertEquals(
                deactivationTimestamp,
                secondResult.documentMetadata.updated,
                "updated must stay pinned at the deactivation time across a 2nd resolve, not drift to the fetch time",
            )
            assertEquals(
                deactivationTimestamp,
                thirdResult.documentMetadata.updated,
                "updated must stay pinned at the deactivation time across a 3rd resolve, not drift further",
            )
        }
}
