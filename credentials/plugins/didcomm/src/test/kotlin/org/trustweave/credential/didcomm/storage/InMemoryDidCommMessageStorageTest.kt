package org.trustweave.credential.didcomm.storage

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import org.trustweave.credential.didcomm.models.DidCommAttachment
import org.trustweave.credential.didcomm.models.DidCommAttachmentData
import org.trustweave.credential.didcomm.models.DidCommMessage

class InMemoryDidCommMessageStorageTest {
    private fun msg(
        id: String,
        from: String? = "did:key:a",
        to: List<String> = listOf("did:key:b"),
        thid: String? = null,
        created: String? = "2026-01-01T00:00:00Z",
        type: String = "https://didcomm.org/basicmessage/2.0/message",
    ) = DidCommMessage(id = id, type = type, from = from, to = to, thid = thid, created = created, body = buildJsonObject { })

    @Test
    fun `store and get round-trip, unknown id is null`() =
        runBlocking<Unit> {
            val storage = InMemoryDidCommMessageStorage()
            storage.store(msg("m1")) shouldBe "m1"
            storage.get("m1")?.id shouldBe "m1"
            storage.get("missing") shouldBe null
        }

    @Test
    fun `storing the same id again replaces it without double-counting`() =
        runBlocking<Unit> {
            val storage = InMemoryDidCommMessageStorage()
            storage.store(msg("m1", to = listOf("did:key:b")))
            storage.store(msg("m1", to = listOf("did:key:c")))
            storage.countMessagesForDid("did:key:a") shouldBe 1
            // The replaced message no longer belongs to its old recipient.
            storage.countMessagesForDid("did:key:b") shouldBe 0
            storage.countMessagesForDid("did:key:c") shouldBe 1
        }

    @Test
    fun `a self-addressed message is indexed once`() =
        runBlocking<Unit> {
            val storage = InMemoryDidCommMessageStorage()
            storage.store(msg("m1", from = "did:key:a", to = listOf("did:key:a")))
            storage.countMessagesForDid("did:key:a") shouldBe 1
            storage.getMessagesForDid("did:key:a").size shouldBe 1
        }

    @Test
    fun `deleting a DID's messages removes them from every other index`() =
        runBlocking<Unit> {
            val storage = InMemoryDidCommMessageStorage()
            storage.store(msg("m1", from = "did:key:a", to = listOf("did:key:b"), thid = "t1"))
            storage.store(msg("m2", from = "did:key:c", to = listOf("did:key:b"), thid = "t1"))
            storage.deleteMessagesForDid("did:key:a") shouldBe 1
            // m1 must not linger in b's or the thread's index.
            storage.countMessagesForDid("did:key:b") shouldBe 1
            storage.getThreadMessages("t1").map { it.id } shouldBe listOf("m2")
            storage.get("m1") shouldBe null
        }

    @Test
    fun `deleting a thread removes its messages from the participants' indexes`() =
        runBlocking<Unit> {
            val storage = InMemoryDidCommMessageStorage()
            storage.store(msg("m1", thid = "t1"))
            storage.store(msg("m2", thid = "t2"))
            storage.deleteThreadMessages("t1") shouldBe 1
            storage.countMessagesForDid("did:key:a") shouldBe 1
            storage.deleteThreadMessages("t1") shouldBe 0
        }

    @Test
    fun `delete reports whether anything was removed`() =
        runBlocking<Unit> {
            val storage = InMemoryDidCommMessageStorage()
            storage.store(msg("m1"))
            storage.delete("m1") shouldBe true
            storage.delete("m1") shouldBe false
        }

    @Test
    fun `search filters by sender, type, time window and attachments and pages newest first`() =
        runBlocking<Unit> {
            val storage = InMemoryDidCommMessageStorage()
            storage.store(msg("old", created = "2026-01-01T00:00:00Z"))
            storage.store(msg("new", created = "2026-03-01T00:00:00Z", type = "t/other"))
            storage.store(
                msg("withAtt", from = "did:key:z", created = "2026-02-01T00:00:00Z")
                    .copy(attachments = listOf(DidCommAttachment(data = DidCommAttachmentData(json = buildJsonObject { })))),
            )
            storage.search(MessageFilter()).map { it.id } shouldBe listOf("new", "withAtt", "old")
            storage.search(MessageFilter(fromDid = "did:key:z")).map { it.id } shouldBe listOf("withAtt")
            storage.search(MessageFilter(type = "t/other")).map { it.id } shouldBe listOf("new")
            storage.search(MessageFilter(createdAfter = "2026-02-01T00:00:00Z")).map { it.id } shouldBe listOf("new", "withAtt")
            storage.search(MessageFilter(createdBefore = "2026-01-15T00:00:00Z")).map { it.id } shouldBe listOf("old")
            storage.search(MessageFilter(hasAttachments = true)).map { it.id } shouldBe listOf("withAtt")
            storage.search(MessageFilter(), limit = 1, offset = 1).map { it.id } shouldBe listOf("withAtt")
        }

    @Test
    fun `archiving is tracked per message`() =
        runBlocking<Unit> {
            val storage = InMemoryDidCommMessageStorage()
            storage.store(msg("m1"))
            storage.isArchived("m1") shouldBe false
            storage.markAsArchived(listOf("m1"), "arch-1")
            storage.isArchived("m1") shouldBe true
            storage.isArchived("m2") shouldBe false
        }

    @Test
    fun `at-rest encryption is refused rather than silently ignored`() {
        val storage = InMemoryDidCommMessageStorage()
        storage.setEncryption(null)
        shouldThrow<UnsupportedOperationException> {
            storage.setEncryption(
                org.trustweave.credential.didcomm.storage.encryption
                    .AesMessageEncryption(ByteArray(32)),
            )
        }
    }
}
