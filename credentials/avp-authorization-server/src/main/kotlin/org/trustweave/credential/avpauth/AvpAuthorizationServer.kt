package org.trustweave.credential.avpauth

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import org.trustweave.credential.avpauth.engine.AuthorizationEngine
import org.trustweave.observability.HostKind
import org.trustweave.observability.HostObservability

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
    private var observability: HostObservability? = null

    /** Configure tracing, protected metrics and optional admission limits before starting. */
    fun withObservability(configuration: HostObservability): AvpAuthorizationServer {
        check(server == null) { "Configure observability before starting the server" }
        observability = configuration
        return this
    }

    fun start(wait: Boolean = false) {
        server =
            embeddedServer(Netty, port = port, host = host) {
                observability?.install(this, HostKind.AVP_AUTHORIZATION)
                configureAuthorization(engine)
            }.start(wait = wait)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }
}
