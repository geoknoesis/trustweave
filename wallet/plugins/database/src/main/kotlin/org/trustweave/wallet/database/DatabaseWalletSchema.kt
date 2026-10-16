package org.trustweave.wallet.database

import java.sql.Connection

/**
 * DDL for the [DatabaseWallet] tables (credentials, collections, tags, metadata) and the
 * dialect probe the wallet's upserts depend on.
 *
 * **Schema versions** (recorded in `schema_version`):
 * - 1 (implicit, no `schema_version` table): `credentials.id` and `collections.id` were the sole
 *   primary keys, so two wallets sharing a database collided on credential ids.
 * - 2: `credentials` and `collections` are keyed by `(wallet_id, id)` and every child table
 *   (`credential_collections`, `credential_tags`, `credential_metadata`) carries `wallet_id`
 *   with composite foreign keys, so a row can only ever reference its own wallet's parent.
 *
 * [initialize] is idempotent and migrates a version-1 database in place (copy into `*_v2`
 * tables, drop the old ones, rename). PostgreSQL runs it in one transaction under an advisory
 * lock; H2 commits DDL implicitly, so the migration is written to be resumable after a crash.
 * Memberships/tags/metadata whose credential and collection belonged to different wallets (only
 * possible by hand-editing) are not carried over.
 */
internal object DatabaseWalletSchema {
    const val CURRENT_VERSION = 2
    private const val MIGRATION_LOCK_KEY = 7243180L

    /** Child tables first, parents last (drop order). */
    private val TABLES = listOf("credential_metadata", "credential_tags", "credential_collections", "collections", "credentials")

