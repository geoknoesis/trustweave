package org.trustweave.credential.avpauth

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import org.trustweave.credential.avpauth.engine.AuthorizationEngine
import org.trustweave.observability.HostAuthentication
import org.trustweave.observability.HostKind
import org.trustweave.observability.HostObservability

/**
 * Standalone server. `configureAuthorization` is the reusable wiring (used by tests too).
 *
 * ## Security
 *
 * **Configure [withAuthentication] before starting this server.** Until you do, every POST is
 * refused with 503.
 *
 * This server decides whether a payment is authorized. Anyone who can reach an unprotected port
 * can present authorizations to it and consume the replay, single-use and daily-spend budgets of
 * any credential whose holder they can impersonate — and can drive state into the store on a port
 * nobody is watching. Reaching the port must not be the whole of the authorization check, so this
 * class no longer lets it be one by omission. A comment saying a proxy is expected in front is not
 * a control; [HostAuthentication.frontedByProxy] is the same claim made where the code can see it.
 *
 * ```kotlin
 * server.withAuthentication(HostAuthentication.bearerToken(System.getenv("AVP_TOKEN")))
 * server.withAuthentication(HostAuthentication.custom { call -> myGateway.authorize(call) })
 * server.withAuthentication(HostAuthentication.frontedByProxy("mTLS terminated at the ingress"))
 * ```
 *
 * ## State
 *
 * The [engine]'s default store keeps replay, single-use and daily-spend state **in this process**.
 * A restart forgets every nonce it has seen, and two replicas enforce two separate sets of limits
 * — for a daily cap, that means the cap can be spent once per replica. Any deployment that is not
 * exactly one process must construct the engine with a
 * `PostgresAuthorizationStore` sharing one database across every instance. That is a correctness
 * decision, not a scaling one.
 *
 * Authentication is not encryption. Terminate TLS in front of this server, or bind to loopback and
 * let the fronting layer be what listens publicly.
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
    private var authentication: HostAuthentication? = null

    /** Configure tracing, protected metrics and optional admission limits before starting. */
    fun withObservability(configuration: HostObservability): AvpAuthorizationServer {
        check(server == null) { "Configure observability before starting the server" }
        observability = configuration
        return this
    }

    /**
     * Declares what authenticates callers. Required before this server authorizes a payment.
     *
     * Until it is called, POST is refused with 503. Use [HostAuthentication.bearerToken] or
     * [HostAuthentication.custom] to authenticate here, or [HostAuthentication.frontedByProxy] to
     * record that something in front already does.
     */
    fun withAuthentication(configuration: HostAuthentication): AvpAuthorizationServer {
        check(server == null) { "Configure authentication before starting the server" }
        authentication = configuration
        return this
    }

    fun start(wait: Boolean = false) {
        server =
            embeddedServer(Netty, port = port, host = host) {
                configureApplication()
            }.start(wait = wait)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    internal fun Application.configureApplication() {
        observability?.install(this, HostKind.AVP_AUTHORIZATION)
        // No authentication configured is not the same as no authentication needed:
        // refuse until the host states which of the two it means.
        authentication?.install(this)
            ?: HostAuthentication.Unconfigured("The AVP authorization server").install(this)
        configureAuthorization(engine)
    }
}
