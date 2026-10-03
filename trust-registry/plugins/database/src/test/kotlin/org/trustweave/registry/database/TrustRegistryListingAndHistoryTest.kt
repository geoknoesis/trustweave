package org.trustweave.registry.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.trustweave.registry.AccreditationStatus
import org.trustweave.registry.InMemoryTrustRegistry
import org.trustweave.registry.IssuerRegistration
import org.trustweave.registry.ParticipantRole
import org.trustweave.registry.RegistryFilter
import org.trustweave.registry.TrustRegistry
import org.trustweave.registry.VerifierRegistration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Listing filters/paging and the status audit trail, run against both implementations for parity. */
class TrustRegistryListingAndHistoryTest {
    private fun dataSource(name: String = "registry_list") =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:${name}_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                username = "sa"
                password = ""
                maximumPoolSize = 10
            },
        )

    private fun registries(): Map<String, TrustRegistry> =
        mapOf("database" to DatabaseTrustRegistry(dataSource()), "in-memory" to InMemoryTrustRegistry())

    private fun forEachRegistry(block: suspend (TrustRegistry) -> Unit) {
        registries().forEach { (name, registry) ->
            try {
                runBlocking { block(registry) }
            } catch (e: AssertionError) {
                throw AssertionError("[$name] ${e.message}", e)
            }
        }
    }

    private suspend fun seed(registry: TrustRegistry) {
        // Distinct registration instants are not guaranteed, so DID is the tie-breaker the contract promises.
        for (i in 1..7) {
            registry.registerIssuer(
                IssuerRegistration(
                    did = "did:key:i%02d".format(i),
                    name = if (i == 3) "Acme 100% Trusted_Co" else "Issuer $i",
                    credentialTypes = if (i % 2 == 0) listOf("Diploma", "A_B") else listOf("License", "AxB"),
                ),
            )
            Thread.sleep(2) // distinct registered_at at millisecond resolution
        }
        registry.revokeIssuer("did:key:i04", "fraud")
        registry.revokeIssuer("did:key:i06", "fraud")
        for (i in 1..4) registry.registerVerifier(VerifierRegistration(did = "did:key:v$i", name = "Verifier $i"))
    }

    @Test
    fun `listing is ordered by registration then DID`() =
        forEachRegistry { registry ->
            seed(registry)
            assertEquals((1..7).map { "did:key:i%02d".format(it) }, registry.listIssuers().map { it.did })
        }

    @Test
    fun `pages partition the filtered listing without gaps or overlap`() =
        forEachRegistry { registry ->
            seed(registry)
            val all = registry.listIssuers()
            val pages = (0 until 3).flatMap { registry.listIssuers(RegistryFilter(), limit = 3, offset = it * 3) }
            assertEquals(all.map { it.did }, pages.map { it.did })
            assertEquals(emptyList(), registry.listIssuers(RegistryFilter(), limit = 3, offset = 7))
            assertEquals(emptyList(), registry.listIssuers(RegistryFilter(), limit = 0, offset = 0))
            assertEquals(2, registry.listIssuers(RegistryFilter(), limit = 2, offset = 5).size)
        }

    @Test
    fun `filters apply before paging`() =
        forEachRegistry { registry ->
            seed(registry)
            val revoked = RegistryFilter(status = AccreditationStatus.REVOKED)
            assertEquals(listOf("did:key:i04", "did:key:i06"), registry.listIssuers(revoked).map { it.did })
            assertEquals(listOf("did:key:i06"), registry.listIssuers(revoked, limit = 1, offset = 1).map { it.did })
            assertEquals(
                listOf("did:key:i02", "did:key:i04", "did:key:i06"),
                registry.listIssuers(RegistryFilter(credentialType = "Diploma")).map { it.did },
            )
            assertEquals(listOf("did:key:i02"), registry.listIssuers(RegistryFilter(credentialType = "Diploma"), 1, 0).map { it.did })
            assertEquals(
                listOf("did:key:i06"),
                registry.listIssuers(RegistryFilter(status = AccreditationStatus.REVOKED, nameContains = "ISSUER 6")).map { it.did },
                "name match is case-insensitive and combined with status",
            )
        }

    @Test
    fun `LIKE wildcards in filters are matched literally`() =
        forEachRegistry { registry ->
            seed(registry)
            assertEquals(listOf("did:key:i03"), registry.listIssuers(RegistryFilter(nameContains = "100%")).map { it.did })
            assertEquals(listOf("did:key:i03"), registry.listIssuers(RegistryFilter(nameContains = "trusted_co")).map { it.did })
            assertEquals(
                emptyList(),
                registry.listIssuers(RegistryFilter(nameContains = "%")).map { it.did }.filter { it != "did:key:i03" },
            )
            // "A_B" must not match "AxB"
            assertEquals(
                listOf("did:key:i02", "did:key:i04", "did:key:i06"),
                registry.listIssuers(RegistryFilter(credentialType = "A_B")).map { it.did },
            )
        }

    @Test
    fun `verifier listing pages and filters`() =
        forEachRegistry { registry ->
            seed(registry)
            registry.revokeVerifier("did:key:v2", "r")
            assertEquals(4, registry.listVerifiers().size)
            assertEquals(listOf("did:key:v2"), registry.listVerifiers(RegistryFilter(status = AccreditationStatus.REVOKED)).map { it.did })
            assertEquals(2, registry.listVerifiers(RegistryFilter(), limit = 2, offset = 2).size)
        }

    @Test
    fun `invalid paging arguments are rejected`() =
        forEachRegistry { registry ->
            assertFailsWith<IllegalArgumentException> { registry.listIssuers(RegistryFilter(), limit = -1, offset = 0) }
            assertFailsWith<IllegalArgumentException> { registry.listVerifiers(RegistryFilter(), limit = 1, offset = -1) }
        }

    @Test
    fun `credential types are the sorted distinct union`() =
        forEachRegistry { registry ->
            seed(registry)
            assertEquals(listOf("AxB", "A_B", "Diploma", "License").sorted(), registry.listCredentialTypes())
        }

    @Test
    fun `status history records registration, revocation and activation in order`() =
        forEachRegistry { registry ->
            registry.registerIssuer(IssuerRegistration(did = "did:key:h", name = "H"))
            Thread.sleep(3)
            registry.revokeIssuer("did:key:h", "key compromise")
            Thread.sleep(3)
            registry.activateIssuer("did:key:h")
            val history = registry.statusHistory("did:key:h")
            assertEquals(3, history.size)
            assertEquals(listOf(null, AccreditationStatus.ACTIVE, AccreditationStatus.REVOKED), history.map { it.from })
            assertEquals(listOf(AccreditationStatus.ACTIVE, AccreditationStatus.REVOKED, AccreditationStatus.ACTIVE), history.map { it.to })
            assertEquals(listOf(null, "key compromise", null), history.map { it.reason })
            assertTrue(history.all { it.role == ParticipantRole.ISSUER && it.did == "did:key:h" })
            assertTrue(history.zipWithNext().all { (a, b) -> a.changedAt <= b.changedAt })
        }

    @Test
    fun `history keeps issuer and verifier roles apart and is empty for unknown DIDs`() =
        forEachRegistry { registry ->
            registry.registerVerifier(VerifierRegistration(did = "did:key:vv", name = "V"))
            registry.revokeVerifier("did:key:vv")
            assertEquals(listOf(ParticipantRole.VERIFIER, ParticipantRole.VERIFIER), registry.statusHistory("did:key:vv").map { it.role })
            assertEquals(emptyList(), registry.statusHistory("did:key:nobody"))
            assertEquals(false, registry.revokeIssuer("did:key:nobody"))
            assertEquals(emptyList(), registry.statusHistory("did:key:nobody"))
        }

    @Test
    fun `a rejected duplicate registration leaves no history row`() =
        forEachRegistry { registry ->
            registry.registerIssuer(IssuerRegistration(did = "did:key:d", name = "D"))
            runCatching { registry.registerIssuer(IssuerRegistration(did = "did:key:d", name = "D2")) }
            assertEquals(1, registry.statusHistory("did:key:d").size)
        }

    @Test
    fun `concurrent first start-ups migrate a legacy table without error`() {
        val ds = dataSource("registry_migrate")
        ds.connection.use { conn ->
            conn.createStatement().execute(
                "CREATE TABLE registry_issuers (did VARCHAR(512) PRIMARY KEY, name VARCHAR(255) NOT NULL, " +
                    "description TEXT, credential_types TEXT NOT NULL DEFAULT '[]', service_endpoint VARCHAR(1024), " +
                    "status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', registered_at TIMESTAMP NOT NULL, " +
                    "updated_at TIMESTAMP NOT NULL, metadata TEXT NOT NULL DEFAULT '{}')",
            )
            conn.createStatement().execute(
                "CREATE TABLE registry_verifiers (did VARCHAR(512) PRIMARY KEY, name VARCHAR(255) NOT NULL, " +
                    "description TEXT, service_endpoint VARCHAR(1024), " +
                    "status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', registered_at TIMESTAMP NOT NULL, " +
                    "updated_at TIMESTAMP NOT NULL, metadata TEXT NOT NULL DEFAULT '{}')",
            )
        }
        val starters = 8
        val pool = Executors.newFixedThreadPool(starters)
        val gate = CountDownLatch(1)
        try {
            val futures =
                (1..starters).map {
                    pool.submit<DatabaseTrustRegistry> {
                        gate.await()
                        DatabaseTrustRegistry(ds)
                    }
                }
            gate.countDown()
            val registries = futures.map { it.get(60, TimeUnit.SECONDS) }
            runBlocking {
                registries.first().registerIssuer(IssuerRegistration(did = "did:key:m", name = "M"))
                registries.last().revokeIssuer("did:key:m", "migrated")
                assertEquals("migrated", registries[2].getIssuer("did:key:m")!!.revocationReason)
            }
            // A further start on the already-migrated schema is a no-op, not an error.
            DatabaseTrustRegistry(ds)
        } finally {
            pool.shutdownNow()
        }
    }
}
