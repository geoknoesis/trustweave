package org.trustweave.registry.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.registry.TrustRegistry

class TrustRegistryServer(
    private val registry: TrustRegistry,
    private val port: Int = 8081,
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
            configureTrustRegistryRoutes(registry)
        }
    }
}
