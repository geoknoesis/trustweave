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
 * **These endpoints are not authenticated, and this class does not authenticate them.** There is no
 * API key, bearer token, or mTLS anywhere in this module.
 *
 * That matters most for `POST /credentials/issue`. It takes the issuer DID and the signing
 * `verificationMethod` from the request body, so anyone who can reach the port can have the
 * [CredentialService] sign a credential of their choosing with any key its KMS holds, attributed to
 * any issuer it can sign for. Reaching the port is the whole of the authorization check.
 *
 * The default [host] of "0.0.0.0" binds every interface, so the default configuration publishes
 * that capability to the network.
 *
 * Embed this behind something that authenticates and authorizes the caller — a reverse proxy, an
 * API gateway, or a Ktor `Authentication` plugin installed on the enclosing application — and bind
 * to a loopback or internal address unless the fronting layer is what listens publicly. Passing
 * `host = "127.0.0.1"` is the conservative starting point.
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

    /** Configure tracing, protected metrics and optional admission limits before starting. */
    fun withObservability(configuration: HostObservability): VcApiServer {
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
        observability?.install(this, HostKind.VC_API)
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
