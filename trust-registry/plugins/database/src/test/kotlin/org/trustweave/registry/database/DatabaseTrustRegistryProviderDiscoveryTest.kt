package org.trustweave.registry.database

import org.junit.jupiter.api.Test
import org.trustweave.registry.TrustRegistryProvider
import java.util.ServiceLoader
import kotlin.test.assertTrue

/**
 * This provider is only ever reached through [ServiceLoader], so whether it loads is a property of
 * the `META-INF/services` file rather than of any code a normal test would exercise.
 *
 * That file carried a UTF-8 byte-order mark. `ServiceLoader` reads service files as UTF-8 but does
 * not strip a BOM, so the first line parsed as `org.trustweave.registry.database…` and the
 * class was never found — the provider simply was not there, and nothing else in the suite noticed
 * because nothing else goes through discovery.
 */
class DatabaseTrustRegistryProviderDiscoveryTest {
    @Test
    fun `the provider is discoverable through ServiceLoader`() {
        val discovered =
            ServiceLoader
                .load(TrustRegistryProvider::class.java)
                .toList()
                .map { it::class.java.name }

        assertTrue(
            discovered.any { it == DatabaseTrustRegistryProvider::class.java.name },
            "DatabaseTrustRegistryProvider must be discoverable; found: $discovered",
        )
    }
}
