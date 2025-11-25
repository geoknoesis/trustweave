package com.trustweave.did.registrar.server.spring.dto

import com.trustweave.did.registrar.model.DeactivateDidOptions
import kotlinx.serialization.Serializable

/**
 * Request DTO for DID deactivation operations.
 * 
 * Used by RESTful endpoint: DELETE /1.0/dids/{did}
 * 
 * **Example:**
 * ```json
 * {
 *   "options": {
 *     "secret": {...}
 *   }
 * }
 * ```
 */
@Serializable
data class DeactivateDidRequest(
    /**
     * Deactivation options.
     */
    val options: DeactivateDidOptions? = null
)


