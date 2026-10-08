package org.trustweave.did.base

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AbstractWebDidMethodDeadlineTest {
    private class TestWebDidMethod(
        httpClient: OkHttpClient,
    ) : AbstractWebDidMethod("web", InMemoryKeyManagementService(), httpClient) {
        override fun getDocumentUrl(did: String): String = "https://${did.substringAfter("did:web:")}/.well-known/did.json"

        override suspend fun publishDocument(
            url: String,
            document: DidDocument,
        ): Boolean = true

        override suspend fun createDid(options: DidCreationOptions): DidDocument = throw UnsupportedOperationException()

        override suspend fun resolveDid(did: Did): DidResolutionResult = resolveFromHttp(did.value)

        val guardedTimeoutMillis: Int get() = resolutionClient.callTimeoutMillis
        val guardedPublishTimeoutMillis: Int get() = publishClient.callTimeoutMillis
    }

    @Test
    fun `resolution and publish clients get a default whole-call deadline`() {
        val method = TestWebDidMethod(OkHttpClient())
        assertEquals(30_000, method.guardedTimeoutMillis)
        assertEquals(30_000, method.guardedPublishTimeoutMillis)
    }

    @Test
    fun `a caller-configured call timeout is kept`() {
        val method = TestWebDidMethod(OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build())
        assertEquals(5_000, method.guardedTimeoutMillis)
    }

    @Test
    fun `cancelling the coroutine cancels the in-flight HTTP call`() =
        runBlocking<Unit> {
            val entered = CompletableDeferred<Unit>()
            val callWasCancelled = AtomicBoolean(false)
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            entered.complete(Unit)
                            val deadline = System.nanoTime() + 10_000_000_000L
                            while (System.nanoTime() < deadline) {
                                if (chain.call().isCanceled()) {
                                    callWasCancelled.set(true)
                                    throw IOException("Canceled")
                                }
                                Thread.sleep(20)
                            }
                            throw IOException("never cancelled")
                        },
                    ).build()
            val method = TestWebDidMethod(client)

            val job = async(Dispatchers.Default) { runCatching { method.resolveDid(Did("did:web:8.8.8.8")) } }
            withTimeout(5_000) { entered.await() }
            job.cancelAndJoin()

            assertTrue(callWasCancelled.get(), "the HTTP call must be cancelled when the coroutine is")
        }
}
