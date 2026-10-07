package org.trustweave.observability

import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostAuthenticationCoverageTest {
    private val token = "t".repeat(40)

    @Test
    fun `a default gate and a declared proxy cover mutations`() {
        assertTrue(HostAuthentication.bearerToken(token).coversMutations())
        assertTrue(HostAuthentication.custom({ true }).coversMutations())
        assertTrue(HostAuthentication.frontedByProxy("mTLS at the ingress").coversMutations())
    }

    @Test
    fun `a gate that skips any mutating method does not`() {
        assertFalse(HostAuthentication.bearerToken(token, protect = setOf(HttpMethod.Get)).coversMutations())
        assertFalse(HostAuthentication.bearerToken(token, protect = setOf(HttpMethod.Post, HttpMethod.Put)).coversMutations())
        assertTrue(HostAuthentication.bearerToken(token, protect = HostAuthentication.ALL).coversMutations())
    }
}
