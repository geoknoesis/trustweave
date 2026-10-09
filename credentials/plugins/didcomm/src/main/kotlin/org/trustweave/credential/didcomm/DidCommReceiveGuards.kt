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
    private val replayStore: DidCommReplayStore = InMemoryDidCommReplayStore(),
    private val defaultRetentionSeconds: Long = DEFAULT_RETENTION_SECONDS,
    private val maxRetentionSeconds: Long = MAX_RETENTION_SECONDS,
    private val unauthenticatedRetentionSeconds: Long = UNAUTHENTICATED_RETENTION_SECONDS,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    init {
        require(defaultRetentionSeconds > 0 && maxRetentionSeconds >= defaultRetentionSeconds) {
            "retention must be positive and maxRetentionSeconds >= defaultRetentionSeconds"
        }
    }

    /**
     * Throws if [message] has expired or has already been accepted.
     *
     * [authenticatedSender] is the sender DID the unpacking step actually authenticated (authcrypt
     * sender or verified signer), NOT the message's own `from` header, which is attacker-controlled
     * for anoncrypt / unsigned messages. Replay ids are scoped by it. A message with no authenticated
     * sender shares one bounded bucket ([UNAUTHENTICATED_SENDER]) and is remembered only for
     * [unauthenticatedRetentionSeconds], so an anonymous party can neither pre-register another
     * sender's message ids nor fill that sender's quota by claiming its DID in `from`.
     */
    suspend fun check(
        message: DidCommMessage,
        authenticatedSender: String? = null,
    ) {
        val expiresAt = rejectIfExpired(message)
        rejectIfAlreadySeen(message, expiresAt, authenticatedSender)
    }

    /**
     * Enforces the sender's `expires_time` header.
     *
     * A value that cannot be read as epoch seconds is rejected rather than ignored, so a malformed
     * header cannot quietly turn into no expiry at all.
     */
    private fun rejectIfExpired(message: DidCommMessage): Long? {
        val raw = message.expiresTime ?: return null
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
        return expiresAt
    }

    /**
     * Records the id in the [replayStore]. It is remembered until the message's own
     * `expires_time` (after which [rejectIfExpired] refuses it anyway) or, without one, for
     * [defaultRetentionSeconds]; never longer than [maxRetentionSeconds]. A message without
     * `expires_time` replayed after its retention has lapsed is not detected — senders that need
     * a hard guarantee should set `expires_time`.
     */
    private suspend fun rejectIfAlreadySeen(
        message: DidCommMessage,
        expiresAt: Long?,
        authenticatedSender: String?,
    ) {
        val now = nowEpochSeconds()
        val retainUntil =
            minOf(expiresAt ?: (now + defaultRetentionSeconds), now + maxRetentionSeconds).let {
                if (authenticatedSender == null) minOf(it, now + unauthenticatedRetentionSeconds) else it
            }
        val recorded =
            try {
                replayStore.recordIfAbsent(authenticatedSender ?: UNAUTHENTICATED_SENDER, message.id, retainUntil, now)
            } catch (e: DidCommReplayStoreFullException) {
                throw DidCommException.UnpackingFailed(
                    reason = "cannot record message ${message.id} for replay protection: ${e.message}",
                    messageId = message.id,
                    cause = e,
                )
            }
        if (!recorded) {
            throw DidCommException.UnpackingFailed(
                reason = "message ${message.id} was already received; refusing to process a replay",
                messageId = message.id,
            )
        }
    }

    internal companion object {
        /** Retention for ids of messages without `expires_time`. */
        const val DEFAULT_RETENTION_SECONDS = 24L * 60 * 60

        /** Retention for ids of messages with no authenticated sender (anonymous bucket). */
        const val UNAUTHENTICATED_RETENTION_SECONDS = 10L * 60

        /** Replay-store scope shared by all messages without an authenticated sender; not a DID, so it cannot collide with one. */
        const val UNAUTHENTICATED_SENDER = "unauthenticated"

        /** Upper bound on how long any id is remembered. */
        const val MAX_RETENTION_SECONDS = 30L * 24 * 60 * 60
    }
}
