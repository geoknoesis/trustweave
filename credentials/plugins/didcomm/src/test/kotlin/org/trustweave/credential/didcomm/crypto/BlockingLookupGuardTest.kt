package org.trustweave.credential.didcomm.crypto

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.credential.didcomm.crypto.interop.BlockingDidDocResolver
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class BlockingLookupGuardTest {
    private fun doc(did: String): DidDocument {
        val vmId = VerificationMethodId.parse("$did#key-1")
        return DidDocument(
            id = Did(did),
            verificationMethod =
                listOf(
                    VerificationMethod(
                        id = vmId,
                        type = "Ed25519VerificationKey2020",
                        controller = Did(did),
                        publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519", "x" to "test-key"),
                    ),
                ),
            keyAgreement = listOf(vmId),
        )
    }

    @Test
    fun `the default fallback timeout is five seconds`() {
        BlockingLookupGuard.DEFAULT_TIMEOUT_MS shouldBe 5_000L
    }

    @Test
    fun `a fallback lookup runs on the dedicated pool, not on the callers thread`() {
        val name = BlockingLookupGuard.run("probe") { Thread.currentThread().name }
        name shouldStartWith "didcomm-fallback-lookup-"
    }

    @Test
    fun `a hung fallback lookup times out instead of pinning the caller`() {
        val e = shouldThrow<IllegalStateException> { BlockingLookupGuard.run("hang", timeoutMs = 100) { delay(10_000) } }
        e.message shouldContain "timed out after 100ms"
    }

    @Test
    fun `fallbacks beyond the concurrency bound are rejected, not queued`() {
        val holders = BlockingLookupGuard.maxConcurrent
        val started = CountDownLatch(holders)
        val release = CountDownLatch(1)
        val threads =
            List(holders) {
                thread {
                    BlockingLookupGuard.run("holder", timeoutMs = 20_000) {
                        started.countDown()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { release.await(15, TimeUnit.SECONDS) }
                    }
                }
            }
        try {
            started.await(10, TimeUnit.SECONDS) shouldBe true
            val e = shouldThrow<IllegalStateException> { BlockingLookupGuard.run("one-too-many", timeoutMs = 100) { 1 } }
            e.message shouldContain "already in flight"
        } finally {
            release.countDown()
            threads.forEach { it.join(10_000) }
        }
    }

    @Test
    fun `a preloaded DID document resolves without any blocking fallback`() =
        runBlocking<Unit> {
            val calls = AtomicInteger()
            val resolver =
                BlockingDidDocResolver { did ->
                    calls.incrementAndGet()
                    doc(did)
                }
            resolver.preload(listOf("did:example:alice", "did:example:bob"))
            calls.get() shouldBe 2
            resolver.resolve("did:example:alice").isPresent shouldBe true
            resolver.resolve("did:example:bob").isPresent shouldBe true
            calls.get() shouldBe 2 // served from the preload, no further resolution
        }

    @Test
    fun `an unloaded DID takes the bounded fallback path and still resolves`() {
        val threadName =
            java.util.concurrent.atomic
                .AtomicReference<String>()
        val resolver =
            BlockingDidDocResolver { did ->
                threadName.set(Thread.currentThread().name)
                doc(did)
            }
        resolver.resolve("did:example:carol").isPresent shouldBe true
        threadName.get() shouldStartWith "didcomm-fallback-lookup-"
    }

    @Test
    fun `a preloaded unresolvable DID is cached as absent`() =
        runBlocking<Unit> {
            val resolver = BlockingDidDocResolver { null }
            resolver.preload(listOf("did:example:ghost"))
            resolver.resolve("did:example:ghost").isPresent shouldBe false
        }

    @Test
    fun `a slow fallback DID resolution fails loudly after its budget`() {
        val resolver = BlockingDidDocResolver(resolveTimeoutMs = 100) { delay(10_000).let { null } }
        shouldThrow<IllegalStateException> { resolver.resolve("did:example:slow") }.message shouldContain "timed out after 100ms"
    }
}
