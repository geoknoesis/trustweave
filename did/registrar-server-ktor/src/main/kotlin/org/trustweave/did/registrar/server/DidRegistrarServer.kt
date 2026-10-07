package org.trustweave.did.registrar.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import org.trustweave.did.registrar.storage.JobStorage
import org.trustweave.did.serialization.DidJsonSerialization
import org.trustweave.observability.HostAuthentication
import org.trustweave.observability.HostKind
import org.trustweave.observability.HostObservability
import org.trustweave.observability.RequestBodyLimit

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
 *
 * **Job status reads need the same credential as mutations.** Job records can carry DID state, so
 * `GET /1.0/jobs/{jobId}` is gated whenever an authenticator is configured, whatever `protect`
 * set was passed to [HostAuthentication]. Other reads (resolution-style GETs) stay open.
 * @param jobStorage Storage for tracking long-running operations (default: InMemoryJobStorage)
 */
class DidRegistrarServer(
    private val registrar: DidRegistrar,
    private val port: Int = 8080,
    private val host: String = "127.0.0.1",
    private val jobStorage: JobStorage = InMemoryJobStorage(),
) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var observability: HostObservability? = null
    private var authentication: HostAuthentication? = null
    private var maxRequestBytes: Long = RequestBodyLimit.DEFAULT_MAX_BYTES

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
     * Sets the largest request body, in bytes, this server accepts; anything larger is answered
     * with 413 before it is parsed. Defaults to [RequestBodyLimit.DEFAULT_MAX_BYTES] (1 MiB).
     */
    fun withMaxRequestBytes(bytes: Long): DidRegistrarServer {
        check(server == null) { "Configure the request limit before starting the server" }
        require(bytes > 0) { "maxRequestBytes must be positive" }
        maxRequestBytes = bytes
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
    internal fun Application.configureApplication() {
        observability?.install(this, HostKind.DID_REGISTRAR)
        // No authentication configured is not the same as no authentication needed:
        // refuse mutations until the host states which of the two it means.
        // Job records can carry DID state, so reads of them need the same credential as mutations.
        authentication?.forRegistrar()?.install(this)
            ?: HostAuthentication.Unconfigured("The DID registrar").install(this)
        // Configure JSON serialization
        // Bound the body before ContentNegotiation (or any handler) reads it.
        RequestBodyLimit.install(this, maxRequestBytes)
        install(ContentNegotiation) {
            json(registrarJson())
        }

        // Configure routing
        routing {
            configureDidRegistrarRoutes(registrar, jobStorage)
        }
    }
}

/**
 * The JSON configuration of the registrar API. It carries [DidJsonSerialization.module]:
 * without it, any response holding a verification method with a `publicKeyJwk` cannot be
 * encoded and every such create/update would answer 500.
 */
internal fun registrarJson(): Json =
    DidJsonSerialization.json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

private const val JOBS_PATH_PREFIX = "/1.0/jobs/"

/** The host's authentication plus the registrar's own rule: job records are privileged reads. */
internal fun HostAuthentication.forRegistrar(): HostAuthentication = protectingPathPrefixes(JOBS_PATH_PREFIX)
