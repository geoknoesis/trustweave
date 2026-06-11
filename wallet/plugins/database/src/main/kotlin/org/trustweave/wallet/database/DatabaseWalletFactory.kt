package org.trustweave.wallet.database

import org.trustweave.wallet.services.WalletFactory
import org.trustweave.wallet.services.WalletCreationOptions
import org.trustweave.wallet.Wallet
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID

/**
 * Database-backed wallet factory implementation.
 *
 * Supports PostgreSQL and H2. MySQL is NOT supported — see [DatabaseWallet]
 * for details.
 *
 * Each [create] call builds a dedicated HikariCP connection pool that is owned
 * by the returned wallet. Call [DatabaseWallet.close] (or use Kotlin's `use {}`)
 * when the wallet is no longer needed, otherwise the pool's connections leak.
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
 * try {
 *     wallet.store(credential)
 * } finally {
 *     wallet.close() // shuts down the wallet-owned connection pool
 * }
 * ```
 */
class DatabaseWalletFactory : WalletFactory {

    override suspend fun create(
        providerName: String,
        walletId: String?,
        walletDid: String?,
        holderDid: String?,
        options: WalletCreationOptions
    ): Wallet {
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

        try {
            // ownsDataSource = true: the pool was created here exclusively for this
            // wallet, so DatabaseWallet.close() is responsible for shutting it down.
            return DatabaseWallet.create(
                walletId = finalWalletId,
                walletDid = finalWalletDid,
                holderDid = finalHolderDid,
                dataSource = dataSource,
                ownsDataSource = true
            )
        } catch (e: Throwable) {
            // The wallet was never handed to the caller — close the pool here or it leaks.
            runCatching { dataSource.close() }
            throw e
        }
    }

    private fun createDataSource(
        connectionString: String,
        username: String?,
        password: String?
    ): HikariDataSource {
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

