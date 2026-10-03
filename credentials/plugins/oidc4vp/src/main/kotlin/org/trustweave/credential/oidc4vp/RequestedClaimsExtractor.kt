package org.trustweave.credential.oidc4vp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.trustweave.credential.oidc4vp.models.DcqlQuery

/**
 * Derives [org.trustweave.credential.oidc4vp.models.PermissionRequest.requestedClaims] from what the
 * verifier actually asked for.
 *
 * The result maps each input descriptor id (Presentation Exchange) or credential query id (DCQL)
 * to the JSONPath expressions of the claims it requests:
 * - Presentation Exchange: for every `constraints.fields[]` entry, its first `path` (later entries
 *   are alternative locations of the same claim).
 * - DCQL: every `claims[].path`, rendered as JSONPath (`$.a.b`, `$['org.iso.18013.5.1']['x']`,
 *   `[0]` for indices, `[*]` for `null` wildcards).
 */
internal object RequestedClaimsExtractor {
    fun extract(
        presentationDefinition: JsonObject?,
        dcqlQuery: DcqlQuery?,
    ): Map<String, List<String>> {
        val out = LinkedHashMap<String, List<String>>()
        presentationDefinition?.let { out.putAll(fromPresentationDefinition(it)) }
        dcqlQuery?.let { query ->
            for (credential in query.credentials) {
                val paths =
                    credential.claims.orEmpty().mapNotNull { claim ->
                        claim.path?.takeIf { it.isNotEmpty() }?.let { toJsonPath(it) }
                    }
                if (paths.isNotEmpty()) out[credential.id] = paths
            }
        }
        return out
    }

    private fun fromPresentationDefinition(pd: JsonObject): Map<String, List<String>> {
        val descriptors = pd["input_descriptors"] as? JsonArray ?: return emptyMap()
        val out = LinkedHashMap<String, List<String>>()
        descriptors.forEachIndexed { index, element ->
            val descriptor = element as? JsonObject ?: return@forEachIndexed
            val id = (descriptor["id"] as? JsonPrimitive)?.content ?: "input_descriptor_$index"
            val fields = (descriptor["constraints"] as? JsonObject)?.get("fields") as? JsonArray ?: return@forEachIndexed
            val paths =
                fields.mapNotNull { field ->
                    val path = (field as? JsonObject)?.get("path") as? JsonArray
                    (path?.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
                }
            if (paths.isNotEmpty()) out[id] = paths
        }
        return out
    }

    private val SIMPLE_KEY = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    private fun toJsonPath(path: List<kotlinx.serialization.json.JsonElement>): String =
        buildString {
            append('$')
            for (segment in path) {
                when {
                    segment is JsonNull -> append("[*]")
                    segment is JsonPrimitive && !segment.isString && segment.intOrNull != null -> append("[${segment.intOrNull}]")
                    segment is JsonPrimitive && SIMPLE_KEY.matches(segment.content) -> append('.').append(segment.content)
                    segment is JsonPrimitive -> append("['").append(segment.content.replace("'", "\\'")).append("']")
                    else -> append("[?]")
                }
            }
        }
}
