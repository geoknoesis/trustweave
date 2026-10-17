package org.trustweave.kms

import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Loaded only on the test classpath; latches are supplied per call to avoid shared test state. */
class LifecycleTestProvider : KeyManagementServiceProvider {
    override val name = "lifecycle-test"
    override val supportedAlgorithms: Set<Algorithm> = emptySet()

    override fun create(options: Map<String, Any?>): KeyManagementService {
        (options["entered"] as? CountDownLatch)?.countDown()
        (options["release"] as? CountDownLatch)?.let { check(it.await(10, TimeUnit.SECONDS)) }
        (options["onCreate"] as? Runnable)?.run()
        (options["service"] as? KeyManagementService)?.let { return it }
        val closeCount = options["closeCount"] as? AtomicInteger
        return object : KeyManagementService by InMemoryKeyManagementService(), AutoCloseable {
            override fun close() {
                closeCount?.incrementAndGet()
                (options["onClose"] as? Runnable)?.run()
                check(options["closeFailure"] != true) { "Test close failure" }
            }
        }
    }
}

class KeyManagementServicesLifecycleRaceTest {
    @Test
    fun `provider creation cannot reenter lifecycle mutations`() {
        KeyManagementServices.resetShutdownForTesting()
        KeyManagementServices.clearCache()
        try {
            val callback =
                Runnable {
                    assertFailsWith<IllegalStateException> { KeyManagementServices.shutdown() }
                    assertFailsWith<IllegalStateException> { KeyManagementServices.clearCache() }
                    assertFailsWith<IllegalStateException> { KeyManagementServices.resetShutdownForTesting() }
                }
            KeyManagementServices.create("lifecycle-test", mapOf("onCreate" to callback))
            assertFalse(KeyManagementServices.isShutdown())
            assertEquals(1, KeyManagementServices.getCacheSize())
        } finally {
            KeyManagementServices.shutdown()
            KeyManagementServices.resetShutdownForTesting()
        }
    }

    @Test
    fun `shutdown closes a shared client only once across different cache keys`() {
        KeyManagementServices.resetShutdownForTesting()
        KeyManagementServices.clearCache()
        val closeCount = AtomicInteger()
        val service =
            object : KeyManagementService by InMemoryKeyManagementService(), AutoCloseable {
                override fun close() {
                    closeCount.incrementAndGet()
                }
            }
        try {
            KeyManagementServices.create("lifecycle-test", mapOf("service" to service, "region" to "one"))
            KeyManagementServices.create("lifecycle-test", mapOf("service" to service, "region" to "two"))
            assertEquals(2, KeyManagementServices.getCacheSize())
            KeyManagementServices.shutdown()
            assertEquals(1, closeCount.get())
        } finally {
            KeyManagementServices.shutdown()
            KeyManagementServices.resetShutdownForTesting()
        }
    }

    @Test
    fun `shutdown continues after a close failure and permits cleanup callbacks`() {
        KeyManagementServices.resetShutdownForTesting()
        KeyManagementServices.clearCache()
        val counts = List(3) { AtomicInteger() }
        try {
            counts.forEachIndexed { index, count ->
                KeyManagementServices.create(
                    "lifecycle-test",
                    mapOf(
                        "index" to index,
                        "closeCount" to count,
                        "closeFailure" to (index == 0),
                        "onClose" to Runnable { KeyManagementServices.shutdown() },
                    ),
                )
            }
            KeyManagementServices.shutdown()
            assertEquals(listOf(1, 1, 1), counts.map { it.get() })
            assertEquals(0, KeyManagementServices.getCacheSize())
        } finally {
            KeyManagementServices.shutdown()
            KeyManagementServices.resetShutdownForTesting()
        }
    }

    @Test
    fun `shutdown during hashing prevents creation for both configuration overloads`() {
        for (typed in listOf(false, true)) {
            KeyManagementServices.resetShutdownForTesting()
            KeyManagementServices.clearCache()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val executor = Executors.newSingleThreadExecutor()
            val blockingValue =
                object {
                    override fun toString(): String {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        return "configuration-value"
                    }
                }
            try {
                val creation =
                    executor.submit {
                        assertFailsWith<IllegalStateException> {
                            create(typed, mapOf("value" to blockingValue))
                        }
                    }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                KeyManagementServices.shutdown()
                release.countDown()
                creation.get(10, TimeUnit.SECONDS)
                assertEquals(0, KeyManagementServices.getCacheSize())
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
                KeyManagementServices.resetShutdownForTesting()
                KeyManagementServices.clearCache()
            }
        }
    }

    @Test
    fun `shutdown waits for in-flight creation and closes its client exactly once`() {
        for (typed in listOf(false, true)) {
            KeyManagementServices.resetShutdownForTesting()
            KeyManagementServices.clearCache()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val shutdownStarted = CountDownLatch(1)
            val shutdownThread = AtomicReference<Thread>()
            val closeCount = AtomicInteger()
            val executor = Executors.newFixedThreadPool(2)
            try {
                val creation =
                    executor.submit {
                        create(typed, mapOf("entered" to entered, "release" to release, "closeCount" to closeCount))
                    }
                assertTrue(entered.await(10, TimeUnit.SECONDS))
                val shutdown =
                    executor.submit {
                        shutdownThread.set(Thread.currentThread())
                        shutdownStarted.countDown()
                        KeyManagementServices.shutdown()
                    }
                assertTrue(shutdownStarted.await(10, TimeUnit.SECONDS))
                awaitShutdownLock(assertNotNull(shutdownThread.get()), shutdown)
                release.countDown()
                creation.get(10, TimeUnit.SECONDS)
                shutdown.get(10, TimeUnit.SECONDS)
                KeyManagementServices.shutdown()
                assertEquals(1, closeCount.get())
                assertEquals(0, KeyManagementServices.getCacheSize())
                assertFailsWith<IllegalStateException> { create(typed, emptyMap()) }
            } finally {
                release.countDown()
                executor.shutdownNow()
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
                KeyManagementServices.resetShutdownForTesting()
                KeyManagementServices.clearCache()
            }
        }
    }

    private fun awaitShutdownLock(
        thread: Thread,
        completion: Future<*>,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (completion.isDone) {
                completion.get()
                fail("Shutdown returned before the in-flight creation was released")
            }
            // A parked writer at this exact lock is evidence of exclusion, not a scheduling delay.
            if (
                thread.state == Thread.State.WAITING &&
                thread.stackTrace.any {
                    it.className == "java.util.concurrent.locks.ReentrantReadWriteLock\$WriteLock" && it.methodName == "lock"
                }
            ) {
                return
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
        }
        fail("Shutdown never reached the lifecycle write lock")
    }

    private fun create(
        typed: Boolean,
        options: Map<String, Any?>,
    ): KeyManagementService =
        if (typed) {
            KeyManagementServices.create("lifecycle-test", KmsCreationOptions(additionalProperties = options))
        } else {
            KeyManagementServices.create("lifecycle-test", options)
        }
}
