package org.trustweave.credential.oidc4vci.server

import java.util.concurrent.ConcurrentHashMap

/**
 * Where an [Oidc4VciIssuerService] keeps its short-lived state: unredeemed offers, access tokens
 * and deferred credentials.
 *
 * The default is [InMemoryOidc4VciIssuerStateStore]; implement this interface (for example over a
 * database) to let several issuer instances share state or to survive a restart. Every implementation
 * must pass `Oidc4VciIssuerStateStoreContract`, published in this module's test fixtures.
 *
 * Contract highlights:
 * - **Atomic consume.** [consumeOffer] and [consumeDeferred] remove and return in one step; of any
 *   number of concurrent callers for one key exactly one receives the value. This is what makes a
 *   pre-authorized code single-use.
 * - **Atomic bounded insert.** The `put*` methods refuse (return `false`) rather than exceed
 *   `maxEntries`, and never evict a live entry; the check and the insert are one step.
 * - **Atomic token update.** [updateToken] is a read-modify-write on one token; it is how `c_nonce`
 *   values are validated and rotated exactly once.
 * - **Expiry is the caller's policy.** Entries carry their `issuedAt`; the service decides what is
 *   expired and asks the store to drop it with [purgeExpired]. A store never invents its own TTL.
 * - Implementations must be safe for concurrent use. Failures must throw, never be reported as an
 *   absent entry or a `false` capacity refusal.
 */
interface Oidc4VciIssuerStateStore {
    /** Stores [offer] under [code]; `false` (nothing stored) when [maxEntries] offers are already held. */
    fun putOffer(
        code: String,
        offer: OfferState,
        maxEntries: Int,
    ): Boolean

    /** Atomically removes and returns the offer under [code], or `null` if there is none. */
    fun consumeOffer(code: String): OfferState?

    /** Stores [entry] under [accessToken]; `false` (nothing stored) when [maxEntries] tokens are already held. */
    fun putToken(
        accessToken: String,
        entry: TokenEntry,
        maxEntries: Int,
    ): Boolean

    /** The token entry, or `null` if unknown. */
    fun getToken(accessToken: String): TokenEntry?

    /** Removes the token (no-op if absent). */
    fun removeToken(accessToken: String)

    /**
     * Atomically applies [update] to the entry of [accessToken]: [update] receives the current entry and
     * returns the entry to store together with a result. Returns that result, or `null` when the token
     * is unknown ([update] is then not called). Implementations over a remote store may invoke [update]
     * more than once on contention, so it must be free of side effects.
     */
    fun <R : Any> updateToken(
        accessToken: String,
        update: (TokenEntry) -> Pair<TokenEntry, R>,
    ): R?

    /** Stores a deferred credential; `false` (nothing stored) when [maxEntries] are already held. */
    fun putDeferred(
        transactionId: String,
        entry: DeferredEntry,
        maxEntries: Int,
    ): Boolean

    /** Atomically removes and returns the deferred credential, or `null` if there is none. */
    fun consumeDeferred(transactionId: String): DeferredEntry?

    /**
     * Drops every offer, token and deferred credential whose `issuedAt` is at or before the matching
     * cutoff (epoch milliseconds) and returns how many entries were dropped.
     */
    fun purgeExpired(
        offersIssuedAtOrBefore: Long,
        tokensIssuedAtOrBefore: Long,
        deferredIssuedAtOrBefore: Long,
    ): Int

    /** Number of retained offers. */
    fun offerCount(): Int

    /** Number of retained access tokens. */
    fun tokenCount(): Int

    /** Number of retained deferred credentials. */
    fun deferredCount(): Int
}

/** Default, process-local [Oidc4VciIssuerStateStore]. */
class InMemoryOidc4VciIssuerStateStore : Oidc4VciIssuerStateStore {
    private val offers = ConcurrentHashMap<String, OfferState>()
    private val tokens = ConcurrentHashMap<String, TokenEntry>()
    private val deferred = ConcurrentHashMap<String, DeferredEntry>()

    private fun <V> ConcurrentHashMap<String, V>.putBounded(
        key: String,
        value: V,
        maxEntries: Int,
    ): Boolean =
        synchronized(this) {
            if (size >= maxEntries && !containsKey(key)) return false
            this[key] = value
            true
        }

    override fun putOffer(
        code: String,
        offer: OfferState,
        maxEntries: Int,
    ): Boolean = offers.putBounded(code, offer, maxEntries)

    override fun consumeOffer(code: String): OfferState? = offers.remove(code)

    override fun putToken(
        accessToken: String,
        entry: TokenEntry,
        maxEntries: Int,
    ): Boolean = tokens.putBounded(accessToken, entry, maxEntries)

    override fun getToken(accessToken: String): TokenEntry? = tokens[accessToken]

    override fun removeToken(accessToken: String) {
        tokens.remove(accessToken)
    }

    override fun <R : Any> updateToken(
        accessToken: String,
        update: (TokenEntry) -> Pair<TokenEntry, R>,
    ): R? {
        var result: R? = null
        tokens.computeIfPresent(accessToken) { _, entry ->
            val (next, r) = update(entry)
            result = r
            next
        }
        return result
    }

    override fun putDeferred(
        transactionId: String,
        entry: DeferredEntry,
        maxEntries: Int,
    ): Boolean = deferred.putBounded(transactionId, entry, maxEntries)

    override fun consumeDeferred(transactionId: String): DeferredEntry? = deferred.remove(transactionId)

    override fun purgeExpired(
        offersIssuedAtOrBefore: Long,
        tokensIssuedAtOrBefore: Long,
        deferredIssuedAtOrBefore: Long,
    ): Int {
        val before = offers.size + tokens.size + deferred.size
        offers.values.removeIf { it.issuedAt <= offersIssuedAtOrBefore }
        tokens.values.removeIf { it.issuedAt <= tokensIssuedAtOrBefore }
        deferred.values.removeIf { it.issuedAt <= deferredIssuedAtOrBefore }
        return before - (offers.size + tokens.size + deferred.size)
    }

    override fun offerCount(): Int = offers.size

    override fun tokenCount(): Int = tokens.size

    override fun deferredCount(): Int = deferred.size
}
