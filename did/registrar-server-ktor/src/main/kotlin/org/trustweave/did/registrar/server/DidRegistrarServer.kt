package org.trustweave.did.registrar.server

import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.storage.JobStorage
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import org.trustweave.did.registrar.storage.DatabaseJobStorage
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

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
 * @param host Server host (default: "0.0.0.0")
 * @param jobStorage Storage for tracking long-running operations (default: InMemoryJobStorage)
 */
class DidRegistrarServer(
    private val registrar: DidRegistrar,
    private val port: Int = 8080,
    private val host: String = "0.0.0.0",
    private val jobStorage: JobStorage = InMemoryJobStorage()
) {
    private var server: NettyApplicationEngine? = null

    /**
     * Starts the DID Registrar server.
     *
     * The server will run until [stop] is called.
     */
    fun start(wait: Boolean = false) {
        server = embeddedServer(Netty, port = port, host = host) {
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
        // Configure JSON serialization
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }

        // Configure routing
        routing {
            configureDidRegistrarRoutes(registrar, jobStorage)
        }
    }
}

