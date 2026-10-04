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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
     * A fixed-window permit budget per caller, keyed by remote host.
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
        ) {
            init {
                require(permits > 0) { "Rate limit permits must be positive" }
                require(windowMillis > 0) { "Rate limit window must be positive" }
                require(maxTrackedCallers > 0) { "Tracked callers must be positive" }
            }

            private val windows = ConcurrentHashMap<String, Window>()
            private val lastSweep = AtomicLong(0)

            private class Window(
                @Volatile var startedAt: Long,
                val used: AtomicLong,
            )

            /** True when this caller still has budget in the current window. */
            internal fun admit(caller: String): Boolean {
                val now = clock()
                sweep(now)
                val window = windows.computeIfAbsent(caller) { Window(now, AtomicLong(0)) }
                synchronized(window) {
                    if (now - window.startedAt >= windowMillis) {
                        window.startedAt = now
                        window.used.set(0)
                    }
                    if (window.used.get() >= permits) return false
                    window.used.incrementAndGet()
                    return true
                }
            }

            /** Drops expired entries so a spray of distinct callers cannot grow this map forever. */
            private fun sweep(now: Long) {
                if (windows.size < maxTrackedCallers) return
                val previous = lastSweep.get()
                if (now - previous < windowMillis || !lastSweep.compareAndSet(previous, now)) return
                windows.entries.removeIf { now - it.value.startedAt >= windowMillis }
                if (windows.size >= maxTrackedCallers) windows.clear()
            }

            internal fun retryAfterSeconds(): Long = maxOf(1, windowMillis / 1000)
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
            if (limit != null && !limit.admit(call.request.origin.remoteHost)) {
                call.response.headers.append("Retry-After", limit.retryAfterSeconds().toString())
                call.refuse(HttpStatusCode.TooManyRequests, "rate_limited", "Too many requests from this caller")
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
                    } catch (refused: Exception) {
                        // An authenticator that throws has not authorized anything.
                        false
                    }
                if (!admitted) {
                    call.response.headers.append("WWW-Authenticate", "Bearer")
                    call.refuse(HttpStatusCode.Unauthorized, "unauthorized", "Caller is not authorized for this operation")
                    finish()
                    return@intercept
                }
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
