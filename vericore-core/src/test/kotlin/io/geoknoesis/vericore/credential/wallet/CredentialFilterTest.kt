package io.geoknoesis.vericore.credential.wallet

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.time.Instant

/**
 * Comprehensive tests for CredentialFilter.
 */
class CredentialFilterTest {

    @Test
    fun `test CredentialFilter with all fields`() {
        val filter = CredentialFilter(
            issuer = "did:key:issuer",
            type = listOf("PersonCredential", "EducationCredential"),
            subjectId = "did:key:subject",
            expired = false,
            revoked = false
        )
        
        assertEquals("did:key:issuer", filter.issuer)
        assertEquals(2, filter.type?.size)
        assertEquals("did:key:subject", filter.subjectId)
        assertEquals(false, filter.expired)
        assertEquals(false, filter.revoked)
    }

    @Test
    fun `test CredentialFilter with defaults`() {
        val filter = CredentialFilter()
        
        assertNull(filter.issuer)
        assertNull(filter.type)
        assertNull(filter.subjectId)
        assertNull(filter.expired)
        assertNull(filter.revoked)
    }

    @Test
    fun `test CredentialFilter with partial fields`() {
        val filter = CredentialFilter(
            issuer = "did:key:issuer",
            expired = true
        )
        
        assertEquals("did:key:issuer", filter.issuer)
        assertNull(filter.type)
        assertNull(filter.subjectId)
        assertEquals(true, filter.expired)
        assertNull(filter.revoked)
    }
}



