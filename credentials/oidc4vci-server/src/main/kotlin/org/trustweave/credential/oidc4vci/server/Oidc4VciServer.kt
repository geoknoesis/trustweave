package org.trustweave.credential.oidc4vci.server

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

/**
 * Standalone OID4VCI issuer server.
 *
 * ## Security
 *
 * **Configure [withAuthentication] before starting this server.** Until you do, `POST /api/offer`
 * is refused with 503.
 *
 * That route mints credential offers: a caller who reaches it unprotected can have this issuer
 * hand out pre-authorized codes for any credential type it is configured to issue, and then
 * redeem them through the protocol as a legitimate wallet would. Reaching the port must not be
 * the whole of that check.
 *
 * `/token` and `/credential` are deliberately not behind the host gate. They are the OID4VCI
 * protocol surface, authenticated by the pre-authorized code and the access token the spec
 * defines, and their callers are wallets, which hold no host credential. A host gate in front of
 * them would refuse the protocol rather than protect it.
 *
 * ```kotlin
 * server.withAuthentication(HostAuthentication.bearerToken(System.getenv("OID4VCI_ADMIN_TOKEN")))
 * server.withAuthentication(HostAuthentication.frontedByProxy("mTLS terminated at the ingress"))
 * ```
 *
 * @param host Bind address. Defaults to loopback: exposing an embedded server to the network is an
 *   explicit decision, not something that happens because a default was left alone. Pass "0.0.0.0"
 *   once something in front of it authenticates callers.
 */
class Oidc4VciServer(
    private val issuerService: Oidc4VciIssuerService,
    private val port: Int = 8080,
    private val host: String = "127.0.0.1",
) {
    private var server: NettyApplicationEngine? = null
    private var observability: HostObservability? = null
    private var authentication: HostAuthentication? = null

    /** Configure tracing, protected metrics and optional admission limits before starting. */
    fun withObservability(configuration: HostObservability): Oidc4VciServer {
        check(server == null) { "Configure observability before starting the server" }
        observability = configuration
        return this
    }

    /**
     * Declares what authenticates callers on the issuer's administrative routes.
     *
     * Until it is called, `POST /api/offer` is refused with 503. The OID4VCI protocol endpoints
     * are unaffected either way; they authenticate their own callers.
     */
    fun withAuthentication(configuration: HostAuthentication): Oidc4VciServer {
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

    internal fun Application.configureApplication() {
        observability?.install(this, HostKind.OID4VCI)
        // No authentication configured is not the same as no authentication needed:
        // refuse the administrative routes until the host states which of the two it means.
        authentication?.install(this, PROTOCOL_AUTHENTICATED_PATHS)
            ?: HostAuthentication.Unconfigured("The OID4VCI issuer server", PROTOCOL_AUTHENTICATED_PATHS).install(this)
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
            configureOidc4VciServerRoutes(issuerService)
        }
    }

    private companion object {
        /**
         * The OID4VCI protocol surface, authenticated by the spec's own credentials.
         *
         * `/token` takes a pre-authorized code; `/credential`, `/deferred_credential` and
         * `/notification` take the access token that route issued. Their callers are wallets. A
         * host gate in front of these would refuse the protocol, so the host gate covers the
         * administrative routes and these carry their own.
         */
        val PROTOCOL_AUTHENTICATED_PATHS = setOf("/token", "/credential", "/deferred_credential", "/notification")
    }
}
