package com.trustweave.wallet.database

import com.trustweave.wallet.services.WalletFactory
import com.trustweave.wallet.services.WalletCreationOptions
import com.trustweave.wallet.Wallet
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import javax.sql.DataSource

/**
 * Database-backed wallet factory implementation.
 * 
 * Supports PostgreSQL, MySQL, and H2 databases.
 * 
 * **Example:**
 * ```kotlin
 * val factory = DatabaseWalletFactory()
 * val wallet = factory.create(
 *     providerName = "database",
 *     holderDid = "did:key:holder",
 *     options = WalletCreationOptions(
 *         storagePath = "jdbc:postgresql://localhost:5432/TrustWeave",
 *         additionalProperties = mapOf(
 *             "username" to "user",
 *             "password" to "pass"
 *         )
 *     )
 * )
 * ```
 */
class DatabaseWalletFactory : WalletFactory {
    
    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions
    ): Any {
        if (providerName.lowercase() != "database") {
            throw IllegalArgumentException("Provider name must be 'database'")
        }
        
        val finalWalletId = walletId ?: UUID.randomUUID().toString()
        val finalWalletDid = walletDid ?: "did:key:wallet-$finalWalletId"
        val finalHolderDid = holderDid 
            ?: throw IllegalArgumentException("holderDid is required for DatabaseWallet")
        
        val connectionString = options.storagePath
            ?: throw IllegalArgumentException("storagePath (JDBC connection string) is required")
        
        val username = options.additionalProperties["username"] as? String
        val password = options.additionalProperties["password"] as? String
        
        val dataSource = createDataSource(connectionString, username, password)
        
        return DatabaseWallet(
            walletId = finalWalletId,
            walletDid = finalWalletDid,
            holderDid = finalHolderDid,
            dataSource = dataSource
        )
    }
    
    private fun createDataSource(
        connectionString: String,
        username: String?,
        password: String?
    ): DataSource {
        val config = HikariConfig()
        config.jdbcUrl = connectionString
        username?.let { config.username = it }
        password?.let { config.password = it }
        config.maximumPoolSize = 10
        config.minimumIdle = 2
        config.connectionTimeout = 30000
        config.idleTimeout = 600000
        config.maxLifetime = 1800000
        
        return HikariDataSource(config)
    }
}

