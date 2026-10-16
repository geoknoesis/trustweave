package org.trustweave.credential.didcomm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.credential.didcomm.exception.DidCommException
import org.trustweave.credential.didcomm.models.DidCommMessage

/** `from` is unauthenticated for anoncrypt/unsigned messages, so replay state must not be keyed on it. */
class DidCommUnauthenticatedReplayTest {
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
    fun `an anonymous message claiming a victim's from cannot burn the victim's id`() =
        runBlocking<Unit> {
            val guards = DidCommReceiveGuards(nowEpochSeconds = { 1_000 })
            // attacker replays/pre-registers the id the victim will use, with from = victim, unauthenticated
            guards.check(message("m1", "did:example:alice"), authenticatedSender = null)
            // the victim's real, authenticated message with the same id must still be accepted
            guards.check(message("m1", "did:example:alice"), authenticatedSender = "did:example:alice")
        }

    @Test
    fun `a flood of anonymous messages claiming a victim's from does not fill the victim's quota`() =
        runBlocking<Unit> {
            val store = InMemoryDidCommReplayStore(capacity = 100, maxPerSender = 3)
            val guards = DidCommReceiveGuards(store, nowEpochSeconds = { 1_000 })
            repeat(3) { guards.check(message("spam-$it", "did:example:alice"), null) }
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("spam-3", "did:example:alice"), null) }
            guards.check(message("real", "did:example:alice"), "did:example:alice")
        }

    @Test
    fun `anonymous ids are remembered only briefly while authenticated ones keep full retention`() =
        runBlocking<Unit> {
            var now = 1_000L
            val guards = DidCommReceiveGuards(defaultRetentionSeconds = 86_400, unauthenticatedRetentionSeconds = 60) { now }
            guards.check(message("anon", null), null)
            guards.check(message("auth", "did:example:bob"), "did:example:bob")
            now += 61
            guards.check(message("anon", null), null) // retention lapsed
            shouldThrow<DidCommException.UnpackingFailed> { guards.check(message("auth", "did:example:bob"), "did:example:bob") }
        }

    @Test
    fun `an anonymous id shaped like a scoped key cannot collide with an authenticated key`() =
        runBlocking<Unit> {
            val seen = mutableListOf<String>()
            val legacy =
                object : DidCommReplayStore {
                    override suspend fun recordIfAbsent(
                        messageId: String,
                        retainUntilEpochSeconds: Long,
                        nowEpochSeconds: Long,
                    ) = seen.add(messageId)
                }
            val guards = DidCommReceiveGuards(legacy, nowEpochSeconds = { 1_000 })
            val forged = DidCommReplayStore.scopedKey("did:example:alice", "m1")
            guards.check(message(forged, null), null)
            guards.check(message("m1", "did:example:alice"), "did:example:alice")
            seen.size shouldBe 2
        }
}
