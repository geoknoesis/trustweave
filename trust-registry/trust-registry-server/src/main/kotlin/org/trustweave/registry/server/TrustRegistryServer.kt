package org.trustweave.registry.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.observability.HostAuthentication
import org.trustweave.observability.HostKind
import org.trustweave.observability.HostObservability
import org.trustweave.registry.TrustRegistry

/**
 * Embedded HTTP server exposing a [TrustRegistry].
 *
 * ## Security
 *
 * Mutating routes — register, update, revoke — are refused until the host says what protects
 * them. Either mechanism satisfies that, and they compose rather than stack:
 *
 * - [withAuthentication] installs the same [HostAuthentication] gate the other embedded servers
 *   use, which is what to reach for when the deployment authenticates with mTLS, a JWT, a
 *   gateway, or declares [HostAuthentication.frontedByProxy].
 * - [apiToken] is the registry's own bearer check, kept for hosts already using it.
 *
 * Configuring neither leaves mutations disabled (503). Configuring [withAuthentication] is what
 * authorizes the call; the [apiToken] check then steps aside rather than demanding a second
 * credential the gate has no way to supply.
 *
 * @param apiToken bearer token required on mutating routes
 *   (`Authorization: Bearer <token>`). When null (the default), all
 *   mutating routes are disabled and respond 503 — the server fails
 *   closed. Read-only routes are always available.
 * @param host Bind address. Defaults to loopback: exposing an embedded server to the network
 *   is an explicit decision, not something that happens because a default was left alone. Pass
 *   "0.0.0.0" once something in front of it authenticates callers.
 */
class TrustRegistryServer(
    private val registry: TrustRegistry,
    private val port: Int = 8081,
    private val host: String = "127.0.0.1",
    private val apiToken: String? = null,
) {
    private var server: NettyApplicationEngine? = null
    private var observability: HostObservability? = null
    private var authentication: HostAuthentication? = null

    /** Configure tracing, protected metrics and optional admission limits before starting. */
    fun withObservability(configuration: HostObservability): TrustRegistryServer {
        check(server == null) { "Configure observability before starting the server" }
        observability = configuration
        return this
    }

    /**
     * Declares what authenticates callers, superseding the [apiToken] check on mutating routes.
     *
     * Until this or [apiToken] is configured, mutations are refused. Use
     * [HostAuthentication.bearerToken] or [HostAuthentication.custom] to authenticate here, or
     * [HostAuthentication.frontedByProxy] to record that something in front already does.
     */
    fun withAuthentication(configuration: HostAuthentication): TrustRegistryServer {
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
        observability?.install(this, HostKind.TRUST_REGISTRY)
        // When the host gate is configured it has already decided; the route-level token check
        // then stands down instead of demanding a second credential the gate cannot supply.
        authentication?.install(this)
        install(ContentNegotiation) {
            json(
                Json {
                    serializersModule = SerializationModule.default
                    ignoreUnknownKeys = true
                    prettyPrint = true
                },
            )
        }
        routing {
            configureTrustRegistryRoutes(registry, apiToken, hostAuthenticated = authentication != null)
        }
    }
}
