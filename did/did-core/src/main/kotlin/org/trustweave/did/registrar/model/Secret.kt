package org.trustweave.did.registrar.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents secret/key material returned by a DID Registrar.
 *
 * According to the DID Registration specification, secrets are returned when:
 * - Registrar operates in Internal Secret Mode
 * - `returnSecrets` option is set to `true`
 *
 * Secrets contain sensitive cryptographic material that should be handled securely:
 * - Private keys for controller authentication
 * - Recovery keys for DID recovery
 * - Method-specific secret fields
 *
 * @see https://identity.foundation/did-registration/
 */
@Serializable
data class Secret(
    /**
     * List of key material (private keys, recovery keys, etc.).
     *
     * Each key entry typically contains:
     * - Key type/algorithm
     * - Private key material (JWK format or method-specific)
     * - Key ID or reference
     */
    val keys: List<KeyMaterial>? = null,

    /**
     * Recovery key for DID recovery operations.
     * Format depends on the DID method.
     */
    val recoveryKey: String? = null,

    /**
     * Update key for DID update operations.
     * Format depends on the DID method.
     */
    val updateKey: String? = null,

    /**
     * Method-specific secret fields.
     * Allows DID methods to include additional secret material.
     */
    val methodSpecificSecrets: Map<String, String>? = null
)

/**
 * Represents a single key material entry.
 */
@Serializable
data class KeyMaterial(
    /**
     * Key identifier or reference.
     */
    val id: String? = null,

    /**
     * Key type (e.g., "Ed25519", "secp256k1").
     */
    val type: String? = null,

    /**
     * Private key in JWK format (JSON Web Key).
     * Contains the private key material in a standardized format.
     */
    val privateKeyJwk: JsonObject? = null,

    /**
     * Private key in method-specific format.
     * Used when JWK format is not applicable.
     */
    val privateKeyMultibase: String? = null,

    /**
     * Additional key-specific properties.
     */
    val additionalProperties: Map<String, String>? = null
)

