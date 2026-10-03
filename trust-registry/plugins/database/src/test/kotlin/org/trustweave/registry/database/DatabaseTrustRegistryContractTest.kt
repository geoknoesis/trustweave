package org.trustweave.registry.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.trustweave.registry.AccreditationStatus
import org.trustweave.registry.IssuerRegistration
import org.trustweave.registry.IssuerUpdate
import org.trustweave.registry.ParticipantAlreadyRegisteredException
import org.trustweave.registry.VerifierRegistration
import org.trustweave.registry.VerifierUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Same contract as `InMemoryTrustRegistryContractTest`, against H2. */
class DatabaseTrustRegistryContractTest {
    private fun dataSource() =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:registry_contract_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                username = "sa"
                password = ""
                maximumPoolSize = 3
            },
        )

    private val registry = DatabaseTrustRegistry(dataSource())

    @Test
    fun `re-registering a revoked issuer is rejected and keeps it revoked`() =
        runBlocking<Unit> {
            registry.registerIssuer(IssuerRegistration(did = "did:key:i", name = "I"))
            registry.revokeIssuer("did:key:i", "fraud")
            assertFailsWith<ParticipantAlreadyRegisteredException> {
                registry.registerIssuer(IssuerRegistration(did = "did:key:i", name = "Other"))
            }
            assertEquals(AccreditationStatus.REVOKED, registry.getAccreditationStatus("did:key:i"))
            assertEquals("I", registry.getIssuer("did:key:i")!!.name)
        }

    @Test
    fun `re-registering a verifier is rejected`() =
        runBlocking<Unit> {
            registry.registerVerifier(VerifierRegistration(did = "did:key:v", name = "V"))
            assertFailsWith<ParticipantAlreadyRegisteredException> {
                registry.registerVerifier(VerifierRegistration(did = "did:key:v", name = "V"))
            }
        }

    @Test
    fun `revocation reason is persisted and cleared on activation`() =
        runBlocking<Unit> {
            registry.registerIssuer(IssuerRegistration(did = "did:key:i", name = "I"))
            registry.revokeIssuer("did:key:i", "key compromise")
            assertEquals("key compromise", registry.getIssuer("did:key:i")!!.revocationReason)
            registry.activateIssuer("did:key:i")
            assertNull(registry.getIssuer("did:key:i")!!.revocationReason)

            registry.registerVerifier(VerifierRegistration(did = "did:key:v", name = "V"))
            registry.revokeVerifier("did:key:v", "expired")
            assertEquals("expired", registry.getVerifier("did:key:v")!!.revocationReason)
        }

    @Test
    fun `update keeps unspecified fields and the revoked status`() =
        runBlocking<Unit> {
            registry.registerIssuer(IssuerRegistration(did = "did:key:i", name = "I", description = "d", credentialTypes = listOf("A")))
            registry.revokeIssuer("did:key:i", "r")
            val updated = registry.updateIssuer("did:key:i", IssuerUpdate(name = "New"))
            assertEquals("New", updated.name)
            assertEquals("d", updated.description)
            assertEquals(listOf("A"), updated.credentialTypes)
            assertEquals(AccreditationStatus.REVOKED, updated.status)
            assertEquals("r", updated.revocationReason)
            assertFailsWith<NoSuchElementException> { registry.updateVerifier("did:key:none", VerifierUpdate(name = "x")) }
        }

    @Test
    fun `tables created before revocation_reason existed are migrated`() =
        runBlocking<Unit> {
            val ds = dataSource()
            ds.connection.use { conn ->
                conn.createStatement().execute(
                    "CREATE TABLE registry_issuers (did VARCHAR(512) PRIMARY KEY, name VARCHAR(255) NOT NULL, " +
                        "description TEXT, credential_types TEXT NOT NULL DEFAULT '[]', service_endpoint VARCHAR(1024), " +
                        "status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', registered_at TIMESTAMP NOT NULL, " +
                        "updated_at TIMESTAMP NOT NULL, metadata TEXT NOT NULL DEFAULT '{}')",
                )
            }
            val legacy = DatabaseTrustRegistry(ds)
            legacy.registerIssuer(IssuerRegistration(did = "did:key:old", name = "Old"))
            legacy.revokeIssuer("did:key:old", "migrated")
            assertEquals("migrated", legacy.getIssuer("did:key:old")!!.revocationReason)
        }
}
