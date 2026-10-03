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
}
