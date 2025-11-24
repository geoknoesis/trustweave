package com.trustweave.did.registrar.server

import com.trustweave.did.registrar.DidRegistrar
import com.trustweave.did.registrar.storage.JobStorage
import com.trustweave.did.registrar.storage.InMemoryJobStorage
import com.trustweave.did.registrar.storage.DatabaseJobStorage
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Universal Registrar Server implementation.
 *
 * This server exposes the Universal Registrar protocol endpoints as defined by the
 * DID Registration specification (https://identity.foundation/did-registration/).
 *
 * **Endpoints:**
 * - `POST /1.0/operations` - Create, update, or deactivate DIDs
 * - `GET /1.0/operations/{jobId}` - Get status of long-running operations
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val registrar = KmsBasedRegistrar(kms)
 * val server = UniversalRegistrarServer(
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
class UniversalRegistrarServer(
    private val registrar: DidRegistrar,
    private val port: Int = 8080,
    private val host: String = "0.0.0.0",
    private val jobStorage: JobStorage = InMemoryJobStorage()
) {
    private var server: NettyApplicationEngine? = null

    /**
     * Starts the Universal Registrar server.
     *
     * The server will run until [stop] is called.
     */
    fun start(wait: Boolean = false) {
        server = embeddedServer(Netty, port = port, host = host) {
            configureApplication()
        }.start(wait = wait)
    }

    /**
     * Stops the Universal Registrar server.
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
            configureUniversalRegistrarRoutes(registrar, jobStorage)
        }
    }
}

