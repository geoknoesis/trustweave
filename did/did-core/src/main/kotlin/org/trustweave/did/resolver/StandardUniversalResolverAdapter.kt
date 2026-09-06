package org.trustweave.did.resolver

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.net.URLEncoder
import java.net.http.HttpRequest

/**
 * Protocol adapter for standard Universal Resolver implementations.
 *
 * Uses the standard endpoint pattern as defined by the Universal Resolver reference implementation:
 * - Resolution: `GET /1.0/identifiers/{did}`
 * - Methods: `GET /1.0/methods`
 *
 * This is the pattern used by:
 * - dev.uniresolver.io (public instance)
 * - Reference Universal Resolver implementation
 * - Most self-hosted Universal Resolver instances
 *
 * **Example Usage**:
 * ```kotlin
 * val resolver = DefaultUniversalResolver(
 *     baseUrl = "https://dev.uniresolver.io",
 *     protocolAdapter = StandardUniversalResolverAdapter()
 * )
 * ```
 */
class StandardUniversalResolverAdapter : UniversalResolverProtocolAdapter {
    override fun buildResolveUrl(
        baseUrl: String,
        did: String,
    ): String {
        val encodedDid = URLEncoder.encode(did, "UTF-8")
        return "$baseUrl/1.0/identifiers/$encodedDid"
    }

    override fun buildMethodsUrl(baseUrl: String): String? = "$baseUrl/1.0/methods"

    override fun configureAuth(
        requestBuilder: HttpRequest.Builder,
        apiKey: String?,
    ) {
        apiKey?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
    }

    override fun extractDidDocument(jsonResponse: JsonObject): JsonObject? {
        // Standard format: { "didDocument": {...}, "didDocumentMetadata": {...}, ... }
        // Return null when "didDocument" is absent so performResolution produces NotFound
        // instead of treating the metadata envelope as the DID document.
        // `as?` rather than the `.jsonObject` extension: an explicit JSON null (JsonNull, not
        // Kotlin null — e.g. a deactivated-DID body's "didDocument": null) would still invoke
        // `.jsonObject` and throw IllegalArgumentException instead of degrading to null.
        return jsonResponse["didDocument"] as? JsonObject
    }

    override fun extractDocumentMetadata(jsonResponse: JsonObject): JsonObject? {
        // See extractDidDocument: `as?` degrades an explicit JSON null to null instead of throwing.
        return jsonResponse["didDocumentMetadata"] as? JsonObject
    }

    override fun extractResolutionMetadata(jsonResponse: JsonObject): JsonObject =
        (jsonResponse["didResolutionMetadata"] as? JsonObject) ?: buildJsonObject {
        }

    override val providerName: String = "universal-resolver"
}
