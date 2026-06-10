package org.trustweave.registry.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.trustweave.registry.*
import kotlin.test.*

class DatabaseTrustRegistryTest {

    private lateinit var registry: DatabaseTrustRegistry

    @BeforeTest
    fun setUp() {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:registry_test_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
            username = "sa"; password = ""; maximumPoolSize = 5
        }
        registry = DatabaseTrustRegistry(HikariDataSource(config))
    }

    @Test
    fun `register and get issuer`() = runBlocking {
        val reg = IssuerRegistration(did = "did:key:issuer1", name = "Test Issuer",
            credentialTypes = listOf("DegreeCredential"))
        val record = registry.registerIssuer(reg)
        assertEquals("did:key:issuer1", record.did)
        assertEquals(AccreditationStatus.ACTIVE, record.status)

        val fetched = registry.getIssuer("did:key:issuer1")
        assertNotNull(fetched)
        assertEquals("Test Issuer", fetched.name)
    }

    @Test
    fun `getIssuer returns null for unknown DID`() = runBlocking {
        assertNull(registry.getIssuer("did:key:unknown"))
    }

    @Test
    fun `listIssuers filters by status`() = runBlocking {
        registry.registerIssuer(IssuerRegistration("did:key:a", "A"))
        registry.registerIssuer(IssuerRegistration("did:key:b", "B"))
        registry.revokeIssuer("did:key:b")
        val active = registry.listIssuers(RegistryFilter(status = AccreditationStatus.ACTIVE))
        assertTrue(active.all { it.status == AccreditationStatus.ACTIVE })
        assertTrue(active.any { it.did == "did:key:a" })
        assertTrue(active.none { it.did == "did:key:b" })
    }

    @Test
    fun `listIssuers filters by credentialType`() = runBlocking {
        registry.registerIssuer(IssuerRegistration("did:key:c", "C", credentialTypes = listOf("PassportCredential")))
        registry.registerIssuer(IssuerRegistration("did:key:d", "D", credentialTypes = listOf("DegreeCredential")))
        val passports = registry.listIssuers(RegistryFilter(credentialType = "PassportCredential"))
        assertEquals(1, passports.size)
        assertEquals("did:key:c", passports[0].did)
    }

    @Test
    fun `revokeIssuer sets status to REVOKED`() = runBlocking {
        registry.registerIssuer(IssuerRegistration("did:key:rev", "Revokable"))
        assertTrue(registry.revokeIssuer("did:key:rev"))
        assertEquals(AccreditationStatus.REVOKED, registry.getIssuer("did:key:rev")?.status)
    }

    @Test
    fun `register and get verifier`() = runBlocking {
        val reg = VerifierRegistration(did = "did:key:verifier1", name = "Test Verifier")
        val record = registry.registerVerifier(reg)
        assertEquals("did:key:verifier1", record.did)
        assertEquals(AccreditationStatus.ACTIVE, record.status)

        val fetched = registry.getVerifier("did:key:verifier1")
        assertNotNull(fetched); assertEquals("Test Verifier", fetched.name)
    }

    @Test
    fun `listVerifiers returns all registered verifiers`() = runBlocking {
        registry.registerVerifier(VerifierRegistration("did:key:v1", "V1"))
        registry.registerVerifier(VerifierRegistration("did:key:v2", "V2"))
        val list = registry.listVerifiers()
        assertTrue(list.size >= 2)
    }

    @Test
    fun `getAccreditationStatus returns UNKNOWN for unregistered DID`() = runBlocking {
        assertEquals(AccreditationStatus.UNKNOWN, registry.getAccreditationStatus("did:key:nobody"))
    }

    @Test
    fun `getAccreditationStatus reflects registered and revoked DIDs`() = runBlocking {
        registry.registerIssuer(IssuerRegistration("did:key:status-issuer", "Status Issuer"))
        assertEquals(AccreditationStatus.ACTIVE, registry.getAccreditationStatus("did:key:status-issuer"))
        registry.revokeIssuer("did:key:status-issuer")
        assertEquals(AccreditationStatus.REVOKED, registry.getAccreditationStatus("did:key:status-issuer"))
    }
}
