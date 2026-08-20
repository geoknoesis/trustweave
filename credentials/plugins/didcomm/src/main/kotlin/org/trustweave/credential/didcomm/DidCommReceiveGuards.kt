package org.trustweave.credential.didcomm

import org.trustweave.credential.didcomm.exception.DidCommException
import org.trustweave.credential.didcomm.models.DidCommMessage

/**
 * Intake checks every [DidCommService] implementation must apply to an unpacked message.
 *
 * Unpacking proves who sent a message and that it was not altered. It says nothing about whether
 * this message should be acted on *now*: a packed DIDComm message is a bearer artifact, so an
 * observer can hand the same bytes back and they decrypt and authenticate exactly as the original
 * did. Without these checks the recipient re-runs whatever the message authorised, and the
 * `expires_time` the sender set never binds.
 *
 * Each service instance owns one of these; construct it alongside the service.
 */
internal class DidCommReceiveGuards(
    private val replayWindowMessages: Int = DEFAULT_REPLAY_WINDOW_MESSAGES,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    /**
     * Ids already accepted, oldest first.
     *
     * Bounded so a peer cannot grow it without limit. The tradeoff is that an id evicted after
     * [replayWindowMessages] further messages would be accepted again, and that the window is
     * per-instance and does not survive a restart. A deployment that needs an unbounded or durable
     * guarantee should keep this state with the message store instead.
     */
    private val seenMessageIds =
        object : LinkedHashMap<String, Boolean>(256, 0.75f, false) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>): Boolean = size > replayWindowMessages
        }

    /** Throws if [message] has expired or has already been accepted by this instance. */
    fun check(message: DidCommMessage) {
        rejectIfExpired(message)
        rejectIfAlreadySeen(message)
    }

    /**
     * Enforces the sender's `expires_time` header.
     *
     * A value that cannot be read as epoch seconds is rejected rather than ignored, so a malformed
     * header cannot quietly turn into no expiry at all.
     */
    private fun rejectIfExpired(message: DidCommMessage) {
        val raw = message.expiresTime ?: return
        val expiresAt =
            raw.trim().toLongOrNull()
                ?: throw DidCommException.ProtocolError(
                    reason = "expires_time is not epoch seconds: $raw",
                    field = "expires_time",
                )
        val now = nowEpochSeconds()
        if (expiresAt <= now) {
            throw DidCommException.ProtocolError(
                reason = "message ${message.id} expired at $expiresAt (now $now)",
                field = "expires_time",
            )
        }
    }

    private fun rejectIfAlreadySeen(message: DidCommMessage) {
        val alreadySeen =
            synchronized(seenMessageIds) {
                seenMessageIds.put(message.id, true) != null
            }
        if (alreadySeen) {
            throw DidCommException.UnpackingFailed(
                reason = "message ${message.id} was already received; refusing to process a replay",
                messageId = message.id,
            )
        }
    }

    internal companion object {
        /** How many recently received message ids an instance remembers. */
        const val DEFAULT_REPLAY_WINDOW_MESSAGES = 10_000
    }
}
