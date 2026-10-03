package org.trustweave.did.registrar.server.spring

import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.trustweave.did.registrar.client.KmsBasedRegistrar
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.server.spring.dto.CreateDidRequest
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DidRegistrarControllerAuthTest {
    private val token = "t".repeat(40)

    private fun controller(authentication: RegistrarAuthentication): DidRegistrarController {
        val jobs = InMemoryJobStorage()
        val registrar = KmsBasedRegistrar(InMemoryKeyManagementService(), jobs) { _, kms -> DidKeyMockMethod(kms) }
        return DidRegistrarController(DidRegistrarService(registrar, jobs), authentication)
    }

    private val request = CreateDidRequest(method = "key", options = CreateDidOptions())

    @Test
    fun `mutations are refused when authentication is not configured`() =
        runBlocking<Unit> {
            val c = controller(RegistrarAuthentication.unconfigured())

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, c.createDid(null, request).statusCode)
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, c.deactivateDid("Bearer $token", "did:key:abc", null).statusCode)
        }

    @Test
    fun `a missing or wrong bearer token is refused`() =
        runBlocking<Unit> {
            val c = controller(RegistrarAuthentication.bearerToken(token))

            assertEquals(HttpStatus.UNAUTHORIZED, c.createDid(null, request).statusCode)
            assertEquals(HttpStatus.UNAUTHORIZED, c.createDid("Bearer ${"x".repeat(40)}", request).statusCode)
            assertEquals(HttpStatus.UNAUTHORIZED, c.createDid(token, request).statusCode)
            assertEquals(HttpStatus.UNAUTHORIZED, c.deactivateDid(null, "did:key:abc", null).statusCode)
        }

    @Test
    fun `the right bearer token is admitted`() =
        runBlocking<Unit> {
            val response = controller(RegistrarAuthentication.bearerToken(token)).createDid("Bearer $token", request)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertIs<DidRegistrationResponse>(response.body)
        }

    @Test
    fun `a proxy declaration admits requests`() =
        runBlocking<Unit> {
            val response = controller(RegistrarAuthentication.frontedByProxy("mTLS at the gateway")).createDid(null, request)
            assertEquals(HttpStatus.OK, response.statusCode)
        }

    @Test
    fun `weak tokens are rejected and properties map to the right mode`() {
        assertFailsWith<IllegalArgumentException> { RegistrarAuthentication.bearerToken("short") }
        assertTrue(RegistrarAuthentication.fromProperties("", "").toString().contains("unconfigured"))
        assertTrue(RegistrarAuthentication.fromProperties(token, "").toString().contains("<redacted>"))
        assertTrue(!RegistrarAuthentication.fromProperties(token, "").toString().contains(token))
        assertEquals("gateway", RegistrarAuthentication.fromProperties(" ", "gateway").delegatedTo)
    }

    @Test
    fun `job status needs the same credentials as mutations`() =
        runBlocking<Unit> {
            val c = controller(RegistrarAuthentication.bearerToken(token))

            assertEquals(HttpStatus.UNAUTHORIZED, c.getJobStatus(null, "job-1").statusCode)
            assertEquals(HttpStatus.UNAUTHORIZED, c.getJobStatus("Bearer ${"x".repeat(40)}", "job-1").statusCode)
            // Authenticated callers get through to the lookup (job-1 does not exist -> 404, not 401).
            assertEquals(HttpStatus.NOT_FOUND, c.getJobStatus("Bearer $token", "job-1").statusCode)
        }

    @Test
    fun `job status is refused while authentication is unconfigured`() =
        runBlocking<Unit> {
            assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                controller(RegistrarAuthentication.unconfigured()).getJobStatus(null, "job-1").statusCode,
            )
        }

    @Test
    fun `public job status is an explicit opt-in and leaves mutations protected`() =
        runBlocking<Unit> {
            val auth = RegistrarAuthentication.fromProperties(token, "", publicJobStatus = true)
            val c = controller(auth)

            assertEquals(HttpStatus.NOT_FOUND, c.getJobStatus(null, "job-1").statusCode)
            assertEquals(HttpStatus.UNAUTHORIZED, c.createDid(null, request).statusCode)
            assertTrue(!RegistrarAuthentication.bearerToken(token).publicJobStatus)
        }

    @Test
    fun `bearer scheme is case-insensitive per RFC 7235`() =
        runBlocking<Unit> {
            val c = controller(RegistrarAuthentication.bearerToken(token))
            listOf("bearer $token", "BEARER $token", "BeArEr  $token").forEach {
                assertEquals(HttpStatus.OK, c.createDid(it, request).statusCode, it)
            }
            // Wrong scheme, token only, empty token and trailing garbage stay refused.
            listOf("Basic $token", token, "Bearer ", "Bearer $token ", "Bearer ${token}x", "Bearer").forEach {
                assertEquals(HttpStatus.UNAUTHORIZED, c.createDid(it, request).statusCode, it)
            }
        }

    @Test
    fun `tokens of different length are refused without error`() =
        runBlocking<Unit> {
            val c = controller(RegistrarAuthentication.bearerToken(token))
            assertEquals(HttpStatus.UNAUTHORIZED, c.createDid("Bearer ${token.take(10)}", request).statusCode)
            assertEquals(HttpStatus.UNAUTHORIZED, c.createDid("Bearer ${token.repeat(10)}", request).statusCode)
        }

    @Test
    fun `fronted-by-proxy needs a real statement, not a switch`() {
        assertFailsWith<IllegalArgumentException> { RegistrarAuthentication.frontedByProxy("") }
        assertFailsWith<IllegalArgumentException> { RegistrarAuthentication.frontedByProxy("   ") }
        assertFailsWith<IllegalArgumentException> { RegistrarAuthentication.frontedByProxy("true") }
        assertFailsWith<IllegalArgumentException> { RegistrarAuthentication.fromProperties("", "TRUE") }
        assertEquals("mTLS at the gateway", RegistrarAuthentication.frontedByProxy("  mTLS at the gateway ").delegatedTo)
    }
}
