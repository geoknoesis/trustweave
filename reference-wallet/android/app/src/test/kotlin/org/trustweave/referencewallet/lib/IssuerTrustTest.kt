package org.trustweave.referencewallet.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests of where issuer trust may come from. */
class IssuerTrustTest {
    private val configured = setOf("did:key:z6MkConfigured")
    private val backend = setOf("did:key:z6MkBackend")
    private val accepted = listOf("did:key:z6MkAccepted")

    @Test
    fun `an issuer named only by an offer is not trusted`() {
        val policy = IssuerTrust.policy(configured, backend, accepted)
        assertFalse(policy.isTrusted("did:key:z6MkOfferNamed"))
    }

    @Test
    fun `configured backend and accepted issuers are trusted`() {
        val policy = IssuerTrust.policy(configured, backend, accepted)
        assertTrue(policy.isTrusted("did:key:z6MkConfigured"))
        assertTrue(policy.isTrusted("did:key:z6MkBackend"))
        assertTrue(policy.isTrusted("did:key:z6MkAccepted"))
    }

    @Test
    fun `a confirmed issuer is trusted for that import only`() {
        assertTrue(IssuerTrust.policy(configured, backend, accepted, "did:key:z6MkNew").isTrusted("did:key:z6MkNew"))
        assertFalse(IssuerTrust.policy(configured, backend, accepted).isTrusted("did:key:z6MkNew"))
    }

    @Test
    fun `confirming one issuer does not trust another`() {
        assertFalse(IssuerTrust.policy(emptySet(), emptySet(), emptyList(), "did:key:z6MkA").isTrusted("did:key:z6MkB"))
    }

    @Test
    fun `sanitize drops malformed oversized and duplicate entries and caps the list`() {
        val raw = listOf("did:key:z6MkA", "did:key:z6MkA", "<script>", "did:key:" + "a".repeat(300), "did:web:example.com")
        assertEquals(listOf("did:key:z6MkA", "did:web:example.com"), IssuerTrust.sanitize(raw))
        assertEquals(IssuerTrust.MAX_ACCEPTED, IssuerTrust.sanitize((0 until 5000).map { "did:key:z6Mk$it" }).size)
    }

    @Test
    fun `withAccepted refuses a non DID and a full list`() {
        assertThrows(IllegalArgumentException::class.java) { IssuerTrust.withAccepted(emptyList(), "javascript:alert(1)") }
        val full = (0 until IssuerTrust.MAX_ACCEPTED).map { "did:key:z6Mk$it" }
        assertThrows(IllegalArgumentException::class.java) { IssuerTrust.withAccepted(full, "did:key:z6MkNew") }
        assertEquals(full, IssuerTrust.withAccepted(full, "did:key:z6Mk1"))
    }

    @Test
    fun `parseConfigured trims and drops invalid entries`() {
        assertEquals(setOf("did:key:z6MkA", "did:key:z6MkB"), IssuerTrust.parseConfigured(" did:key:z6MkA , nonsense,did:key:z6MkB,"))
        assertEquals(emptySet<String>(), IssuerTrust.parseConfigured(null))
    }
}
