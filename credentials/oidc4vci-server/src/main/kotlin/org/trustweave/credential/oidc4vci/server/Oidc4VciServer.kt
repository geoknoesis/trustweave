package org.trustweave.credential.oidc4vci.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.trustweave.core.serialization.SerializationModule

class Oidc4VciServer(
    private val issuerService: Oidc4VciIssuerService,
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

    internal fun Application.configureApplication() {
        install(ContentNegotiation) {
            json(Json {
                serializersModule = SerializationModule.default
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        routing {
            configureOidc4VciServerRoutes(issuerService)
        }
    }
}
