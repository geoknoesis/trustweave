package org.trustweave.credential.oidc4vci.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A signature-free builder for tests that exercise the protocol flow rather than signing. */
class TestCredentialBuilder(
    override val supportedFormats: Set<String> = setOf("jwt_vc_json"),
) : Oidc4VciCredentialBuilder {
    override suspend fun build(request: Oidc4VciCredentialRequest): String =
        buildJsonObject {
            put("type", JsonArray(request.credentialTypes.map { JsonPrimitive(it) }))
            put("issuer", request.issuerDid)
            put(
                "credentialSubject",
                JsonObject(mapOf("id" to JsonPrimitive(request.subjectDid)) + request.claims),
            )
        }.toString()
}
