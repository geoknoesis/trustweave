package org.trustweave.credential.vi.verification

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.sql.Connection
import java.util.Base64
import javax.sql.DataSource

internal fun verifyLedgerIntegrity(
    source: DataSource,
    expected: String?,
): String {
    require(expected == null || expected.matches(Regex("vi-ledger-v1:[A-Za-z0-9_-]{43}"))) { "Invalid checkpoint format" }
    return source.connection.use { connection ->
        connection.isReadOnly = true
        connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        connection.autoCommit = false
        try {
            connection.createStatement().use { statement ->
                statement.queryTimeout = 60
                // A filtered view can hide corruption and produce a misleading checkpoint.
                statement.execute("SET LOCAL row_security = off")
                statement
                    .executeQuery(
                        """
                        SELECT EXISTS (
                          SELECT 1 FROM vi_budget_accounts a LEFT JOIN
                            (SELECT scope,count(*) AS occurrences,
                             coalesce(sum(amount) FILTER (WHERE settlement_state <> 'RELEASED'),0) AS spending
                             FROM vi_budget_reservations GROUP BY scope) r USING(scope)
                          WHERE a.spent <> coalesce(r.spending,0) OR a.occurrence_count < coalesce(r.occurrences,0)
                             OR a.spent < 0 OR a.maximum < a.spent OR a.occurrence_count < 0
                             OR a.currency !~ '^[A-Z]{3}${'$'}'
                             OR a.scope IS NULL OR a.currency IS NULL OR a.maximum IS NULL
                             OR a.spent IS NULL OR a.occurrence_count IS NULL
                          UNION ALL
                          SELECT 1 FROM vi_budget_reservations r LEFT JOIN vi_budget_accounts a USING(scope)
                          WHERE a.scope IS NULL OR r.amount < 0
                             OR r.transaction_hash IS NULL OR r.challenge_hash IS NULL
                             OR r.amount IS NULL OR r.settlement_state IS NULL
                             OR r.settlement_state NOT IN ('RESERVED','SETTLED','RELEASED')
                             OR (r.settlement_state='RESERVED' AND r.evidence_hash IS NOT NULL)
                             OR (r.settlement_state<>'RESERVED' AND
                                 (r.evidence_hash IS NULL OR r.evidence_hash !~ '^[A-Za-z0-9_-]{43}${'$'}'))
                        )
                        """.trimIndent(),
                    ).use { rows ->
                        check(rows.next() && !rows.getBoolean(1)) { "Ledger integrity verification failed" }
                    }
            }
            val digest = MessageDigest.getInstance("SHA-256")

            fun field(value: String?) {
                val bytes = value?.toByteArray(Charsets.UTF_8)
                digest.update(ByteBuffer.allocate(4).putInt(bytes?.size ?: -1).array())
                if (bytes != null) digest.update(bytes)
            }
            field("vi-ledger-v1")
            listOf(
                "SELECT scope,currency,maximum,spent,occurrence_count FROM vi_budget_accounts ORDER BY scope COLLATE \"C\"",
                "SELECT scope,transaction_hash,challenge_hash,amount,settlement_state,evidence_hash," +
                    "extract(epoch FROM created_at)::numeric(30,6) FROM vi_budget_reservations " +
                    "ORDER BY scope COLLATE \"C\",transaction_hash COLLATE \"C\"",
            ).forEachIndexed { table, query ->
                field("table:$table")
                connection.prepareStatement(query).use { statement ->
                    statement.queryTimeout = 60
                    statement.fetchSize = 256
                    statement.executeQuery().use { rows ->
                        val columns = rows.metaData.columnCount
                        while (rows.next()) {
                            field("row")
                            for (column in 1..columns) field(rows.getString(column))
                        }
                    }
                }
            }
            val result = "vi-ledger-v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
            check(expected == null || MessageDigest.isEqual(result.toByteArray(), expected.toByteArray())) {
                "Ledger checkpoint mismatch"
            }
            connection.commit()
            result
        } catch (failure: Throwable) {
            try {
                connection.rollback()
            } catch (rollback: Throwable) {
                failure.addSuppressed(rollback)
            }
            throw failure
        }
    }
}
