package org.trustweave.registry.database

import org.trustweave.registry.TrustRegistry
import javax.sql.DataSource

object DatabaseTrustRegistryFactory {
    fun create(dataSource: DataSource): DatabaseTrustRegistry =
        DatabaseTrustRegistry(dataSource)
}
