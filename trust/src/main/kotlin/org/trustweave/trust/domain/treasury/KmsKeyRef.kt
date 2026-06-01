package org.trustweave.trust.domain.treasury

/**
 * Opaque reference to a key held in the configured KMS. The treasury layer
 * resolves this against a [org.trustweave.kms.KeyManagementService] at
 * signing time; the key material never crosses module boundaries.
 *
 * @property kmsId   identifier of the KMS provider holding the key (e.g.
 *                   `"aws"`, `"azure"`, `"in-memory"`); allows multi-KMS
 *                   deployments to disambiguate
 * @property keyId   provider-specific key identifier (AWS KMS ARN, Azure key
 *                   URL, alias). Carried as a string so this type stays free
 *                   of provider SDK types.
 */
data class KmsKeyRef(
    val kmsId: String,
    val keyId: String,
) {
    init {
        require(kmsId.isNotBlank()) { "kmsId must not be blank" }
        require(keyId.isNotBlank()) { "keyId must not be blank" }
    }

    override fun toString(): String = "$kmsId:$keyId"
}
