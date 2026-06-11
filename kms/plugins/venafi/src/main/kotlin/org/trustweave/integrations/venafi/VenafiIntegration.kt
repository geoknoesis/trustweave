package org.trustweave.integrations.venafi

import org.trustweave.core.exception.TrustWeaveException

/**
 * **PLACEHOLDER — NOT A KMS, NOT IMPLEMENTED.**
 *
 * Despite living under `kms/plugins/venafi`, this module provides **no
 * `KeyManagementService` implementation at all** — there is no key generation, signing,
 * or key storage here, and nothing is registered for SPI discovery. This single class is
 * a non-functional sketch of a future Venafi certificate-lifecycle integration; its only
 * method throws a not-implemented error.
 *
 * What a real integration might eventually cover:
 * - Certificate lifecycle management
 * - Credential issuance with Venafi certificates
 *
 * Do not depend on this module expecting KMS capabilities.
 */
class VenafiIntegration(
    val baseUrl: String,
    val apiKey: String
) {
    init {
        require(baseUrl.isNotBlank()) { "Venafi base URL must be specified" }
        require(apiKey.isNotBlank()) { "Venafi API key must be specified" }
    }

    /**
     * Issues a verifiable credential using a Venafi certificate.
     *
     * @param certificateId Venafi certificate ID
     * @param credentialType Type of credential to issue
     * @return Issued verifiable credential
     */
    suspend fun issueCredentialWithCertificate(
        certificateId: String,
        credentialType: String
    ): Any {
        throw TrustWeaveException.Unknown(
            code = "VENAFI_NOT_IMPLEMENTED",
            message = "The Venafi integration is a placeholder and is not implemented: " +
                "no Venafi API client exists in this module."
        )
    }
}

