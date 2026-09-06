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
    fun `configuration and database failures do not disclose internal details`() =
        testApplication {
            val backing =
                org.h2.jdbcx.JdbcDataSource().apply {
                    setURL("jdbc:h2:mem:status-errors-${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1")
                }
            var fail = false
            val source =
                object : javax.sql.DataSource by backing {
                    override fun getConnection(): java.sql.Connection {
                        if (fail) throw java.sql.SQLException("private-db.example password=secret")
                        return backing.connection
                    }
                }
            val kms =
                org.trustweave.testkit.kms
                    .InMemoryKeyManagementService()
            val bitstring =
                org.trustweave.revocation.bitstring
                    .BitstringStatusListManager(source, kms, "did:key:issuer")
            val token =
                org.trustweave.revocation.token
                    .TokenStatusListManager(source, kms, "did:key:issuer", "https://issuer.example/status")
            fail = true
            application {
                install(ContentNegotiation) { json() }
                routing { configureStatusListRoutes(bitstring, token) }
            }
            for (path in listOf("/status-lists/example", "/token-status-lists/example")) {
                val response = client.get(path)
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                val body = response.bodyAsText()
                assertTrue(body.contains("Unable to retrieve status list"))
                kotlin.test.assertFalse(body.contains("private-db"))
                kotlin.test.assertFalse(body.contains("secret"))
            }
        }

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
