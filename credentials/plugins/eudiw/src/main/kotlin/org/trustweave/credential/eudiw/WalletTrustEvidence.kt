package org.trustweave.credential.eudiw

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wallet Trust Evidence (WTE) — a signed attestation about a wallet instance's
 * security posture and capabilities.
 *
 * The WTE is issued by the Wallet Provider and attests that a specific wallet
 * instance has been certified according to a recognized scheme (e.g. "EUDI-CC").
 * Relying Parties and PID Providers use it to verify that the wallet meets
 * the minimum assurance level required for credential issuance.
 *
 * Per eIDAS 2.0 Article 5c and EUDIW ARF §6.4.
 */
@Serializable
data class WalletTrustEvidence(
    /** Unique identifier for this wallet instance. */
    @SerialName("wallet_instance_id") val walletInstanceId: String,

    /** DID of the Wallet Provider that issued this attestation. */
    @SerialName("wallet_provider_id") val walletProviderId: String,

    /**
     * Public key of the wallet attestation key, as a JSON-serialized JWK object.
     * This key is bound to the wallet instance and used in key attestation proofs.
     */
    @SerialName("wallet_attestation_key") val walletAttestationKey: String,

    /** Security assurance level achieved by this wallet instance. */
    @SerialName("security_level") val securityLevel: WalletSecurityLevel,

    /** DID of the conformity assessment body that certified the wallet. */
    @SerialName("certification_authority") val certificationAuthority: String,

    /** Certification scheme under which the wallet was assessed, e.g. "EUDI-CC". */
    @SerialName("certification_scheme") val certificationScheme: String,

    /** Optional reference to the specific certification record or report. */
    @SerialName("certification_ref") val certificationRef: String? = null,

    /** Capabilities supported by this wallet instance. */
    @SerialName("wallet_capabilities") val walletCapabilities: WalletCapabilities,

    /** Issued-at timestamp in epoch seconds. */
    @SerialName("iat") val issuedAt: Long,

    /** Expiry timestamp in epoch seconds. */
    @SerialName("exp") val expiresAt: Long,
)

/**
 * Assurance level of a wallet instance, aligned with eIDAS assurance levels.
 *
 * - [HIGH]: Highest assurance, requires hardware-backed key storage and strong user authentication.
 * - [SUBSTANTIAL]: Medium assurance, suitable for most PID and attribute credential use cases.
 * - [LOW]: Basic assurance, typically software-only wallets.
 */
@Serializable
enum class WalletSecurityLevel {
    @SerialName("high") HIGH,
    @SerialName("substantial") SUBSTANTIAL,
    @SerialName("low") LOW,
}

/**
 * The set of capabilities supported by a certified wallet instance.
 *
 * Used by Credential Issuers and Relying Parties to determine whether the wallet
 * can handle specific credential formats, cryptographic operations, and
 * authentication methods.
 */
@Serializable
data class WalletCapabilities(
    /**
     * Credential formats supported by this wallet.
     * Values drawn from [EudiwConstants.CREDENTIAL_FORMAT_SD_JWT_VC] and
     * [EudiwConstants.CREDENTIAL_FORMAT_MSO_MDOC].
     */
    val formats: List<String>,

    /**
     * Cryptographic suites supported by this wallet for signing and key agreement.
     * E.g. `["ES256", "ES384", "EdDSA"]`.
     */
    @SerialName("cryptographic_suites") val cryptographicSuites: List<String>,

    /**
     * Key storage mechanisms available in this wallet.
     * E.g. `["hardware_key_storage"]` for SE/TEE-backed wallets.
     */
    @SerialName("key_storage") val keyStorage: List<String>,

    /**
     * Proof types the wallet can produce during credential issuance (OID4VCI).
     * Typically `["jwt", "cwt"]`.
     */
    @SerialName("proof_types") val proofTypes: List<String>,

    /**
     * User authentication methods supported by the wallet for credential access.
     * E.g. `["system_pin", "biometric"]`.
     */
    @SerialName("user_authentication") val userAuthentication: List<String>,
)
