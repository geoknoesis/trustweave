package org.trustweave.kms.cyberark

/** An access token together with the lifetime the issuer granted it (null when unknown). */
internal class IssuedToken(
    val value: String,
    val lifetimeMillis: Long?,
)

/**
 * Caches one access token until shortly before it expires, so a token is fetched once per lifetime
 * rather than once per request.
 *
 * The cached copy is dropped a safety margin (a quarter of the lifetime, at least a minute) before
 * the issuer's expiry so a token is never presented at the edge of its validity. [invalidate]
 * forces a refetch, for use after the server rejects a token.
 */
internal class TokenCache(
    private val defaultLifetimeMillis: Long,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val fetch: () -> IssuedToken,
) {
    private val lock = Any()
    private var token: String? = null
    private var validUntil = 0L

    fun get(): String =
        synchronized(lock) {
            val cached = token
            if (cached != null && nowMillis() < validUntil) return cached
            val issued = fetch()
            val lifetime = issued.lifetimeMillis?.takeIf { it > 0 } ?: defaultLifetimeMillis
            val margin = maxOf(MIN_MARGIN_MILLIS, lifetime / 4)
            // For a very short lifetime the margin must not swallow all of it.
            val usable = if (lifetime > margin) lifetime - margin else lifetime / 2
            token = issued.value
            validUntil = nowMillis() + usable
            issued.value
        }

    fun invalidate() {
        synchronized(lock) {
            token = null
            validUntil = 0L
        }
    }

    private companion object {
        const val MIN_MARGIN_MILLIS = 60_000L
    }
}
