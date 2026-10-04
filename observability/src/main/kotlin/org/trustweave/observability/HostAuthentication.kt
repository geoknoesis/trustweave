package org.trustweave.observability

import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest

/**
 * Decides whether one call may proceed. Return `true` to admit it.
 *
 * Implementations must not throw: an authenticator that fails is treated as a refusal, because a
 * thrown exception on this path would otherwise surface as a 500 and tell the caller more about
 * the server than a 401 does.
 */
public fun interface HostAuthenticator {
    public suspend fun authorize(call: ApplicationCall): Boolean
}

/**
 * Authentication and per-caller rate limiting for the embedded servers this SDK ships.
 *
 * These servers perform key custody operations — the DID registrar creates, updates and
 * deactivates DIDs — and previously carried no authentication primitive at all, only a loopback
 * bind default and documentation asking the operator to front them with a proxy. Documentation is
 * not a control, and an operator who binds to `0.0.0.0` without reading it gets an open registrar.
 *
 * So a server refuses mutating requests until the host has said what protects them. Saying so is
 * cheap and explicit: configure [bearerToken] or [custom], or state the deployment's actual
 * control with [frontedByProxy]. Silence is the one thing that is not accepted.
 *
 * This is authentication, not encryption. Use TLS, or a loopback-only bind behind a TLS proxy;
 * a bearer token on a plaintext connection is readable by anything on the path.
 */
