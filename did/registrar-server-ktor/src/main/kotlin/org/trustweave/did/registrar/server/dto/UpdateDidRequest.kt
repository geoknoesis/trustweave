package org.trustweave.did.registrar.server.dto

import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.model.UpdateDidOptions
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Request DTO for DID update operations.
 *
 * Used by RESTful endpoint: PUT /1.0/dids/{did}
 *
 * **Example:**
 * ```json
 * {
 *   "didDocument": {
 *     "id": "did:web:example.com",
 *     "verificationMethod": [...]
 *   },
 *   "options": {
 *     "secret": {...}
 *   }
 * }
 * ```
 */
@Serializable
data class UpdateDidRequest(
    /**
     * Updated DID Document.
     */
    @Contextual
    val didDocument: DidDocument,

    /**
     * Update options.
     */
    val options: UpdateDidOptions? = null
)


