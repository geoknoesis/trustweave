package org.trustweave.credential.oidc4vci.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import io.ktor.server.response.respondText

/**
 * Per-caller request budget for the OID4VCI protocol endpoints.
 *
 * `/token`, `/credential`, `/deferred_credential` and `/notification` are exempt from the host
 * authentication gate (and so from its rate limit), because their callers are wallets that hold no host
 * credential. They authenticate with a pre-authorized code or an access token instead, and this limit
 * bounds how often one caller may try, so a guessed or stolen token cannot be spent in a tight loop and
 * the signing backend behind `/credential` cannot be driven at will.
 *
 * Each (caller, endpoint) pair gets [permits] requests per fixed window of [windowMillis]. At most
 * [maxTrackedCallers] pairs are tracked; when the table is full, expired windows are dropped and any
 * remaining untracked pairs share one overflow window per endpoint, so a spray of distinct callers cannot
 * reset anyone's spend. Behind a proxy that does not forward the client address every request shares one
 * caller: apply the limit at the proxy and pass `null` to [Oidc4VciServer.withProtocolRateLimit].
 */
class Oidc4VciProtocolRateLimit
    @JvmOverloads
    constructor(
        private val permits: Int = 120,
        private val windowMillis: Long = 60_000,
        private val maxTrackedCallers: Int = 10_000,
        private val clock: () -> Long = System::currentTimeMillis,
    ) {
        init {
            require(permits > 0) { "permits must be positive" }
            require(windowMillis > 0) { "windowMillis must be positive" }
            require(maxTrackedCallers > 0) { "maxTrackedCallers must be positive" }
        }

        private class Window(
            var startedAt: Long,
            var used: Int = 0,
        )

        private val windows = HashMap<String, Window>()
        private val overflow = HashMap<String, Window>()

        /** True when [caller] still has budget on [path] in the current window. */
        @Synchronized
        internal fun admit(
            caller: String,
            path: String,
        ): Boolean {
            val now = clock()
            val key = "$path\n$caller"
            var window = windows[key]
            if (window == null) {
                if (windows.size >= maxTrackedCallers) windows.values.removeIf { now - it.startedAt >= windowMillis }
                window =
                    if (windows.size >= maxTrackedCallers) {
                        overflow.getOrPut(path) { Window(now) }
                    } else {
                        Window(now).also { windows[key] = it }
                    }
            }
            if (now - window.startedAt >= windowMillis) {
                window.startedAt = now
                window.used = 0
            }
            if (window.used >= permits) return false
            window.used++
            return true
        }

        internal fun install(
            application: Application,
            paths: Set<String>,
        ) {
            application.intercept(ApplicationCallPipeline.Plugins) {
                val path = call.request.path()
                if (path in paths && !admit(call.request.origin.remoteHost, path)) {
                    call.response.headers.append("Retry-After", maxOf(1L, windowMillis / 1000).toString())
                    call.response.headers.append("Cache-Control", "no-store")
                    call.respondText(
                        """{"error":"rate_limited"}""",
                        ContentType.Application.Json,
                        HttpStatusCode.TooManyRequests,
                    )
                    finish()
                    return@intercept
                }
                proceed()
            }
        }
    }
