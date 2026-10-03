package org.trustweave.revocation.token

import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.kms.KeyManagementService
import javax.sql.DataSource

/**
 * Factory for creating [TokenStatusListManager] instances.
 *
 * **Example:**
 * ```kotlin
 * val manager = TokenStatusListManagerFactory.create(
 *     dataSource = hikariDataSource,
 *     kms = keyManagementService,
 *     issuerDid = "did:key:z6Mk...",
 *     statusListUri = "https://example.com/status/1",
 *     bitsPerEntry = 1,
 *     issuerKeyId = VerificationMethodId.parse("did:key:z6Mk...#key-1") // signs the tokens
 * )
 * ```
 */
object TokenStatusListManagerFactory {
    /**
     * Create a new [TokenStatusListManager].
     *
     * @param dataSource JDBC [DataSource] (HikariCP recommended)
     * @param kms [KeyManagementService] used to sign Token Status List JWTs
     * @param issuerDid DID of the issuer (`iss` claim in the JWT)
     * @param statusListUri Publicly reachable URI where the status token is served (`sub` claim)
     * @param bitsPerEntry 1 for single-purpose lists, 2 for combined revocation + suspension
     * @param issuerKeyId Issuer verification method (an Ed25519 key held in [kms]) that signs tokens;
     *   without it, building a token fails with a ConfigException
     * @return Configured [TokenStatusListManager]
     */
    fun create(
        dataSource: DataSource,
        kms: KeyManagementService,
        issuerDid: String,
        statusListUri: String,
        bitsPerEntry: Int = 1,
        issuerKeyId: VerificationMethodId? = null,
    ): TokenStatusListManager =
        TokenStatusListManager(
            dataSource = dataSource,
            kms = kms,
            issuerDid = issuerDid,
            statusListUri = statusListUri,
            bitsPerEntry = bitsPerEntry,
            issuerKeyId = issuerKeyId,
        )
}
