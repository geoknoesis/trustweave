package org.trustweave.registry.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.trustweave.registry.TrustRegistry
import org.trustweave.registry.TrustRegistryProvider

class DatabaseTrustRegistryProvider : TrustRegistryProvider {
    override val name = "database"

    override fun create(config: Map<String, Any?>): TrustRegistry {
        val jdbcUrl = config["jdbcUrl"] as? String
            ?: "jdbc:h2:mem:trust_registry;DB_CLOSE_DELAY=-1"
        val hikari = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = config["username"] as? String ?: "sa"
            password = config["password"] as? String ?: ""
            maximumPoolSize = (config["poolSize"] as? Int) ?: 5
        }
        return DatabaseTrustRegistry(HikariDataSource(hikari))
    }
}
