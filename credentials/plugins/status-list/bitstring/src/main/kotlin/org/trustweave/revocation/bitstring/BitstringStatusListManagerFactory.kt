package org.trustweave.revocation.bitstring

import org.trustweave.kms.KeyManagementService
import javax.sql.DataSource

/**
 * Factory for creating [BitstringStatusListManager] instances.
 *
 * **Example:**
 * ```kotlin
 * val manager = BitstringStatusListManagerFactory.create(
 *     dataSource = hikariDataSource,
 *     kms = keyManagementService,
 *     issuerDid = "did:key:z6Mk...",
 *     bitsPerEntry = 1
 * )
 * ```
 */
object BitstringStatusListManagerFactory {

    /**
     * Create a new [BitstringStatusListManager].
     *
     * @param dataSource JDBC [DataSource] (HikariCP recommended)
     * @param kms [KeyManagementService] used to sign status list VCs
     * @param issuerDid DID of the issuer used in status list VCs
     * @param bitsPerEntry 1 for single-purpose lists, 2 for combined revocation + suspension
     * @return Configured [BitstringStatusListManager]
     */
    fun create(
        dataSource: DataSource,
        kms: KeyManagementService,
        issuerDid: String,
        bitsPerEntry: Int = 1
    ): BitstringStatusListManager = BitstringStatusListManager(
        dataSource = dataSource,
        kms = kms,
        issuerDid = issuerDid,
        bitsPerEntry = bitsPerEntry
    )
}
