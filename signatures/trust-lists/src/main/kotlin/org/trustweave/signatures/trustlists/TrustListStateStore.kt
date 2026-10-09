package org.trustweave.signatures.trustlists

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * What was last accepted for one trust list.
 *
 * @property sequence       The list's `TSLSequenceNumber`.
 * @property documentSha256 Lower-case hex SHA-256 of the exact bytes that were accepted.
 */
data class TrustListState(
    val sequence: Int,
    val documentSha256: String,
) {
    companion object {
        /** The [documentSha256] of [document]. */
        fun hashOf(document: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(document).joinToString("") { "%02x".format(it) }
    }
}

/**
 * Remembers, per trust list, the highest sequence number accepted and the document accepted at it, so that
 * [VerifiedTrustListLoader] can refuse a rollback without the caller having to thread the values through by hand.
 *
 * Keys are `LOTL` for the List of Trusted Lists and `TSL:<TERRITORY>` for a member-state
 * list. Implementations that must survive a restart persist the entries (a file, a database row); the loader only
 * calls [get] before it accepts a list and [put] after a whole load succeeded.
 */
interface TrustListStateStore {
    fun get(key: String): TrustListState?

    fun put(
        key: String,
        state: TrustListState,
    )
}

/** Process-local, thread-safe [TrustListStateStore]; it forgets everything on restart. */
class InMemoryTrustListStateStore : TrustListStateStore {
    private val entries = ConcurrentHashMap<String, TrustListState>()

    override fun get(key: String): TrustListState? = entries[key]

    override fun put(
        key: String,
        state: TrustListState,
    ) {
        entries.merge(key, state) { old, new -> if (new.sequence >= old.sequence) new else old }
    }
}
