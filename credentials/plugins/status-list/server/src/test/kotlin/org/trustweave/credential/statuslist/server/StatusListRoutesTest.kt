package org.trustweave.credential.statuslist.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusListRoutesTest {
    @Test
    fun `unconfigured managers return explicit unavailable responses`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { configureStatusListRoutes(null, null) }
            }
            for (path in listOf("/status-lists/example", "/token-status-lists/example")) {
                val response = client.get(path)
                assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
                assertTrue(response.bodyAsText().contains("NOT_CONFIGURED"))
            }
        }

    @Test
    fun `missing list identifiers do not resolve to a default list`() =
        testApplication {
            application {
                install(ContentNegotiation) { json() }
                routing { configureStatusListRoutes(null, null) }
            }
            for (path in listOf("/status-lists/", "/token-status-lists/")) {
                assertEquals(HttpStatusCode.NotFound, client.get(path).status)
            }
        }
}
