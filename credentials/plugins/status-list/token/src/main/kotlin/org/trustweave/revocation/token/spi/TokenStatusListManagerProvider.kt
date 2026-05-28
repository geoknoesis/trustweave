package org.trustweave.revocation.token.spi

import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.kms.KeyManagementService
import org.trustweave.revocation.services.StatusListRegistryFactory
import org.trustweave.revocation.token.TokenStatusListManagerFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * SPI provider for the IETF Token Status List implementation.
 *
 * Discovered automatically via Java [java.util.ServiceLoader] when this module is on
 * the classpath. The provider name is `"token"`.
 *
 * **ServiceLoader registration:**
 * `META-INF/services/org.trustweave.revocation.services.StatusListRegistryFactory`
 *
 * Callers must inject a [KeyManagementService] via [kms] before calling [create].
 *
 * System properties consumed (fall back to H2 in-memory defaults):
 * - `trustweave.statuslist.jdbc.url`
 * - `trustweave.statuslist.jdbc.username`
 * - `trustweave.statuslist.jdbc.password`
 * - `trustweave.statuslist.issuer.did`
 * - `trustweave.statuslist.token.uri`
 */
class TokenStatusListManagerProvider : StatusListRegistryFactory {

    companion object {
        const val PROVIDER_NAME = "token"
    }

    /**
     * Injected [KeyManagementService]. Must be set before calling [create].
     */
    var kms: KeyManagementService? = null

    /**
     * Create a [org.trustweave.revocation.token.TokenStatusListManager].
     *
     * @param providerName Must be `"token"`
     * @throws IllegalStateException if [kms] has not been set
     */
    override suspend fun create(providerName: String): CredentialRevocationManager {
        check(providerName == PROVIDER_NAME) {
            "TokenStatusListManagerProvider does not support provider '$providerName'. Expected '$PROVIDER_NAME'."
        }
        val resolvedKms = checkNotNull(kms) {
            "TokenStatusListManagerProvider: kms must be set before calling create()."
        }

        val jdbcUrl = System.getProperty("trustweave.statuslist.jdbc.url")
            ?: "jdbc:h2:mem:token_status;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        val username = System.getProperty("trustweave.statuslist.jdbc.username") ?: "sa"
        val password = System.getProperty("trustweave.statuslist.jdbc.password") ?: ""
        val issuerDid = System.getProperty("trustweave.statuslist.issuer.did") ?: "did:key:default"
        val statusListUri = System.getProperty("trustweave.statuslist.token.uri")
            ?: "https://example.com/statuslists/default"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            maximumPoolSize = 5
        }
        val dataSource = HikariDataSource(config)

        return TokenStatusListManagerFactory.create(
            dataSource = dataSource,
            kms = resolvedKms,
            issuerDid = issuerDid,
            statusListUri = statusListUri
        )
    }
}
