package org.trustweave.revocation.bitstring

import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.did.identifiers.VerificationMethodId
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
 *     bitsPerEntry = 1,
 *     proofEngine = vcLdProofEngine, // wired to the issuer's KMS
 *     issuerKeyId = VerificationMethodId.parse("did:key:z6Mk...#key-1")
 * )
 * ```
 */
object BitstringStatusListManagerFactory {

    /**
     * Create a new [BitstringStatusListManager].
     *
     * @param dataSource JDBC [DataSource] (HikariCP recommended)
     * @param kms [KeyManagementService]; retained for source compatibility, no longer used for signing
     * @param issuerDid DID of the issuer used in status list VCs
     * @param bitsPerEntry 1 for single-purpose lists, 2 for combined revocation + suspension
     * @param proofEngine [ProofEngine] used to sign status list VCs with the issuer's real key;
     *   if omitted, building/publishing signed status list VCs fails with a ConfigException
     * @param issuerKeyId Issuer verification method ID used as the signing key for status list VCs
     * @param baseUrl Optional base URL where status list VCs are published; when set, the VC's
     *   `credentialSubject.id` becomes `"<baseUrl>/<statusListId>"`, otherwise it defaults to
     *   `"urn:uuid:<statusListId>"`. Either way the id is an absolute IRI so the subject's
     *   triples are covered by the proof signature.
     * @return Configured [BitstringStatusListManager]
     */
    fun create(
        dataSource: DataSource,
        kms: KeyManagementService,
        issuerDid: String,
        bitsPerEntry: Int = 1,
        proofEngine: ProofEngine? = null,
        issuerKeyId: VerificationMethodId? = null,
        baseUrl: String? = null
    ): BitstringStatusListManager = BitstringStatusListManager(
        dataSource = dataSource,
        kms = kms,
        issuerDid = issuerDid,
        bitsPerEntry = bitsPerEntry,
        proofEngine = proofEngine,
        issuerKeyId = issuerKeyId,
        baseUrl = baseUrl
    )
}
