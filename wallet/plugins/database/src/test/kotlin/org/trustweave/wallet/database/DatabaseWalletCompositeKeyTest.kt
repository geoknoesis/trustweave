package org.trustweave.wallet.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** The credentials table is keyed by (wallet_id, id): wallets never collide and never observe each other. */
class DatabaseWalletCompositeKeyTest {
    private fun newDataSource(): HikariDataSource {
        val config = HikariConfig()
        config.jdbcUrl = "jdbc:h2:mem:composite-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        config.maximumPoolSize = 2
        return HikariDataSource(config)
    }

    private fun wallet(
        dataSource: DataSource,
        id: String,
    ) = DatabaseWallet.create(id, "did:key:w-$id", "did:key:h-$id", dataSource)

    private fun credential(
        id: String,
        type: String = "TestCredential",
    ) = VerifiableCredential(
        id = CredentialId(id),
        type = listOf(CredentialType.Custom(type)),
        issuer = Issuer.fromDid(Did("did:key:z6MkIssuer")),
        credentialSubject = CredentialSubject.fromIri("did:key:z6MkSubject"),
        issuanceDate = Clock.System.now(),
        proof = null,
    )

    private fun scalar(
        source: DataSource,
        sql: String,
    ): String? =
        source.connection.use { c ->
            c.createStatement().use { s -> s.executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1) else null } }
        }

    @Test
    fun `two wallets can store the same credential id independently`() =
        runBlocking<Unit> {
            newDataSource().use { source ->
                val a = wallet(source, "wallet-a")
                val b = wallet(source, "wallet-b")
                a.store(credential("urn:shared", "TypeA"))
                b.store(credential("urn:shared", "TypeB"))

                assertEquals(
                    "TypeA",
                    a
                        .get("urn:shared")!!
                        .type
                        .first()
                        .value,
                )
                assertEquals(
                    "TypeB",
                    b
                        .get("urn:shared")!!
                        .type
                        .first()
                        .value,
                )

                assertTrue(a.tagCredential("urn:shared", setOf("only-a")))
                assertEquals(emptySet(), b.getTags("urn:shared"))
                val col = a.createCollection("c", null)
                assertTrue(a.addToCollection("urn:shared", col))
                assertEquals(1, a.getCredentialsInCollection(col).size)
                assertTrue(b.query { byTag("only-a") }.isEmpty())
                assertTrue(a.addMetadata("urn:shared", mapOf("k" to "a-value")))
                assertTrue(b.getMetadata("urn:shared")!!.metadata.isEmpty())

                assertTrue(a.delete("urn:shared"))
                assertNull(a.get("urn:shared"))
                assertNotNull(b.get("urn:shared"))
                assertEquals(1, scalar(source, "SELECT COUNT(*) FROM credentials")!!.toInt())
                assertEquals(0, scalar(source, "SELECT COUNT(*) FROM credential_tags")!!.toInt())
                assertEquals(0, scalar(source, "SELECT COUNT(*) FROM credential_collections")!!.toInt())
            }
        }

    @Test
    fun `fresh schema records its version`() {
        newDataSource().use { source ->
            wallet(source, "w")
            assertEquals("2", scalar(source, "SELECT MAX(version) FROM schema_version"))
            wallet(source, "w2") // idempotent
            assertEquals("1", scalar(source, "SELECT COUNT(*) FROM schema_version"))
        }
    }

    @Test
    fun `a legacy single-key schema is migrated in place without losing data`() =
        runBlocking<Unit> {
            newDataSource().use { source ->
                val seed = wallet(source, "seed-wallet")
                val legacyCredential = Json.encodeToString(VerifiableCredential.serializer(), credential("urn:old"))
                source.connection.use { c ->
                    c.createStatement().use { s ->
                        for (t in listOf(
                            "credential_metadata",
                            "credential_tags",
                            "credential_collections",
                            "collections",
                            "credentials",
                            "schema_version",
                        )) {
                            s.execute("DROP TABLE IF EXISTS $t")
                        }
                        s.execute(
                            "CREATE TABLE credentials (id VARCHAR(255) PRIMARY KEY, wallet_id VARCHAR(255) NOT NULL, " +
                                "credential_data TEXT NOT NULL, archived BOOLEAN DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                        )
                        s.execute(
                            "CREATE TABLE collections (id VARCHAR(255) PRIMARY KEY, wallet_id VARCHAR(255) NOT NULL, " +
                                "name VARCHAR(255) NOT NULL, description TEXT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                        )
                        s.execute(
                            "CREATE TABLE credential_collections (credential_id VARCHAR(255) NOT NULL, " +
                                "collection_id VARCHAR(255) NOT NULL, PRIMARY KEY (credential_id, collection_id), " +
                                "FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE, " +
                                "FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE)",
                        )
                        s.execute(
                            "CREATE TABLE credential_tags (credential_id VARCHAR(255) NOT NULL, tag VARCHAR(255) NOT NULL, " +
                                "PRIMARY KEY (credential_id, tag), FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE)",
                        )
                        s.execute(
                            "CREATE TABLE credential_metadata (credential_id VARCHAR(255) PRIMARY KEY, notes TEXT, metadata_json TEXT, " +
                                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                                "FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE)",
                        )
                    }
                    c.prepareStatement("INSERT INTO credentials (id, wallet_id, credential_data) VALUES ('urn:old', 'legacy', ?)").use {
                        it.setString(1, legacyCredential)
                        it.executeUpdate()
                    }
                    c.createStatement().use { s ->
                        s.execute("INSERT INTO collections (id, wallet_id, name) VALUES ('col-1', 'legacy', 'Old')")
                        s.execute("INSERT INTO credential_collections VALUES ('urn:old', 'col-1')")
                        s.execute("INSERT INTO credential_tags VALUES ('urn:old', 'keep')")
                        s.execute("INSERT INTO credential_metadata (credential_id, notes) VALUES ('urn:old', 'note')")
                    }
                }

                repeat(2) {
                    // second pass proves idempotence
                    val legacy = wallet(source, "legacy")
                    assertNotNull(legacy.get("urn:old"))
                    assertEquals(setOf("keep"), legacy.getTags("urn:old"))
                    assertEquals("note", legacy.getMetadata("urn:old")!!.notes)
                    assertEquals(1, legacy.getCredentialsInCollection("col-1").size)
                    assertEquals("2", scalar(source, "SELECT MAX(version) FROM schema_version"))
                }
                val other = wallet(source, "other")
                other.store(credential("urn:old", "Other"))
                assertEquals(
                    "Other",
                    other
                        .get("urn:old")!!
                        .type
                        .first()
                        .value,
                )
                assertNotNull(wallet(source, "legacy").get("urn:old"))
                assertEquals(
                    0,
                    scalar(source, "SELECT COUNT(*) FROM information_schema.tables WHERE table_name LIKE '%\\_V2' ESCAPE '\\'")!!.toInt(),
                )
                seed.close()
            }
        }
}
