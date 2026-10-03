package org.trustweave.wallet.database

import org.junit.jupiter.api.Test
import org.trustweave.wallet.CredentialFilter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** SQL construction extracted from DatabaseWallet; needs no database. */
class DatabaseWalletQueriesTest {
    @Test
    fun `tag and collection query has one placeholder per value and the HAVING counts`() {
        val sql = buildTagCollectionQuerySql(listOf("a", "b"), listOf("c1"))
        assertEquals(1 + 2 + 1 + 1 + 1, sql.count { it == '?' }, "wallet + tags + count + collections + count")
        assertTrue(sql.startsWith("SELECT credential_data FROM credentials WHERE wallet_id = ? AND archived = FALSE"))
        assertTrue(sql.endsWith(" ORDER BY id"))
        assertEquals(
            "SELECT credential_data FROM credentials WHERE wallet_id = ? AND archived = FALSE ORDER BY id",
            buildTagCollectionQuerySql(emptyList(), emptyList()),
        )
    }

    @Test
    fun `page query adds the cursor and, on PostgreSQL only, JSON containment for issuer type and subject`() {
        val filter = CredentialFilter(issuer = "did:key:i", type = listOf("T1", "T2"), subjectId = "did:key:s")

        val h2 = buildPageQuery("cursor-1", filter, postgres = false)
        assertEquals(" AND id > ?", h2.extraSql)
        assertEquals(listOf("cursor-1"), h2.values)

        val pg = buildPageQuery(null, filter, postgres = true)
        assertEquals(5, Regex("@>").findAll(pg.extraSql).count(), "2 issuer shapes + 2 types + 1 subject")
        assertEquals(2 + 2 + 1, pg.values.size, "issuer has 2 shapes, each type its own, subject 1")
        assertTrue(pg.extraSql.contains("@>"))

        assertEquals("", buildPageQuery(null, null, postgres = true).extraSql)
    }
}