public class HostAuthentication private constructor(
    private val authenticator: HostAuthenticator?,
    private val protectedMethods: Set<HttpMethod>,
    private val rateLimit: RateLimit?,
    private val exemptPaths: Set<String>,
    /**
     * Path prefixes that are gated for every method, reads included, even when [protectedMethods]
     * covers only mutations. See [protectingPathPrefixes].
     */
    private val protectedPathPrefixes: Set<String> = emptySet(),
    /**
     * What protects this server, when nothing in this process does.
     *
     * Non-null only for [frontedByProxy]. Readable so a host can log or surface the declaration
     * at startup — a claim nobody can inspect is not much better than no claim.
     */
    public val delegatedTo: String? = null,
) {
    public companion object {
        /** Methods that change state. Reads are not gated by default. */
        public val MUTATING: Set<HttpMethod> =
            setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete)

        /** Every method, for a server whose reads are also privileged. */
        public val ALL: Set<HttpMethod> =
            MUTATING + setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)

        private const val METRICS_PATH = "/internal/metrics"

        /**
         * Constant-time bearer token comparison.
         *
         * @param token 32-256 printable non-space ASCII characters, matching the metrics token
         *   rule in [HostObservability]. Short tokens are refused rather than accepted weakly.
         */
        @JvmStatic
        @JvmOverloads
        public fun bearerToken(
            token: String,
            protect: Set<HttpMethod> = MUTATING,
            rateLimit: RateLimit? = null,
        ): HostAuthentication {
            require(token.length in 32..256 && token.all { it.code in 33..126 }) {
                "Bearer token must contain 32-256 printable non-space ASCII characters"
            }
            val expected = "Bearer $token".toByteArray(Charsets.UTF_8)
            return HostAuthentication(
                authenticator = { call ->
                    val actual =
                        call.request.headers["Authorization"]
                            ?.takeIf { it.length <= 263 }
                            ?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                    MessageDigest.isEqual(expected, actual)
                },
                protectedMethods = protect,
                rateLimit = rateLimit,
                exemptPaths = setOf(METRICS_PATH),
            )
        }

        /** Any host-supplied check: mTLS subject, JWT, an allow-list, a delegated authorizer. */
        @JvmStatic
        @JvmOverloads
        public fun custom(
            authenticator: HostAuthenticator,
            protect: Set<HttpMethod> = MUTATING,
            rateLimit: RateLimit? = null,
        ): HostAuthentication = HostAuthentication(authenticator, protect, rateLimit, setOf(METRICS_PATH))

        /**
         * Declares that something in front of this server already authenticates callers.
         *
         * This admits every request. It exists so that "a proxy handles it" is a recorded decision
         * in the host's own code rather than the accidental result of configuring nothing.
         *
         * @param reason what actually authenticates callers, for the operator reading this later.
         */
        @JvmStatic
        @JvmOverloads
        public fun frontedByProxy(
            reason: String,
            rateLimit: RateLimit? = null,
        ): HostAuthentication {
            require(reason.isNotBlank()) { "State what authenticates callers in front of this server" }
            return HostAuthentication(null, emptySet(), rateLimit, setOf(METRICS_PATH), delegatedTo = reason.trim())
        }
    }

    /**
     * Returns a copy that also requires the credential for every request, whatever its method,
     * whose path starts with one of [prefixes]. Use it when a few read routes are privileged but
     * the rest of the server's reads are not, e.g. job-status records in the DID registrar.
     *
     * Has no effect on [frontedByProxy], which has no authenticator of its own.
     */
    public fun protectingPathPrefixes(vararg prefixes: String): HostAuthentication =
        HostAuthentication(
            authenticator = authenticator,
            protectedMethods = protectedMethods,
            rateLimit = rateLimit,
            exemptPaths = exemptPaths,
            protectedPathPrefixes = protectedPathPrefixes + prefixes,
            delegatedTo = delegatedTo,
        )

    /**
     * Fixed-window permit budgets per caller, keyed by remote host.
     *
     * Three budgets are kept, so that unauthenticated traffic cannot spend what a legitimate
     * caller sharing the same host (a NAT, a proxy that does not forward the client address) needs:
     *
     *  1. **Attempts** ([attemptPermits], generous): every request, counted *before* authentication.
     *     It exists only to bound the work an anonymous flood can cause (the authenticator runs
     *     for each request that gets past it). It is deliberately far above [permits].
     *  2. **Failed authentications** ([failedAuthPermits], strict): charged only when the credential
     *     is refused. When it is exhausted, a refused credential is answered 429 instead of 401. A
     *     caller presenting a valid credential is never affected by this budget.
     *  3. **Admitted requests** ([permits]): charged only to requests that passed authentication
     *     (or needed none). A flood of bad credentials therefore cannot use up the budget of the
     *     legitimate caller behind the same host.
     *
     * Each budget tracks at most [maxTrackedCallers] callers. When the table is full, expired
     * windows are dropped first; if every tracked caller is still inside its window, callers that
     * are not tracked share a single overflow window per budget. Tracked callers keep their
     * budgets, so spraying distinct caller keys cannot reset anyone's spend; the cost of that is
     * that, while such a spray lasts, untracked callers compete for the overflow window.
     *
     * This bounds one caller; [HostTelemetry]'s admission limit bounds the server as a whole.
     * Behind a proxy every request shares one remote host, so configure the limit at the proxy
     * instead of here unless the proxy preserves the client address.
     */
    public class RateLimit
        @JvmOverloads
        constructor(
            private val permits: Int,
            private val windowMillis: Long = 60_000,
            private val maxTrackedCallers: Int = 10_000,
            private val clock: () -> Long = System::currentTimeMillis,
            private val failedAuthPermits: Int = defaultFailedAuthPermits(permits),
            private val attemptPermits: Int = defaultAttemptPermits(permits),
        ) {
            init {
                require(permits > 0) { "Rate limit permits must be positive" }
                require(windowMillis > 0) { "Rate limit window must be positive" }
                require(maxTrackedCallers > 0) { "Tracked callers must be positive" }
                require(failedAuthPermits > 0) { "Failed-authentication permits must be positive" }
                require(attemptPermits > 0) { "Attempt permits must be positive" }
            }

            private val admitted = Budget(permits)
            private val failed = Budget(failedAuthPermits)
            private val attempts = Budget(attemptPermits)

            /** One fixed window for one caller. Guarded by its [Budget]'s lock. */
            private class Window(
                var startedAt: Long,
                var used: Int = 0,
            )

            private inner class Budget(
                private val limit: Int,
            ) {
                private val windows = HashMap<String, Window>()
                private var overflow: Window? = null
                private var lastPurge = Long.MIN_VALUE

                @Synchronized
                fun take(
                    caller: String,
                    now: Long,
                ): Boolean {
                    var window = windows[caller]
                    if (window == null) {
                        if (windows.size >= maxTrackedCallers) purgeExpired(now)
                        window =
                            if (windows.size >= maxTrackedCallers) {
                                overflow ?: Window(now).also { overflow = it }
                            } else {
                                Window(now).also { windows[caller] = it }
                            }
                    }
                    if (now - window.startedAt >= windowMillis) {
                        window.startedAt = now
                        window.used = 0
                    }
                    if (window.used >= limit) return false
                    window.used++
                    return true
                }

                /** Drops expired windows, at most a few times per window so a spray cannot make it O(n) per call. */
                private fun purgeExpired(now: Long) {
                    if (lastPurge != Long.MIN_VALUE && now - lastPurge < maxOf(1L, windowMillis / 10)) return
                    lastPurge = now
                    windows.values.removeIf { now - it.startedAt >= windowMillis }
                }
            }

            /** True when this caller still has budget for an admitted request in the current window. */
            internal fun admit(caller: String): Boolean = admitted.take(caller, clock())

            /** Pre-authentication, generous bound on the work one caller can cause. */
            internal fun admitAttempt(caller: String): Boolean = attempts.take(caller, clock())

            /** Charged when a credential is refused; false once the caller is out of failed-auth budget. */
            internal fun admitFailedAuthentication(caller: String): Boolean = failed.take(caller, clock())

            internal fun retryAfterSeconds(): Long = maxOf(1, windowMillis / 1000)

            private companion object {
                /** A quarter of the admitted budget (at least one): stricter than it, by design. */
                fun defaultFailedAuthPermits(permits: Int): Int = maxOf(1, permits / 4)

                /** Ten times the admitted budget, saturating. Generous: it only bounds anonymous work. */
                fun defaultAttemptPermits(permits: Int): Int = if (permits > Int.MAX_VALUE / 10) Int.MAX_VALUE else maxOf(1, permits * 10)
            }
        }

    /**
     * Installs the gate ahead of routing. Call before the server starts.
     *
     * @param protocolAuthenticatedPaths routes the server itself authenticates through the
     *   protocol it implements — an OAuth token endpoint that takes a pre-authorized code, a
     *   credential endpoint that takes an access token. Those callers are wallets, which cannot
     *   hold a host credential, so a host gate in front of them would refuse the protocol rather
     *   than protect it. This is the server's declaration about its own routes, not a host
     *   setting: a host that wants to narrow what it exposes does that in front of the server.
     */
    @JvmOverloads
    public fun install(
        application: Application,
        protocolAuthenticatedPaths: Set<String> = emptySet(),
    ) {
        application.intercept(ApplicationCallPipeline.Plugins) {
            val path = call.request.path()
            if (path in exemptPaths || path in protocolAuthenticatedPaths) {
                proceed()
                return@intercept
            }
            val limit = rateLimit
            val caller = call.request.origin.remoteHost
            // Generous, pre-authentication bound on anonymous work. Not the caller's real budget.
            if (limit != null && !limit.admitAttempt(caller)) {
                call.tooManyRequests(limit)
                finish()
                return@intercept
            }
            val gate = authenticator
            if (gate != null &&
                (call.request.httpMethod in protectedMethods || underProtectedPrefix(path))
            ) {
                val admitted =
                    try {
                        gate.authorize(call)
                    } catch (cancelled: CancellationException) {
                        // Cancellation is not a verdict on the caller; never swallow it.
                        throw cancelled
                    } catch (refused: Exception) {
                        // An authenticator that throws has not authorized anything.
                        false
                    }
                if (!admitted) {
                    // A refused credential is charged to its own, stricter budget so that failures
                    // can never spend the budget of an authenticated caller sharing this host.
                    if (limit != null && !limit.admitFailedAuthentication(caller)) {
                        call.tooManyRequests(limit)
                    } else {
                        call.response.headers.append("WWW-Authenticate", "Bearer")
                        call.refuse(HttpStatusCode.Unauthorized, "unauthorized", "Caller is not authorized for this operation")
                    }
                    finish()
                    return@intercept
                }
            }
            // Only requests that passed authentication (or needed none) spend the caller's budget.
            if (limit != null && !limit.admit(caller)) {
                call.tooManyRequests(limit)
                finish()
                return@intercept
            }
            proceed()
        }
    }

    /**
     * Whether [rawPath] falls under a protected prefix once it is normalised the way a router
     * would: percent-escapes decoded, empty and `.` segments dropped, `..` resolved. Compared
     * case-insensitively, which can only gate more, never less. A raw match also counts.
     */
    private fun underProtectedPrefix(rawPath: String): Boolean {
        if (protectedPathPrefixes.isEmpty()) return false
        val normalised = normalisePath(rawPath)
        return protectedPathPrefixes.any { prefix ->
            rawPath.startsWith(prefix) ||
                normalised.startsWith(normalisePath(prefix).trimEnd('/') + "/", ignoreCase = true) ||
                normalised.equals(normalisePath(prefix), ignoreCase = true)
        }
    }

    private fun normalisePath(path: String): String {
        val segments = ArrayDeque<String>()
        for (segment in path.split('/')) {
            val decoded =
                try {
                    java.net.URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8)
                } catch (_: IllegalArgumentException) {
                    segment
                }
            // A decoded slash would have split the path in a router that decodes first.
            for (part in decoded.split('/', '\\')) {
                when (part) {
                    "", "." -> Unit
                    ".." -> segments.removeLastOrNull()
                    else -> segments.addLast(part)
                }
            }
        }
        return "/" + segments.joinToString("/")
    }

    private suspend fun ApplicationCall.tooManyRequests(limit: RateLimit) {
        response.headers.append("Retry-After", limit.retryAfterSeconds().toString())
        refuse(HttpStatusCode.TooManyRequests, "rate_limited", "Too many requests from this caller")
    }

    private suspend fun ApplicationCall.refuse(
        status: HttpStatusCode,
        code: String,
        message: String,
    ) {
        response.headers.append("Cache-Control", "no-store")
        respondText(
            """{"error":"$code","message":"$message"}""",
            ContentType.Application.Json,
            status,
        )
    }

    /**
     * Installs the fail-closed default for a server the host never configured.
     *
     * Mutating requests are refused with 503 and an actionable message. Reads still work, so a
     * resolver or status-list endpoint keeps serving while the operator decides what to do.
     */
    public class Unconfigured
        @JvmOverloads
        constructor(
            private val serverName: String,
            /** See the [protocolAuthenticatedPaths] parameter of [HostAuthentication.install]. */
            private val protocolAuthenticatedPaths: Set<String> = emptySet(),
        ) {
            public fun install(application: Application) {
                application.intercept(ApplicationCallPipeline.Plugins) {
                    if (call.request.httpMethod !in MUTATING || call.request.path() in protocolAuthenticatedPaths) {
                        proceed()
                        return@intercept
                    }
                    call.response.headers.append("Cache-Control", "no-store")
                    call.respondText(
                        """{"error":"authentication_not_configured","message":""" +
                            """"$serverName refuses mutating requests until the host calls """ +
                            """withAuthentication(...). Use HostAuthentication.bearerToken or custom to """ +
                            """authenticate callers, or frontedByProxy to record what already does."}""",
                        ContentType.Application.Json,
                        HttpStatusCode.ServiceUnavailable,
                    )
                    finish()
                }
            }
        }
}
