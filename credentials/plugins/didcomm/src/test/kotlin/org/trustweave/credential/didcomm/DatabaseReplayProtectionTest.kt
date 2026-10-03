package org.trustweave.credential.didcomm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.Test
import org.trustweave.credential.didcomm.crypto.DidCommCryptoInterface
import org.trustweave.credential.didcomm.exception.DidCommException
import org.trustweave.credential.didcomm.models.DidCommEnvelope
import org.trustweave.credential.didcomm.models.DidCommMessage
import org.trustweave.credential.didcomm.packing.DidCommPacker
import org.trustweave.credential.didcomm.storage.DidCommMessageStorage
import org.trustweave.credential.didcomm.storage.InMemoryDidCommMessageStorage
import javax.sql.DataSource

class DatabaseReplayProtectionTest {
    private fun dataSource(): DataSource =
        JdbcDataSource().apply {
            setURL("jdbc:h2:mem:replay_${System.nanoTime()};DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }

    private fun rowCount(ds: DataSource): Int =
        ds.connection.use { c ->
            c.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM didcomm_replay_ids").use {
                    it.next()
                    it.getInt(1)
                }
            }
        }

    @Test
    fun `an id is accepted once and then refused`() =
        runBlocking<Unit> {
            val store = DatabaseDidCommReplayStore(dataSource())
            store.recordIfAbsent("m1", 2_000, 1_000) shouldBe true
            store.recordIfAbsent("m1", 2_000, 1_001) shouldBe false
            store.recordIfAbsent("m2", 2_000, 1_001) shouldBe true
        }

    @Test
    fun `an id is shared by every store over the same database`() =
        runBlocking<Unit> {
            val ds = dataSource()
            val replicaA = DatabaseDidCommReplayStore(ds)
            val replicaB = DatabaseDidCommReplayStore(ds)
            replicaA.recordIfAbsent("m1", 2_000, 1_000) shouldBe true
            replicaB.recordIfAbsent("m1", 2_000, 1_000) shouldBe false
        }

    @Test
    fun `an id is accepted again only after its retention has passed`() =
        runBlocking<Unit> {
            val store = DatabaseDidCommReplayStore(dataSource())
            store.recordIfAbsent("m1", retainUntilEpochSeconds = 2_000, nowEpochSeconds = 1_000) shouldBe true
            store.recordIfAbsent("m1", 3_000, nowEpochSeconds = 1_999) shouldBe false
            store.recordIfAbsent("m1", 3_000, nowEpochSeconds = 2_000) shouldBe true
        }

    @Test
    fun `concurrent presentations of one id admit exactly one`() =
        runBlocking<Unit> {
            val ds = dataSource()
            val stores = List(4) { DatabaseDidCommReplayStore(ds) }
            val results =
                (1..32)
                    .map { i -> async(Dispatchers.IO) { stores[i % stores.size].recordIfAbsent("race", 5_000, 1_000) } }
                    .awaitAll()
            results.count { it } shouldBe 1
        }

    @Test
    fun `expired rows are purged and live ones are kept`() =
        runBlocking<Unit> {
            val ds = dataSource()
            val store = DatabaseDidCommReplayStore(ds, cleanupIntervalSeconds = 10)
            store.recordIfAbsent("old", 1_100, 1_000)
            store.recordIfAbsent("live", 9_000, 1_000)
            rowCount(ds) shouldBe 2
            store.recordIfAbsent("trigger", 9_000, 2_000)
            rowCount(ds) shouldBe 2 // "old" purged; "live" and "trigger" remain
            store.recordIfAbsent("live", 9_000, 2_001) shouldBe false
        }

    @Test
    fun `table name must be a plain identifier`() {
        shouldThrow<IllegalArgumentException> { DatabaseDidCommReplayStore(dataSource(), "x; DROP TABLE y") }
    }

    private class NoCrypto : DidCommCryptoInterface {
        override suspend fun encrypt(
            message: kotlinx.serialization.json.JsonObject,
            fromDid: String,
            fromKeyId: String,
            toDid: String,
            toKeyId: String,
        ): DidCommEnvelope = throw UnsupportedOperationException()

        override suspend fun decrypt(
            envelope: DidCommEnvelope,
            recipientDid: String,
            recipientKeyId: String,
            senderDid: String,
        ) = throw UnsupportedOperationException()
    }

    private fun packer() = DidCommPacker(NoCrypto(), { null }, { _, _ -> ByteArray(0) })

    private fun plain(id: String) =
        DidCommMessage(
            id = id,
            type = "https://didcomm.org/basicmessage/2.0/message",
            body = buildJsonObject { put("content", "hi") },
        ).toJsonObject().toString()

    private fun storageWithDurableReplay(ds: DataSource): DidCommMessageStorage =
        object : DidCommMessageStorage by InMemoryDidCommMessageStorage() {
            override fun replayStore(): DidCommReplayStore = DatabaseDidCommReplayStore(ds)
        }

    @Test
    fun `DatabaseDidCommService defaults to its storage's durable replay store`() =
        runBlocking<Unit> {
            val ds = dataSource()
            // Two service instances (replicas) with their own storage objects, one shared database.
            val a = DatabaseDidCommService(packer(), { null }, storageWithDurableReplay(ds))
            val b = DatabaseDidCommService(packer(), { null }, storageWithDurableReplay(ds))
            a.receiveMessage(plain("m1"), "did:example:me", "k")
            shouldThrow<DidCommException.UnpackingFailed> { b.receiveMessage(plain("m1"), "did:example:me", "k") }
            rowCount(ds) shouldBe 1
        }

    @Test
    fun `a storage without a durable store falls back to in-memory replay protection`() =
        runBlocking<Unit> {
            val service = DatabaseDidCommService(packer(), { null }, InMemoryDidCommMessageStorage())
            service.receiveMessage(plain("m1"), "did:example:me", "k")
            shouldThrow<DidCommException.UnpackingFailed> { service.receiveMessage(plain("m1"), "did:example:me", "k") }
        }

    @Test
    fun `an explicit replay store wins over the storage default`() =
        runBlocking<Unit> {
            val ds = dataSource()
            val seen = mutableListOf<String>()
            val explicit =
                object : DidCommReplayStore {
                    override suspend fun recordIfAbsent(
                        messageId: String,
                        retainUntilEpochSeconds: Long,
                        nowEpochSeconds: Long,
                    ): Boolean {
                        seen += messageId
                        return true
                    }
                }
            val service = DatabaseDidCommService(packer(), { null }, storageWithDurableReplay(ds), explicit)
            service.receiveMessage(plain("m1"), "did:example:me", "k")
            seen shouldBe listOf("m1")
        }
}
