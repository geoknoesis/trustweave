package org.trustweave.credential.didcomm.storage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.junit.jupiter.api.Test
import org.trustweave.credential.didcomm.models.DidCommMessage
import org.trustweave.credential.didcomm.storage.database.MongoDidCommMessageStorage

/** In-memory stand-in for the Mongo driver, reached by the storage through reflection. */
class FakeMongoClient(
    val collection: FakeCollection = FakeCollection(),
) {
    fun getDatabase(name: String) = FakeDatabase(collection)
}

class FakeDatabase(
    private val collection: FakeCollection,
) {
    fun getCollection(
        name: String,
        type: Class<*>,
    ) = collection
}

class FakeCollection {
    val docs = mutableListOf<Document>()
    var failFind: Boolean = false
    var failDelete: Boolean = false
    var failIndex: Boolean = false

    fun createIndex(spec: Any) {
        if (failIndex) throw RuntimeException("not authorised to create indexes")
    }

    fun insertOne(doc: Document) {
        docs += doc
    }

    fun findOne(filter: Any): Document? {
        if (failFind) throw RuntimeException("connection refused")
        val f = filter as Document
        return docs.firstOrNull { d -> f.all { (k, v) -> d[k] == v } }
    }

    fun find(filter: Any): FakeCursor {
        if (failFind) throw RuntimeException("connection refused")
        return FakeCursor(docs.toList())
    }

    fun deleteOne(filter: Any): FakeDeleteResult {
        if (failDelete) throw RuntimeException("write concern error")
        val f = filter as Document
        val hit = docs.firstOrNull { d -> f.all { (k, v) -> d[k] == v } }
        if (hit != null) docs.remove(hit)
        return FakeDeleteResult(if (hit != null) 1L else 0L)
    }
}

class FakeDeleteResult(
    private val n: Long,
) {
    fun deletedCount(): Long = n
}

class FakeCursor(
    private val docs: List<Document>,
) {
    fun sort(spec: Any) = this

    fun skip(n: Int) = this

    fun limit(n: Int) = this

    fun toList(): List<Document> = docs
}

class MongoDidCommMessageStorageTest {
    private val message =
        DidCommMessage(id = "m1", type = "https://didcomm.org/basicmessage/2.0/message", from = "did:key:a", to = listOf("did:key:b"))

    @Test
    fun `a stored message round-trips and a missing one is null`() =
        runBlocking<Unit> {
            val storage = MongoDidCommMessageStorage(FakeMongoClient())
            storage.store(message)
            storage.get("m1")?.id shouldBe "m1"
            storage.get("nope") shouldBe null
            storage.delete("m1") shouldBe true
            storage.delete("m1") shouldBe false
        }

    @Test
    fun `a driver failure while reading is an error, not an empty result`() =
        runBlocking<Unit> {
            val fake = FakeCollection()
            val storage = MongoDidCommMessageStorage(FakeMongoClient(fake))
            storage.store(message)
            fake.failFind = true
            val get = shouldThrow<IllegalStateException> { storage.get("m1") }
            get.message!! shouldContain "connection refused"
            shouldThrow<IllegalStateException> { storage.getMessagesForDid("did:key:a", 10, 0) }
        }

    @Test
    fun `a driver failure while deleting is an error, not zero deletions`() =
        runBlocking<Unit> {
            val fake = FakeCollection()
            val storage = MongoDidCommMessageStorage(FakeMongoClient(fake))
            storage.store(message)
            fake.failDelete = true
            shouldThrow<IllegalStateException> { storage.delete("m1") }
        }

    @Test
    fun `an index creation failure surfaces at construction`() {
        val fake = FakeCollection().apply { failIndex = true }
        val failure = shouldThrow<IllegalStateException> { MongoDidCommMessageStorage(FakeMongoClient(fake)) }
        failure.message!! shouldContain "not authorised"
    }

    @Test
    fun `a missing driver API fails loudly at construction`() {
        shouldThrow<IllegalStateException> { MongoDidCommMessageStorage(Any()) }
    }

    @Test
    fun `an unreadable stored document is skipped while readable ones are returned`() =
        runBlocking<Unit> {
            val fake = FakeCollection()
            val storage = MongoDidCommMessageStorage(FakeMongoClient(fake))
            storage.store(message)
            fake.docs += Document("_id", "bad").append("to_dids", "not-a-list").append("body", 5)
            storage.getMessagesForDid("did:key:a", 10, 0).map { it.id } shouldBe listOf("m1")
            storage.get("bad") shouldBe null
        }
}
