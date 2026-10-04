package org.trustweave.trust

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.core.plugin.PluginLifecycle
import org.trustweave.did.DidMethod
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.testkit.trust.InMemoryTrustRegistry
import org.trustweave.trust.dsl.ComponentOwnership
import org.trustweave.trust.dsl.TrustWeaveConfig
import org.trustweave.trust.internal.TrustWeaveShutdown
import org.trustweave.trust.services.TrustRegistryFactory
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Facade close()/ownership tests: TrustWeave.close() must close every component the
 * factory created (Closeable or PluginLifecycle-bearing), never caller-injected ones,
 * and stay idempotent on double-close.
 */
class TrustWeaveCloseTest {
    private class CloseableKms(
        delegate: KeyManagementService = InMemoryKeyManagementService(),
    ) : KeyManagementService by delegate,
        Closeable {
        val closeCount = AtomicInteger(0)

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class CloseableTrustRegistry(
        delegate: TrustRegistry = InMemoryTrustRegistry(),
    ) : TrustRegistry by delegate,
        Closeable {
        val closeCount = AtomicInteger(0)

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class CloseableDidMethod(
        delegate: DidMethod,
    ) : DidMethod by delegate,
        Closeable {
        val closeCount = AtomicInteger(0)

        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    /** Lifecycle-bearing (not Closeable) component: close() must drive stop()+cleanup(). */
    private class LifecycleDidMethod(
        delegate: DidMethod,
    ) : DidMethod by delegate,
        PluginLifecycle {
        val calls = mutableListOf<String>()

        override suspend fun initialize(config: Map<String, Any?>): Boolean {
            calls.add("initialize")
            return true
        }

        override suspend fun start(): Boolean {
            calls.add("start")
            return true
        }

        override suspend fun stop(): Boolean {
            calls.add("stop")
            return true
        }

        override suspend fun cleanup() {
            calls.add("cleanup")
        }
    }

    private fun directConfig(
        kms: KeyManagementService,
        didRegistry: DidMethodRegistry = DidMethodRegistry(),
        ownership: ComponentOwnership = ComponentOwnership(),
    ) = TrustWeaveConfig(
        name = "close-test",
        kms = kms,
        didRegistry = didRegistry,
        blockchainRegistry = BlockchainAnchorRegistry(),
        credentialConfig = TrustWeaveConfig.CredentialConfig(),
        credentialService = null,
        ownership = ownership,
    )

    @Test
    fun `close closes the trust registry the factory created during build`() =
        runBlocking<Unit> {
            var created: CloseableTrustRegistry? = null
            val trustWeave =
                TrustWeave.build {
                    keys {
                        provider("inMemory")
                        algorithm("Ed25519")
                    }
                    did {
                        method("key") { algorithm("Ed25519") }
                    }
                    trust { provider("inMemory") }
                    factories(
                        trustRegistryFactory =
                            object : TrustRegistryFactory {
                                override suspend fun create(providerName: String): TrustRegistry =
                                    CloseableTrustRegistry().also {
                                        created =
                                            it
                                    }
                            },
                    )
                }

            val registry = assertNotNull(created, "factory should have created the trust registry")
            assertEquals(0, registry.closeCount.get())

            trustWeave.close()

            assertEquals(1, registry.closeCount.get())
        }

    @Test
    fun `close does not close a caller-injected KMS`() =
        runBlocking<Unit> {
            val kms = CloseableKms()
            val trustWeave =
                TrustWeave.build {
                    keys { custom(kms) }
                    did {
                        method("key") { algorithm("Ed25519") }
                    }
                }

            trustWeave.close()

            assertEquals(0, kms.closeCount.get(), "caller-owned KMS must not be closed by the facade")
        }

    @Test
    fun `close closes a facade-owned KMS and double-close is idempotent`() {
        val kms = CloseableKms()
        val trustWeave = TrustWeave.from(directConfig(kms)) // default ownership: ownsKms = true

        trustWeave.close()
        assertEquals(1, kms.closeCount.get())

        trustWeave.close()
        assertEquals(1, kms.closeCount.get(), "double-close must be a no-op")
    }

    @Test
    fun `close closes owned DID method instances`() {
        val backingKms = InMemoryKeyManagementService()
        val ownedMethod = CloseableDidMethod(DidKeyMockMethod(backingKms))
        val didRegistry = DidMethodRegistry().apply { register(ownedMethod) }
        val trustWeave =
            TrustWeave.from(
                directConfig(
                    kms = backingKms,
                    didRegistry = didRegistry,
                    ownership =
                        ComponentOwnership(
                            ownsKms = false,
                            ownedDidMethods = listOf(ownedMethod),
                        ),
                ),
            )

        trustWeave.close()

        assertEquals(1, ownedMethod.closeCount.get())
    }

    @Test
    fun `close drives stop and cleanup on lifecycle-bearing owned components`() {
        val backingKms = InMemoryKeyManagementService()
        val ownedMethod = LifecycleDidMethod(DidKeyMockMethod(backingKms))
        val didRegistry = DidMethodRegistry().apply { register(ownedMethod) }
        val trustWeave =
            TrustWeave.from(
                directConfig(
                    kms = backingKms,
                    didRegistry = didRegistry,
                    ownership =
                        ComponentOwnership(
                            ownsKms = false,
                            ownedDidMethods = listOf(ownedMethod),
                        ),
                ),
            )

        trustWeave.close()

        assertEquals(listOf("stop", "cleanup"), ownedMethod.calls)
    }

    @Test
    fun `closeAsync suspends through lifecycle teardown and shares idempotence with close`() =
        runBlocking<Unit> {
            val backingKms = CloseableKms()
            val ownedMethod = LifecycleDidMethod(DidKeyMockMethod(backingKms))
            val didRegistry = DidMethodRegistry().apply { register(ownedMethod) }
            val trustWeave =
                TrustWeave.from(
                    directConfig(
                        kms = backingKms,
                        didRegistry = didRegistry,
                        ownership = ComponentOwnership(ownedDidMethods = listOf(ownedMethod)),
                    ),
                )

            trustWeave.closeAsync()
            trustWeave.close()
            trustWeave.closeAsync()

            assertEquals(listOf("stop", "cleanup"), ownedMethod.calls)
            assertEquals(1, backingKms.closeCount.get())
        }

    @Test
    fun `close does not close DID methods the caller registered after construction`() =
        runBlocking<Unit> {
            val trustWeave =
                TrustWeave.build {
                    keys {
                        provider("inMemory")
                        algorithm("Ed25519")
                    }
                    did {
                        method("key") { algorithm("Ed25519") }
                    }
                }
            val callerMethod = CloseableDidMethod(DidKeyMockMethod(InMemoryKeyManagementService()))
            // Caller-registered after construction: caller-owned, must not be closed.
            trustWeave.getDidRegistry().register(callerMethod)

            trustWeave.close()

            assertEquals(0, callerMethod.closeCount.get())
        }

    @Test
    fun `component close failure does not prevent closing the remaining components`() {
        val kms = CloseableKms()
        val throwingMethod =
            object : DidMethod by DidKeyMockMethod(kms), Closeable {
                override fun close(): Unit = throw IllegalStateException("teardown failure")
            }
        val didRegistry = DidMethodRegistry().apply { register(throwingMethod) }
        val trustWeave =
            TrustWeave.from(
                directConfig(
                    kms = kms,
                    didRegistry = didRegistry,
                    ownership =
                        ComponentOwnership(
                            ownsKms = true,
                            ownedDidMethods = listOf(throwingMethod),
                        ),
                ),
            )

        trustWeave.close() // must not throw

        assertEquals(1, kms.closeCount.get(), "KMS must still be closed after an earlier failure")
    }

    // ---- blocking close() runs on a dedicated thread and is bounded ----

    /** A lifecycle component whose stop() does real suspending work on other dispatchers. */
    private class SuspendingLifecycleDidMethod(
        delegate: DidMethod,
        private val onStop: suspend () -> Unit,
    ) : DidMethod by delegate,
        PluginLifecycle {
        val stopped = AtomicInteger(0)
        val stopThread =
            java.util.concurrent.atomic
                .AtomicReference<String?>(null)

        override suspend fun initialize(config: Map<String, Any?>) = true

        override suspend fun start() = true

        override suspend fun stop(): Boolean {
            stopThread.set(Thread.currentThread().name)
            onStop()
            stopped.incrementAndGet()
            return true
        }

        override suspend fun cleanup() = Unit
    }

    private fun configOwning(vararg methods: DidMethod): TrustWeaveConfig {
        val kms = InMemoryKeyManagementService()
        return directConfig(
            kms = kms,
            didRegistry = DidMethodRegistry().apply { methods.forEach { register(it) } },
            ownership = ComponentOwnership(ownsKms = false, ownedDidMethods = methods.toList()),
        )
    }

    @Test
    fun `close from a single threaded dispatcher completes and runs on a dedicated thread`() {
        val method =
            SuspendingLifecycleDidMethod(DidKeyMockMethod(InMemoryKeyManagementService())) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(50) }
            }
        val trustWeave = TrustWeave.from(configOwning(method))
        val single =
            java.util.concurrent.Executors
                .newSingleThreadExecutor { Thread(it, "caller-single") }
        try {
            runBlocking(single.asCoroutineDispatcher()) {
                assertTrue(Thread.currentThread().name.startsWith("caller-single"))
                trustWeave.close()
            }
        } finally {
            single.shutdownNow()
        }

        assertEquals(1, method.stopped.get())
        assertTrue(method.stopThread.get()!!.startsWith("trustweave-close-"), "stop() ran on ${method.stopThread.get()}")
    }

    @Test
    fun `a stuck component cannot hang a blocking close beyond the timeout`() {
        val never = kotlinx.coroutines.CompletableDeferred<Unit>()
        val stuck = SuspendingLifecycleDidMethod(DidKeyMockMethod(InMemoryKeyManagementService())) { never.await() }
        val shutdown = TrustWeaveShutdown(configOwning(stuck), org.slf4j.LoggerFactory.getLogger("test"))

        val started = System.nanoTime()
        shutdown.closeBlocking(timeoutMillis = 300)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertTrue(elapsedMs in 250..5_000, "returned after $elapsedMs ms")
        assertEquals(0, stuck.stopped.get())
        never.cancel() // lets the abandoned daemon thread finish
    }

    @Test
    fun `an interrupted caller still waits for the close and keeps its interrupt flag`() {
        val method =
            SuspendingLifecycleDidMethod(DidKeyMockMethod(InMemoryKeyManagementService())) {
                kotlinx.coroutines.delay(300)
            }
        val trustWeave = TrustWeave.from(configOwning(method))

        Thread.currentThread().interrupt()
        try {
            trustWeave.close()
            assertTrue(Thread.currentThread().isInterrupted, "the interrupt must be re-asserted, not swallowed")
        } finally {
            Thread.interrupted() // clear for the rest of the suite
        }
        assertEquals(1, method.stopped.get(), "close() must still have completed")
    }

    @Test
    fun `a failure inside the dedicated close thread is logged and close never throws`() {
        val boom =
            SuspendingLifecycleDidMethod(DidKeyMockMethod(InMemoryKeyManagementService())) {
                throw IllegalStateException("stop failed")
            }
        TrustWeave.from(configOwning(boom)).close() // must not throw
        assertEquals(0, boom.stopped.get())
    }
}
