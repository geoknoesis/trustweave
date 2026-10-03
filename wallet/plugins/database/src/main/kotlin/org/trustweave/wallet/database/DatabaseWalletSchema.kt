package org.trustweave.wallet.database

import java.sql.Connection

/**
 * DDL for the [DatabaseWallet] tables (credentials, collections, tags, metadata) and the
 * dialect probe the wallet's upserts depend on. Extracted from [DatabaseWallet] unchanged.
 */
internal object DatabaseWalletSchema {
    /**
     * Creates the wallet tables and indexes if they do not exist, in one transaction on [conn].
     */
    fun initialize(conn: Connection) {
        val savedAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            // Credentials table
            conn
                .prepareStatement(
                    """
                CREATE TABLE IF NOT EXISTS credentials (
                    id VARCHAR(255) PRIMARY KEY,
                    wallet_id VARCHAR(255) NOT NULL,
                    credential_data TEXT NOT NULL,
                    archived BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """,
                ).use { it.execute() }
            conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_credentials_wallet_id ON credentials(wallet_id)").use { it.execute() }
            conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_credentials_archived ON credentials(archived)").use { it.execute() }

            conn
                .prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_credentials_cursor ON credentials(wallet_id, archived, id)",
                ).use { it.execute() }
            if (isPostgreSql(conn)) {
                conn
                    .prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_credentials_json_search ON credentials USING GIN ((CAST(credential_data AS jsonb)) jsonb_path_ops)",
                    ).use {
                        it.execute()
                    }
            }

            // Collections table
            conn
                .prepareStatement(
                    """
                CREATE TABLE IF NOT EXISTS collections (
                    id VARCHAR(255) PRIMARY KEY,
                    wallet_id VARCHAR(255) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    description TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """,
                ).use { it.execute() }
            conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_collections_wallet_id ON collections(wallet_id)").use { it.execute() }

            // Credential collections junction table
            conn
                .prepareStatement(
                    """
                CREATE TABLE IF NOT EXISTS credential_collections (
                    credential_id VARCHAR(255) NOT NULL,
                    collection_id VARCHAR(255) NOT NULL,
                    PRIMARY KEY (credential_id, collection_id),
                    FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE,
                    FOREIGN KEY (collection_id) REFERENCES collections(id) ON DELETE CASCADE
                )
            """,
                ).use { it.execute() }
            conn
                .prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_cred_collections_credential_id ON credential_collections(credential_id)",
                ).use {
                    it.execute()
                }
            conn
                .prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_cred_collections_collection_id ON credential_collections(collection_id)",
                ).use {
                    it.execute()
                }

            // Tags table
            conn
                .prepareStatement(
                    """
                CREATE TABLE IF NOT EXISTS credential_tags (
                    credential_id VARCHAR(255) NOT NULL,
                    tag VARCHAR(255) NOT NULL,
                    PRIMARY KEY (credential_id, tag),
                    FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE
                )
            """,
                ).use { it.execute() }
            conn
                .prepareStatement(
                    "CREATE INDEX IF NOT EXISTS idx_credential_tags_credential_id ON credential_tags(credential_id)",
                ).use { it.execute() }
            conn.prepareStatement("CREATE INDEX IF NOT EXISTS idx_credential_tags_tag ON credential_tags(tag)").use { it.execute() }

            // Metadata table
            conn
                .prepareStatement(
                    """
                CREATE TABLE IF NOT EXISTS credential_metadata (
                    credential_id VARCHAR(255) PRIMARY KEY,
                    notes TEXT,
                    metadata_json TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (credential_id) REFERENCES credentials(id) ON DELETE CASCADE
                )
            """,
                ).use { it.execute() }

            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = savedAutoCommit
        }
    }

    /**
     * Dialect probe for upsert statements. PostgreSQL gets `ON CONFLICT … DO UPDATE`;
     * everything else (H2 in particular) gets standard SQL `MERGE`.
     */
    fun isPostgreSql(conn: Connection): Boolean = conn.metaData.databaseProductName?.contains("PostgreSQL", ignoreCase = true) == true
}
