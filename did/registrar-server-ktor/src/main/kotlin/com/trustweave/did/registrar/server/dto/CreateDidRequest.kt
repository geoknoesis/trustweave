package com.trustweave.did.registrar.server.dto

import com.trustweave.did.registrar.model.CreateDidOptions
import kotlinx.serialization.Serializable

/**
 * Request DTO for DID creation operations.
 *
 * Used by RESTful endpoint: POST /1.0/dids
 *
 * **Example:**
 * ```json
 * {
 *   "method": "web",
 *   "options": {
 *     "keyManagementMode": "internal-secret",
 *     "returnSecrets": true
 *   }
 * }
 * ```
 */
@Serializable
data class CreateDidRequest(
    /**
     * DID method name (e.g., "web", "key", "ion").
     */
    val method: String,

    /**
     * Creation options.
     */
    val options: CreateDidOptions = CreateDidOptions()
)


