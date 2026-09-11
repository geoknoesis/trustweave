package org.trustweave.credential.statuslist.server

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
import org.trustweave.revocation.bitstring.BitstringStatusListManager
import org.trustweave.revocation.token.TokenStatusListManager

/**
 * Ktor HTTP server that serves W3C Bitstring Status List credentials and IETF Token
 * Status List JWTs at stable public URLs, enabling verifiers to check credential status.
 *
 * Endpoints:
 * - `GET /status-lists/{id}` → signed BitstringStatusListCredential JSON
 *   (`Content-Type: application/vc+ld+json`)
 * - `GET /token-status-lists/{id}` → signed JWT status token
 *   (`Content-Type: application/statuslist+jwt`)
 *
 * Example:
 * ```kotlin
 * val server = StatusListServer(
 *     port = 8080,
 *     bitstringManager = BitstringStatusListManager(dataSource, kms, issuerDid),
 * )
 * server.start()
 * ```
 *
 * @param port TCP port to listen on (default 8080).
 * @param host Bind address. Defaults to loopback: exposing an embedded server to the network
 *   is an explicit decision, not something that happens because a default was left alone. Pass
 *   "0.0.0.0" once something in front of it authenticates callers.
 *
 * ## Security
 *
 * Status lists are published to be read, so GET stays open. Revocation and suspension are not:
 * configure [withAuthentication] before starting, or every mutating request is refused with 503.
 * @param bitstringManager Optional [BitstringStatusListManager] for W3C Bitstring Status Lists.
 * @param tokenManager Optional [TokenStatusListManager] for IETF Token Status Lists.
 */
class StatusListServer(
    private val port: Int = 8080,
    private val host: String = "127.0.0.1",
    private val bitstringManager: BitstringStatusListManager? = null,
    private val tokenManager: TokenStatusListManager? = null,
) {
    private var server: NettyApplicationEngine? = null
    private var observability: HostObservability? = null
    private var authentication: HostAuthentication? = null

    /** Configure tracing, protected metrics and optional admission limits before starting. */
    fun withObservability(configuration: HostObservability): StatusListServer {
        check(server == null) { "Configure observability before starting the server" }
        observability = configuration
        return this
    }

    /**
     * Declares what authenticates callers. Required before this server accepts a mutating request.
     *
     * Until it is called, POST, PUT, PATCH and DELETE are refused with 503. Reads keep working.
     * Use [HostAuthentication.bearerToken] or [HostAuthentication.custom] to authenticate here, or
     * [HostAuthentication.frontedByProxy] to record that something in front already does.
     */
    fun withAuthentication(configuration: HostAuthentication): StatusListServer {
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

    private fun Application.configureApplication() {
        observability?.install(this, HostKind.STATUS_LIST)
        // No authentication configured is not the same as no authentication needed:
        // refuse mutations until the host states which of the two it means.
        authentication?.install(this)
            ?: HostAuthentication.Unconfigured("The status list server").install(this)
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
            configureStatusListRoutes(bitstringManager, tokenManager)
        }
    }
}
