package org.trustweave.credential.avpauth

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import org.trustweave.credential.avpauth.engine.AuthorizationEngine

/**
 * Standalone server. `configureAuthorization` is the reusable wiring (used by tests too).
 *
 * @param host Bind address. Defaults to loopback: exposing an embedded server to the network is an
 *   explicit decision, not something that happens because a default was left alone. Pass "0.0.0.0"
 *   once something in front of it authenticates callers.
 */
class AvpAuthorizationServer(
    private val engine: AuthorizationEngine = AuthorizationEngine(),
    private val port: Int = 8080,
    private val host: String = "127.0.0.1",
) {
    private var server: NettyApplicationEngine? = null

    fun start(wait: Boolean = false) {
        server =
            embeddedServer(Netty, port = port, host = host) {
                configureAuthorization(engine)
            }.start(wait = wait)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}
