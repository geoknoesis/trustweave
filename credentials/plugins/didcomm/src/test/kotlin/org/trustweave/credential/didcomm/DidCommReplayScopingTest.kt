package org.trustweave.credential.didcomm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.credential.didcomm.exception.DidCommException
import org.trustweave.credential.didcomm.models.DidCommMessage

/** Replay ids are chosen by the sender, so they are scoped to it and bounded per sender. */
class DidCommReplayScopingTest {
    private fun message(
        id: String,
        from: String?,
    ) = DidCommMessage(
        id = id,
        type = "https://didcomm.org/basicmessage/2.0/message",
        from = from,
        body = buildJsonObject { put("content", "hi") },
    )

    @Test
    fun `the same id from two senders is two messages`() =
        runBlocking<Unit> {
            val guards = DidCommReceiveGuards(nowEpochSeconds = { 1_000 })
            guards.check(message("m1", "did:example:alice"), "did:example:alice")
            // Before: "m1" was remembered globally, so Bob's first message looked like a replay.
            guards.check(message("m1", "did:example:bob"), "did:example:bob")
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("m1", "did:example:alice"), "did:example:alice") }
        }

    @Test
    fun `one sender cannot fill the store`() =
        runBlocking<Unit> {
            val store = InMemoryDidCommReplayStore(capacity = 10, maxPerSender = 3)
            val guards = DidCommReceiveGuards(store, nowEpochSeconds = { 1_000 })
            repeat(3) { guards.check(message("flood-$it", "did:example:mallory"), "did:example:mallory") }
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("flood-3", "did:example:mallory"), "did:example:mallory") }

            // Everyone else still has room, and Mallory's retained ids still detect replays.
            guards.check(message("hello", "did:example:alice"), "did:example:alice")
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("flood-0", "did:example:mallory"), "did:example:mallory") }
            store.size() shouldBe 4
        }

    @Test
    fun `a sender's slots are released as its ids expire`() =
        runBlocking<Unit> {
            var now = 1_000L
            val store = InMemoryDidCommReplayStore(capacity = 10, maxPerSender = 1)
            val guards = DidCommReceiveGuards(store, defaultRetentionSeconds = 100, maxRetentionSeconds = 100) { now }
            guards.check(message("a", "did:example:alice"), "did:example:alice")
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("b", "did:example:alice"), "did:example:alice") }
            now += 101
            guards.check(message("b", "did:example:alice"), "did:example:alice")
        }

    @Test
    fun `an existing store keeps working and is scoped by sender through the default method`() =
        runBlocking<Unit> {
            val seen = mutableSetOf<String>()
            val legacy =
                object : DidCommReplayStore {
                    override suspend fun recordIfAbsent(
                        messageId: String,
                        retainUntilEpochSeconds: Long,
                        nowEpochSeconds: Long,
                    ) = seen.add(messageId)
                }
            val guards = DidCommReceiveGuards(legacy)
            guards.check(message("m", "did:example:alice"), "did:example:alice")
            guards.check(message("m", "did:example:bob"), "did:example:bob")
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("m", "did:example:alice"), "did:example:alice") }
        }

    @Test
    fun `scoped keys cannot be forged by moving a separator`() {
        DidCommReplayStore.scopedKey("a", "b:c") shouldBe "1:a:b:c"
        (DidCommReplayStore.scopedKey("a:b", "c") == DidCommReplayStore.scopedKey("a", "b:c")) shouldBe false
        DidCommReplayStore.scopedKey(null, "m1") shouldBe "m1"
    }
}
