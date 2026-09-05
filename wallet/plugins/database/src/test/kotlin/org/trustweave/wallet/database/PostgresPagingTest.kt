package org.trustweave.wallet.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.PostgreSQLContainer
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.identifiers.Did
import org.trustweave.wallet.CredentialFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresPagingTest {
    @Test
    fun `ten thousand records page completely with selective filtering and wallet isolation`() =
        runBlocking {
            val database = PostgreSQLContainer<Nothing>("postgres:15")
            database.start()
            try {
                HikariDataSource(
                    HikariConfig().apply {
                        jdbcUrl = database.jdbcUrl
                        username = database.username
                        password = database.password
                    },
                ).use { source ->
                    val wallet = DatabaseWallet.create("load-wallet", "did:key:w", "did:key:h", source)
                    wallet.store(
                        VerifiableCredential(
                            id = CredentialId("seed"),
                            type = listOf(CredentialType.Custom("Ordinary")),
                            issuer = Issuer.fromDid(Did("did:key:issuer")),
                            credentialSubject = CredentialSubject.fromIri("did:key:holder"),
                        ),
                    )
                    source.connection.use { connection ->
                        connection.createStatement().use { statement ->
                            statement.executeUpdate(
                                """
                                INSERT INTO credentials (id, wallet_id, credential_data, archived)
                                SELECT 'load-' || lpad(i::text, 5, '0'), 'load-wallet',
                                jsonb_set(jsonb_set(credential_data::jsonb, '{id}',
                                to_jsonb('load-' || lpad(i::text, 5, '0'))), '{type}',
                                CASE WHEN i % 1000 = 0 THEN '["Rare"]'::jsonb ELSE '["Ordinary"]'::jsonb END)::text, FALSE
                                FROM credentials CROSS JOIN generate_series(1, 10000) AS i WHERE id = 'seed'
                                """.trimIndent(),
                            )
                            statement.executeUpdate("DELETE FROM credentials WHERE id = 'seed'")
                            statement.execute("ANALYZE credentials")
                        }
                    }
                    val started = System.nanoTime()
                    val seen = mutableSetOf<String>()
                    var cursor: String? = null
                    var pages = 0
                    do {
                        val page = wallet.pageRecords(50, cursor)
                        assertTrue(page.records.size <= 50)
                        page.records.forEach { assertTrue(seen.add(it.storageId), "Duplicate record ${it.storageId}") }
                        cursor = page.nextCursor
                        pages++
                        assertTrue(pages <= 201, "Cursor failed to make progress")
                    } while (cursor != null)
                    assertEquals(10000, seen.size)
                    assertEquals(200, pages)
                    val rare = wallet.pageRecords(50, filter = CredentialFilter(type = listOf("Rare")))
                    assertEquals(10, rare.records.size)
                    assertNull(rare.nextCursor)
                    val other = DatabaseWallet.create("other-wallet", "did:key:o", "did:key:o", source)
                    assertTrue(other.pageRecords(50).records.isEmpty())
                    println("POSTGRES_PAGING records=10000 pages=$pages pageSize=50 elapsedMs=${(System.nanoTime() - started) / 1000000}")
                }
            } finally {
                database.stop()
            }
        }

    @Test
    fun `PostgreSQL filters before pagination and has an eligible containment index`() =
        runBlocking {
            val database = PostgreSQLContainer<Nothing>("postgres:15")
            database.start()
            try {
                HikariDataSource(
                    HikariConfig().apply {
                        jdbcUrl = database.jdbcUrl
                        username = database.username
                        password = database.password
                    },
                ).use { source ->
                    val wallet = DatabaseWallet.create("page-test", "did:key:w", "did:key:h", source)
                    repeat(120) { index ->
                        wallet.store(
                            VerifiableCredential(
                                id = CredentialId("record-%03d".format(index)),
                                type = listOf(CredentialType.Custom(if (index == 119) "Rare" else "Ordinary")),
                                issuer = Issuer.fromDid(Did("did:key:issuer")),
                                credentialSubject = CredentialSubject.fromIri("did:key:holder"),
                            ),
                        )
                    }
                    val page = wallet.pageRecords(2, filter = CredentialFilter(type = listOf("Rare"), issuer = "did:key:issuer"))
                    assertEquals("record-119", page.records.single().storageId)
                    assertNull(page.nextCursor)
                    source.connection.use { connection ->
                        connection.createStatement().use { statement ->
                            statement.execute("SET enable_seqscan = off")
                            statement
                                .executeQuery(
                                    "EXPLAIN SELECT id FROM credentials WHERE CAST(credential_data AS jsonb) @> '{\"type\":[\"Rare\"]}'::jsonb",
                                ).use { rows ->
                                    val plan = buildString { while (rows.next()) append(rows.getString(1)) }
                                    assertTrue(plan.contains("idx_credentials_json_search"), plan)
                                }
                        }
                    }
                }
            } finally {
                database.stop()
            }
        }
}
