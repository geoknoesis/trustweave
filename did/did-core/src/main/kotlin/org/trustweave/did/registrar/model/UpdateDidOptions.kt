package org.trustweave.did.registrar.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Options for DID update operations according to DID Registration specification.
 *
 * @see https://identity.foundation/did-registration/
 */
@Serializable
data class UpdateDidOptions(
    /**
     * Secret material for authorization.
     *
     * Required for methods that require cryptographic proof of control.
     * Contains:
     * - Update key or signing key
     * - Method-specific authorization material
     */
    val secret: Secret? = null,

    /**
     * Method-specific options.
     *
     * Each DID method may define additional options:
     * - Update operation type
     * - Priority/urgency
     * - Method-specific configuration
     */
    val methodSpecificOptions: Map<String, JsonElement> = emptyMap()
)

