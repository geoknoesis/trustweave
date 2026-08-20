package org.trustweave.did.resolution

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.did.representation.DidDocumentJsonProducer
import org.trustweave.did.representation.DidMediaTypes
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.documentMetadata
import org.trustweave.did.resolver.documentOrNull
import org.trustweave.did.resolver.resolutionMetadata

/**
 * Serializes a [DidResolutionResult] to the DID Resolution Result JSON structure of
 * DID Resolution 1.0 §9.
 *
 * The structure always carries all three members: `didDocument` (JSON null when there is no
 * document), `didResolutionMetadata` and `didDocumentMetadata` (an empty object on failure, as
 * §4 requires).
 */
object DidResolutionResultJson {

    /** Media type of this data structure (§9). */
    const val MEDIA_TYPE: String = DidMediaTypes.DID_RESOLUTION

    fun toJson(result: DidResolutionResult): JsonObject = buildJsonObject {
        val document = result.documentOrNull
        if (document == null) {
            put("didDocument", JsonNull)
        } else {
            put("didDocument", DidDocumentJsonProducer.toJsonObject(document))
        }
        put("didResolutionMetadata", result.resolutionMetadata.toJson())
        put("didDocumentMetadata", result.documentMetadata.toJson())
    }
}