    /**
     * Creates or migrates the wallet tables and indexes, in one transaction on [conn] where the
     * dialect allows it.
     */
    fun initialize(conn: Connection) {
        val savedAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            if (isPostgreSql(conn)) {
                conn.prepareStatement("SELECT pg_advisory_xact_lock($MIGRATION_LOCK_KEY)").use { it.execute() }
            }
            execute(
                conn,
                "CREATE TABLE IF NOT EXISTS schema_version (" +
                    "version INT NOT NULL PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
            )
            val current = currentVersion(conn)
            if (current < CURRENT_VERSION) {
                val legacy = tableExists(conn, "credentials")
                val resumable = !legacy && TABLES.any { tableExists(conn, "${it}_v2") }
                when {
                    legacy -> migrateFromV1(conn)
                    resumable -> swapInMigratedTables(conn)
                    else -> createTables(conn, "")
                }
                createIndexes(conn)
                execute(conn, "INSERT INTO schema_version (version) VALUES ($CURRENT_VERSION)")
            } else {
                createTables(conn, "")
                createIndexes(conn)
            }
            conn.commit()
        } catch (e: Exception) {
            runCatching { conn.rollback() }
            throw e
        } finally {
            conn.autoCommit = savedAutoCommit
        }
    }

    private fun currentVersion(conn: Connection): Int =
        conn.prepareStatement("SELECT MAX(version) FROM schema_version").use { stmt ->
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    private fun execute(
        conn: Connection,
        sql: String,
    ) = conn.prepareStatement(sql).use { it.execute() }

    private fun tableExists(
        conn: Connection,
        name: String,
    ): Boolean =
        listOf(name.lowercase(), name.uppercase()).any { candidate ->
            conn.metaData.getTables(conn.catalog, conn.schema, candidate, arrayOf("TABLE")).use { it.next() }
        }

    /** Creates the version-2 tables with names suffixed by [sfx] ("" for the live tables). */
    private fun createTables(
        conn: Connection,
        sfx: String,
    ) {
        execute(
            conn,
            """
            CREATE TABLE IF NOT EXISTS credentials$sfx (
                id VARCHAR(255) NOT NULL,
                wallet_id VARCHAR(255) NOT NULL,
                credential_data TEXT NOT NULL,
                archived BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (wallet_id, id)
            )
            """,
        )
        execute(
            conn,
            """
            CREATE TABLE IF NOT EXISTS collections$sfx (
                id VARCHAR(255) NOT NULL,
                wallet_id VARCHAR(255) NOT NULL,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (wallet_id, id)
            )
            """,
        )
        execute(
            conn,
            """
            CREATE TABLE IF NOT EXISTS credential_collections$sfx (
                wallet_id VARCHAR(255) NOT NULL,
                credential_id VARCHAR(255) NOT NULL,
                collection_id VARCHAR(255) NOT NULL,
                PRIMARY KEY (wallet_id, credential_id, collection_id),
                FOREIGN KEY (wallet_id, credential_id) REFERENCES credentials$sfx(wallet_id, id) ON DELETE CASCADE,
                FOREIGN KEY (wallet_id, collection_id) REFERENCES collections$sfx(wallet_id, id) ON DELETE CASCADE
            )
            """,
        )
        execute(
            conn,
            """
            CREATE TABLE IF NOT EXISTS credential_tags$sfx (
                wallet_id VARCHAR(255) NOT NULL,
                credential_id VARCHAR(255) NOT NULL,
                tag VARCHAR(255) NOT NULL,
                PRIMARY KEY (wallet_id, credential_id, tag),
                FOREIGN KEY (wallet_id, credential_id) REFERENCES credentials$sfx(wallet_id, id) ON DELETE CASCADE
            )
            """,
        )
        execute(
            conn,
            """
            CREATE TABLE IF NOT EXISTS credential_metadata$sfx (
                wallet_id VARCHAR(255) NOT NULL,
                credential_id VARCHAR(255) NOT NULL,
                notes TEXT,
                metadata_json TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (wallet_id, credential_id),
                FOREIGN KEY (wallet_id, credential_id) REFERENCES credentials$sfx(wallet_id, id) ON DELETE CASCADE
            )
            """,
        )
    }

    private fun migrateFromV1(conn: Connection) {
        // A crashed earlier attempt may have left partial copies: start the copy from scratch.
        TABLES.forEach { execute(conn, "DROP TABLE IF EXISTS ${it}_v2") }
        createTables(conn, "_v2")
        execute(
            conn,
            "INSERT INTO credentials_v2 (id, wallet_id, credential_data, archived, created_at) " +
                "SELECT id, wallet_id, credential_data, archived, created_at FROM credentials",
        )
        execute(
            conn,
            "INSERT INTO collections_v2 (id, wallet_id, name, description, created_at) " +
                "SELECT id, wallet_id, name, description, created_at FROM collections",
        )
        execute(
            conn,
            "INSERT INTO credential_collections_v2 (wallet_id, credential_id, collection_id) " +
                "SELECT c.wallet_id, cc.credential_id, cc.collection_id FROM credential_collections cc " +
                "JOIN credentials c ON c.id = cc.credential_id " +
                "JOIN collections col ON col.id = cc.collection_id AND col.wallet_id = c.wallet_id",
        )
        execute(
            conn,
            "INSERT INTO credential_tags_v2 (wallet_id, credential_id, tag) " +
                "SELECT c.wallet_id, t.credential_id, t.tag FROM credential_tags t JOIN credentials c ON c.id = t.credential_id",
        )
        execute(
            conn,
            "INSERT INTO credential_metadata_v2 (wallet_id, credential_id, notes, metadata_json, created_at, updated_at) " +
                "SELECT c.wallet_id, m.credential_id, m.notes, m.metadata_json, m.created_at, m.updated_at " +
                "FROM credential_metadata m JOIN credentials c ON c.id = m.credential_id",
        )
        swapInMigratedTables(conn)
    }

    /** Drops any remaining version-1 tables and renames each `*_v2` table to its live name. */
    private fun swapInMigratedTables(conn: Connection) {
        TABLES.forEach { execute(conn, "DROP TABLE IF EXISTS $it") }
        TABLES.reversed().forEach { if (tableExists(conn, "${it}_v2")) execute(conn, "ALTER TABLE ${it}_v2 RENAME TO $it") }
    }

    private fun createIndexes(conn: Connection) {
        execute(conn, "CREATE INDEX IF NOT EXISTS idx_credentials_archived ON credentials(archived)")
        execute(conn, "CREATE INDEX IF NOT EXISTS idx_credentials_cursor ON credentials(wallet_id, archived, id)")
        if (isPostgreSql(conn)) {
            execute(
                conn,
                "CREATE INDEX IF NOT EXISTS idx_credentials_json_search ON credentials USING GIN ((CAST(credential_data AS jsonb)) jsonb_path_ops)",
            )
        }
        execute(conn, "CREATE INDEX IF NOT EXISTS idx_collections_wallet_id ON collections(wallet_id)")
        execute(
            conn,
            "CREATE INDEX IF NOT EXISTS idx_cred_collections_collection_id ON credential_collections(wallet_id, collection_id)",
        )
        execute(conn, "CREATE INDEX IF NOT EXISTS idx_credential_tags_tag ON credential_tags(wallet_id, tag)")
    }

    /**
     * Dialect probe for upsert statements. PostgreSQL gets `ON CONFLICT … DO UPDATE`;
     * everything else (H2 in particular) gets standard SQL `MERGE`.
     */
    fun isPostgreSql(conn: Connection): Boolean = conn.metaData.databaseProductName?.contains("PostgreSQL", ignoreCase = true) == true
}
