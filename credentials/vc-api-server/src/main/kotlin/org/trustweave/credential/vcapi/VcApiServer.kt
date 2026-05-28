package org.trustweave.credential.vcapi

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.CredentialService

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
 * @param credentialService The [CredentialService] used for all issuance and verification.
 * @param port TCP port to listen on (default 8080).
 * @param host Bind address (default "0.0.0.0" — all interfaces).
 */
class VcApiServer(
    private val credentialService: CredentialService,
    private val port: Int = 8080,
    private val host: String = "0.0.0.0",
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
            configureVcApiRoutes(credentialService)
        }
    }
}
