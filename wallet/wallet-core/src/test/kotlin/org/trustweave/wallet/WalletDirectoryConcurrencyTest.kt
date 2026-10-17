package org.trustweave.wallet

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class WalletDirectoryConcurrencyTest {
    @Test
    fun `DID readers cannot observe a partially registered wallet`() {
        val directory = WalletDirectory()
        val identity = RichWallet("wallet", "did:key:wallet", "did:key:holder")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reading = CountDownLatch(1)
        val lookupThread = AtomicReference<Thread>()
        val wallet =
            object : Wallet by identity, DidManagement by identity {
                override val walletDid: String
                    get() {
                        entered.countDown()
                        check(release.await(10, TimeUnit.SECONDS))
                        return identity.walletDid
                    }
            }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val registration = executor.submit { directory.register(wallet) }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            val lookup =
                executor.submit<Wallet?> {
                    lookupThread.set(Thread.currentThread())
                    reading.countDown()
                    directory.getByDid(identity.walletDid)
                }
            assertTrue(reading.await(10, TimeUnit.SECONDS))
            awaitDirectoryLock(assertNotNull(lookupThread.get()), lookup)
            release.countDown()
            registration.get(10, TimeUnit.SECONDS)
            assertSame(wallet, lookup.get(10, TimeUnit.SECONDS))
            assertSame(wallet, directory.get(wallet.walletId))
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun awaitDirectoryLock(
        thread: Thread,
        completion: Future<*>,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (completion.isDone) {
                completion.get()
                fail("Lookup completed while registration still held the directory lock")
            }
            if (
                thread.state == Thread.State.BLOCKED &&
                thread.stackTrace.any { it.className == WalletDirectory::class.java.name && it.methodName == "getByDid" }
            ) {
                return
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
        }
        fail("Lookup never reached the directory monitor")
    }
}
