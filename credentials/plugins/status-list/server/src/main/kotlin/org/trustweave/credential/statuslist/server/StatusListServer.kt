package org.trustweave.credential.statuslist.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.trustweave.core.serialization.SerializationModule
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
 * @param host Bind address (default "0.0.0.0" — all interfaces).
 * @param bitstringManager Optional [BitstringStatusListManager] for W3C Bitstring Status Lists.
 * @param tokenManager Optional [TokenStatusListManager] for IETF Token Status Lists.
 */
class StatusListServer(
    private val port: Int = 8080,
    private val host: String = "0.0.0.0",
    private val bitstringManager: BitstringStatusListManager? = null,
    private val tokenManager: TokenStatusListManager? = null,
) {
    private var server: NettyApplicationEngine? = null

    fun start(wait: Boolean = false) {
        server = embeddedServer(Netty, port = port, host = host) {
            configureApplication()
        }.start(wait = wait)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    private fun Application.configureApplication() {
        install(ContentNegotiation) {
            json(Json {
                serializersModule = SerializationModule.default
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        routing {
            configureStatusListRoutes(bitstringManager, tokenManager)
        }
    }
}
