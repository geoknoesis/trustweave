package org.trustweave.trust

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
import org.trustweave.trust.services.TrustRegistryFactory
import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Facade close()/ownership tests: TrustWeave.close() must close every component the
 * factory created (Closeable or PluginLifecycle-bearing), never caller-injected ones,
 * and stay idempotent on double-close.
 */
class TrustWeaveCloseTest {

    private class CloseableKms(
        delegate: KeyManagementService = InMemoryKeyManagementService()
    ) : KeyManagementService by delegate, Closeable {
        val closeCount = AtomicInteger(0)
        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class CloseableTrustRegistry(
        delegate: TrustRegistry = InMemoryTrustRegistry()
    ) : TrustRegistry by delegate, Closeable {
        val closeCount = AtomicInteger(0)
        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    private class CloseableDidMethod(
        delegate: DidMethod
    ) : DidMethod by delegate, Closeable {
        val closeCount = AtomicInteger(0)
        override fun close() {
            closeCount.incrementAndGet()
        }
    }

    /** Lifecycle-bearing (not Closeable) component: close() must drive stop()+cleanup(). */
    private class LifecycleDidMethod(
        delegate: DidMethod
    ) : DidMethod by delegate, PluginLifecycle {
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
        ownership: ComponentOwnership = ComponentOwnership()
    ) = TrustWeaveConfig(
        name = "close-test",
        kms = kms,
        didRegistry = didRegistry,
        blockchainRegistry = BlockchainAnchorRegistry(),
        credentialConfig = TrustWeaveConfig.CredentialConfig(),
        credentialService = null,
        ownership = ownership
    )

    @Test
    fun `close closes the trust registry the factory created during build`() = runBlocking {
        var created: CloseableTrustRegistry? = null
        val trustWeave = TrustWeave.build {
            keys {
                provider("inMemory")
                algorithm("Ed25519")
            }
            did {
                method("key") { algorithm("Ed25519") }
            }
            trust { provider("inMemory") }
            factories(
                trustRegistryFactory = object : TrustRegistryFactory {
                    override suspend fun create(providerName: String): TrustRegistry =
                        CloseableTrustRegistry().also { created = it }
                }
            )
        }

        val registry = assertNotNull(created, "factory should have created the trust registry")
        assertEquals(0, registry.closeCount.get())

        trustWeave.close()

        assertEquals(1, registry.closeCount.get())
    }

    @Test
    fun `close does not close a caller-injected KMS`() = runBlocking {
        val kms = CloseableKms()
        val trustWeave = TrustWeave.build {
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
        val trustWeave = TrustWeave.from(
            directConfig(
                kms = backingKms,
                didRegistry = didRegistry,
                ownership = ComponentOwnership(
                    ownsKms = false,
                    ownedDidMethods = listOf(ownedMethod)
                )
            )
        )

        trustWeave.close()

        assertEquals(1, ownedMethod.closeCount.get())
    }

    @Test
    fun `close drives stop and cleanup on lifecycle-bearing owned components`() {
        val backingKms = InMemoryKeyManagementService()
        val ownedMethod = LifecycleDidMethod(DidKeyMockMethod(backingKms))
        val didRegistry = DidMethodRegistry().apply { register(ownedMethod) }
        val trustWeave = TrustWeave.from(
            directConfig(
                kms = backingKms,
                didRegistry = didRegistry,
                ownership = ComponentOwnership(
                    ownsKms = false,
                    ownedDidMethods = listOf(ownedMethod)
                )
            )
        )

        trustWeave.close()

        assertEquals(listOf("stop", "cleanup"), ownedMethod.calls)
    }

    @Test
    fun `close does not close DID methods the caller registered after construction`() = runBlocking {
        val trustWeave = TrustWeave.build {
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
        val throwingMethod = object : DidMethod by DidKeyMockMethod(kms), Closeable {
            override fun close() {
                throw IllegalStateException("teardown failure")
            }
        }
        val didRegistry = DidMethodRegistry().apply { register(throwingMethod) }
        val trustWeave = TrustWeave.from(
            directConfig(
                kms = kms,
                didRegistry = didRegistry,
                ownership = ComponentOwnership(
                    ownsKms = true,
                    ownedDidMethods = listOf(throwingMethod)
                )
            )
        )

        trustWeave.close() // must not throw

        assertEquals(1, kms.closeCount.get(), "KMS must still be closed after an earlier failure")
    }
}
