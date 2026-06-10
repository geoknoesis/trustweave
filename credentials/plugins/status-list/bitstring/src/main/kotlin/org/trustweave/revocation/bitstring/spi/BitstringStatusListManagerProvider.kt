package org.trustweave.revocation.bitstring.spi

import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.kms.KeyManagementService
import org.trustweave.revocation.bitstring.BitstringStatusListManagerFactory
import org.trustweave.revocation.services.StatusListRegistryFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * SPI provider for the W3C Bitstring Status List implementation.
 *
 * Discovered automatically via Java [java.util.ServiceLoader] when this module is on
 * the classpath. The provider name is `"bitstring"`.
 *
 * **ServiceLoader registration:**
 * `META-INF/services/org.trustweave.revocation.services.StatusListRegistryFactory`
 *
 * Callers must inject a [KeyManagementService] before calling [create], or set the
 * `trustweave.statuslist.jdbc.*` system properties. The [kms] property can be set
 * programmatically after construction.
 *
 * Note: managers created via this provider have no proof engine / issuer signing key
 * configured, so they support status tracking (assign/revoke/check) but
 * `buildStatusListVc` fails closed with a ConfigException. To publish signed status
 * list credentials, construct the manager via [BitstringStatusListManagerFactory]
 * with a `proofEngine` and `issuerKeyId`.
 */
class BitstringStatusListManagerProvider : StatusListRegistryFactory {

    companion object {
        const val PROVIDER_NAME = "bitstring"
    }

    /**
     * Injected [KeyManagementService]. Must be set before calling [create].
     *
     * If not set, [create] will throw [IllegalStateException].
     */
    var kms: KeyManagementService? = null

    /**
     * Create a [org.trustweave.revocation.bitstring.BitstringStatusListManager].
     *
     * System properties consumed:
     * - `trustweave.statuslist.jdbc.url` (default: H2 in-memory)
     * - `trustweave.statuslist.jdbc.username` (default: `sa`)
     * - `trustweave.statuslist.jdbc.password` (default: empty)
     * - `trustweave.statuslist.issuer.did` (default: `did:key:default`)
     *
     * @param providerName Must be `"bitstring"`
     * @throws IllegalStateException if [kms] has not been set
     */
    override suspend fun create(providerName: String): CredentialRevocationManager {
        check(providerName == PROVIDER_NAME) {
            "BitstringStatusListManagerProvider does not support provider '$providerName'. Expected '$PROVIDER_NAME'."
        }
        val resolvedKms = checkNotNull(kms) {
            "BitstringStatusListManagerProvider: kms must be set before calling create()."
        }

        val jdbcUrl = System.getProperty("trustweave.statuslist.jdbc.url")
            ?: "jdbc:h2:mem:bitstring_status;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        val username = System.getProperty("trustweave.statuslist.jdbc.username") ?: "sa"
        val password = System.getProperty("trustweave.statuslist.jdbc.password") ?: ""
        val issuerDid = System.getProperty("trustweave.statuslist.issuer.did") ?: "did:key:default"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            maximumPoolSize = 5
        }
        val dataSource = HikariDataSource(config)

        return BitstringStatusListManagerFactory.create(
            dataSource = dataSource,
            kms = resolvedKms,
            issuerDid = issuerDid
        )
    }
}
