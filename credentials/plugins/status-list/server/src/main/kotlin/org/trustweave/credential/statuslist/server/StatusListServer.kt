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

    /** Configure tracing, protected metrics and optional admission limits before starting. */
    fun withObservability(configuration: HostObservability): StatusListServer {
        check(server == null) { "Configure observability before starting the server" }
        observability = configuration
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
