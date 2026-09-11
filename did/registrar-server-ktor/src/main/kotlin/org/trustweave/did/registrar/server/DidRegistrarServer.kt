package org.trustweave.did.registrar.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import org.trustweave.did.registrar.storage.JobStorage
import org.trustweave.observability.HostAuthentication
import org.trustweave.observability.HostKind
import org.trustweave.observability.HostObservability

/**
 * DID Registrar Server implementation.
 *
 * RESTful endpoints:
 * - `POST /1.0/dids` - Create DID
 * - `PUT /1.0/dids/{did}` - Update DID
 * - `DELETE /1.0/dids/{did}` - Deactivate DID
 * - `GET /1.0/jobs/{jobId}` - Get job status
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val registrar = KmsBasedRegistrar(kms)
 * val server = DidRegistrarServer(
 *     registrar = registrar,
 *     port = 8080
 * )
 * server.start()
 * ```
 *
 * @param registrar The DID Registrar implementation to use for operations
 * @param port Server port (default: 8080)
 * @param host Bind address. Defaults to loopback: exposing an embedded server to the network
 *   is an explicit decision, not something that happens because a default was left alone. Pass
 *   "0.0.0.0" once something in front of it authenticates callers.
 *
 * ## Security
 *
 * This server creates, updates and deactivates DIDs with the keys its KMS holds. Configure
 * [withAuthentication] before starting it; until you do, every mutating request is refused with
 * 503 and reads keep working. `HostAuthentication.frontedByProxy("...")` records the case where
 * something in front already authenticates callers.
 * @param jobStorage Storage for tracking long-running operations (default: InMemoryJobStorage)
 */
class DidRegistrarServer(
    private val registrar: DidRegistrar,
    private val port: Int = 8080,
    private val host: String = "127.0.0.1",
    private val jobStorage: JobStorage = InMemoryJobStorage(),
) {
    private var server: NettyApplicationEngine? = null
    private var observability: HostObservability? = null
    private var authentication: HostAuthentication? = null

    /** Configure tracing, protected metrics and optional admission limits before starting. */
    fun withObservability(configuration: HostObservability): DidRegistrarServer {
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
    fun withAuthentication(configuration: HostAuthentication): DidRegistrarServer {
        check(server == null) { "Configure authentication before starting the server" }
        authentication = configuration
        return this
    }

    /**
     * Starts the DID Registrar server.
     *
     * The server will run until [stop] is called.
     */
    fun start(wait: Boolean = false) {
        server =
            embeddedServer(Netty, port = port, host = host) {
                configureApplication()
            }.start(wait = wait)
    }

    /**
     * Stops the DID Registrar server.
     */
    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    /**
     * Configures the Ktor application with routing and serialization.
     */
    private fun Application.configureApplication() {
        observability?.install(this, HostKind.DID_REGISTRAR)
        // No authentication configured is not the same as no authentication needed:
        // refuse mutations until the host states which of the two it means.
        authentication?.install(this)
            ?: HostAuthentication.Unconfigured("The DID registrar").install(this)
        // Configure JSON serialization
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                },
            )
        }

        // Configure routing
        routing {
            configureDidRegistrarRoutes(registrar, jobStorage)
        }
    }
}
