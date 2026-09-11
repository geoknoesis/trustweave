package org.trustweave.credential.vcapi

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
import org.trustweave.credential.CredentialService
import org.trustweave.observability.HostAuthentication
import org.trustweave.observability.HostKind
import org.trustweave.observability.HostObservability

/**
 * W3C VC API server (https://w3c-ccg.github.io/vc-api/).
 *
 * Exposes the standard W3C VC API endpoints backed by a TrustWeave [CredentialService]:
 *
 * - `POST /credentials/issue`    — Issue a new Verifiable Credential
 * - `POST /credentials/verify`   — Verify a Verifiable Credential
 * - `POST /presentations/prove`  — Assemble and sign a Verifiable Presentation
 * - `POST /presentations/verify` — Verify a Verifiable Presentation
 *
 * Example:
 * ```kotlin
 * val service = CredentialServices.createCredentialService(kms, didResolver)
 * val server = VcApiServer(credentialService = service, port = 8080)
 * server.start()
 * ```
 *
 * ## Security
 *
 * **Configure [withAuthentication] before starting this server.** Until you do, every POST, PUT,
 * PATCH and DELETE is refused with 503; reads keep working.
 *
 * That default exists because of `POST /credentials/issue`. It takes the issuer DID and the signing
 * `verificationMethod` from the request body, so anyone who can reach an unprotected port can have
 * the [CredentialService] sign a credential of their choosing with any key its KMS holds,
 * attributed to any issuer it can sign for. Reaching the port must not be the whole of the
 * authorization check, so this class no longer lets it be one by omission.
 *
 * Three ways to satisfy it:
 * ```kotlin
 * server.withAuthentication(HostAuthentication.bearerToken(System.getenv("VC_API_TOKEN")))
 * server.withAuthentication(HostAuthentication.custom { call -> myGateway.authorize(call) })
 * server.withAuthentication(HostAuthentication.frontedByProxy("mTLS terminated at the ingress"))
 * ```
 *
 * Authentication is not encryption. Terminate TLS in front of this server, or bind to loopback and
 * let the fronting layer be what listens publicly. `host = "127.0.0.1"` is the conservative start.
 *
 * @param credentialService The [CredentialService] used for all issuance and verification.
 * @param port TCP port to listen on (default 8080).
 * @param host Bind address. Defaults to loopback: exposing an embedded server to the network
 *   is an explicit decision, not something that happens because a default was left alone. Pass
 *   "0.0.0.0" once something in front of it authenticates callers.
 */
class VcApiServer(
    private val credentialService: CredentialService,
    private val port: Int = 8080,
    private val host: String = "127.0.0.1",
) {
    private var server: NettyApplicationEngine? = null
    private var observability: HostObservability? = null
    private var authentication: HostAuthentication? = null

    /** Configure tracing, protected metrics and optional admission limits before starting. */
    fun withObservability(configuration: HostObservability): VcApiServer {
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
    fun withAuthentication(configuration: HostAuthentication): VcApiServer {
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
        observability?.install(this, HostKind.VC_API)
        // No authentication configured is not the same as no authentication needed:
        // refuse mutations until the host states which of the two it means.
        authentication?.install(this)
            ?: HostAuthentication.Unconfigured("The VC API server").install(this)
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
            configureVcApiRoutes(credentialService)
        }
    }
}
