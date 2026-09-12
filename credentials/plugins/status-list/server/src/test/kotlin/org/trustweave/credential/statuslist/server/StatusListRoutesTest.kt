package org.trustweave.credential.statuslist.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
            val logBytes = java.io.ByteArrayOutputStream()
            val previousErrorStream = System.err
            val logStream = java.io.PrintStream(logBytes, true, Charsets.UTF_8)
            val ids =
                try {
                    System.setErr(logStream)
                    coroutineScope {
                        (0 until 20)
                            .map { index ->
                                async {
                                    val path = if (index % 2 == 0) "/status-lists/example" else "/token-status-lists/example"
                                    val response = client.get(path) { header("X-Request-ID", "attacker-controlled-secret") }
                                    assertEquals(HttpStatusCode.InternalServerError, response.status)
                                    val body = response.bodyAsText()
                                    assertTrue(body.contains("Unable to retrieve status list"))
                                    kotlin.test.assertFalse(body.contains("private-db"))
                                    kotlin.test.assertFalse(body.contains("secret"))
                                    val requestId =
                                        Json
                                            .parseToJsonElement(body)
                                            .jsonObject["requestId"]!!
                                            .jsonPrimitive.content
                                    assertEquals(requestId, response.headers["X-Request-ID"])
                                    assertEquals(
                                        requestId,
                                        java.util.UUID
                                            .fromString(requestId)
                                            .toString(),
                                    )
                                    requestId
                                }
                            }.awaitAll()
                    }
                } finally {
                    System.setErr(previousErrorStream)
                    logStream.close()
                }
            assertEquals(20, ids.toSet().size)
            val logs = logBytes.toString(Charsets.UTF_8)
            val events = logs.lines().filter { it.contains("event=status_list_failure") }
            assertEquals(20, events.size)
            for ((index, id) in ids.withIndex()) {
                val code = if (index % 2 == 0) "CONFIGURATION_FAILURE" else "STORAGE_FAILURE"
                assertEquals(1, events.count { it.endsWith("error_code=$code request_id=$id") })
            }
            kotlin.test.assertFalse(logs.contains("secret"))
            kotlin.test.assertFalse(logs.contains("private-db"))
            val output =
                evidenceDir("status-list-diagnostics.log")
            java.nio.file.Files
                .createDirectories(output.parent)
            java.nio.file.Files
                .writeString(output, events.joinToString("\n", postfix = "\n"))
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

    @Test
    fun `diagnostic error codes cannot disclose provider exception details`() {
        assertEquals("TIMEOUT", statusListErrorCode(java.sql.SQLTimeoutException("secret")))
        assertEquals("TIMEOUT", statusListErrorCode(java.net.SocketTimeoutException("secret")))
        assertEquals("STORAGE_FAILURE", statusListErrorCode(java.sql.SQLException("secret")))
        assertEquals("INTERNAL_FAILURE", statusListErrorCode(IllegalStateException("secret")))
    }
}
