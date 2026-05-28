package org.trustweave.did.representation

import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.model.serviceEndpointToJsonElement
import org.trustweave.did.model.toServiceTypeJsonElement
import kotlinx.serialization.json.*

/**
 * Media type for DID documents per DID 1.1 / IANA registration.
 * Conforming producers SHOULD report this as Content-Type when serving or returning DID documents.
 */
const val APPLICATION_DID_MEDIA_TYPE: String = "application/did"

/** DID 1.1 JSON-LD context (first context entry per §6.2.3). */
const val DID_1_1_CONTEXT: String = "https://www.w3.org/ns/did/v1.1"

/** DID 1.0 JSON-LD context (legacy). */
const val DID_1_0_CONTEXT: String = "https://www.w3.org/ns/did/v1"

/**
 * Centralized conforming producer for DID document JSON per DID 1.1 §6.2.1 / §6.2.3.
 *
 * Use this when serializing [DidDocument] to JSON so that:
 * - [@context] uses v1.1 as first entry when [useV1_1Context] is true
 * - [controller], [alsoKnownAs], verification relationships, and [DidService.type] (string or array) are emitted correctly
 *
 * All producers (Universal Registrar adapter, AbstractWebDidMethod, AbstractBlockchainDidMethod, etc.)
 * should use this for consistent, spec-aligned output.
 */
object DidDocumentJsonProducer {

    /**
     * Produces a JSON object for the DID document.
     *
     * @param document The DID document
     * @param useV1_1Context If true, use [DID_1_1_CONTEXT] as first @context entry (DID 1.1 §6.2.3); otherwise use document's context or v1
     * @return JsonObject suitable for HTTP body or embedding
     */
    fun toJsonObject(document: DidDocument, useV1_1Context: Boolean = true): JsonObject {
        val contextList = when {
            useV1_1Context -> {
                val rest = document.context.filter { it != DID_1_1_CONTEXT && it != DID_1_0_CONTEXT }
                listOf(DID_1_1_CONTEXT) + rest
            }
            document.context.isNotEmpty() -> document.context
            else -> listOf(DID_1_0_CONTEXT)
        }
        val contextEl = if (contextList.size == 1) JsonPrimitive(contextList[0])
        else JsonArray(contextList.map { JsonPrimitive(it) })

        return buildJsonObject {
            put("@context", contextEl)
            put("id", JsonPrimitive(document.id.value))
            putController(this, document)
            putAlsoKnownAs(this, document)
            putVerificationMethod(this, document)
            putRelationship(this, "authentication", document.authentication.map { it.value })
            putRelationship(this, "assertionMethod", document.assertionMethod.map { it.value })
            putRelationship(this, "keyAgreement", document.keyAgreement.map { it.value })
            putRelationship(this, "capabilityInvocation", document.capabilityInvocation.map { it.value })
            putRelationship(this, "capabilityDelegation", document.capabilityDelegation.map { it.value })
            putService(this, document)
        }
    }

    /**
     * Produces JSON bytes and the conforming Content-Type.
     *
     * @param document The DID document
     * @param useV1_1Context If true, use v1.1 @context
     * @return Pair of (utf-8 bytes, APPLICATION_DID_MEDIA_TYPE)
     */
    fun toBytesWithMediaType(document: DidDocument, useV1_1Context: Boolean = true): Pair<ByteArray, String> {
        val json = toJsonObject(document, useV1_1Context)
        return json.toString().toByteArray(Charsets.UTF_8) to APPLICATION_DID_MEDIA_TYPE
    }

    private fun putController(builder: JsonObjectBuilder, document: DidDocument) {
        if (document.controller.isEmpty()) return
        if (document.controller.size == 1) {
            builder.put("controller", JsonPrimitive(document.controller[0].value))
        } else {
            builder.put("controller", JsonArray(document.controller.map { JsonPrimitive(it.value) }))
        }
    }

    private fun putAlsoKnownAs(builder: JsonObjectBuilder, document: DidDocument) {
        if (document.alsoKnownAs.isEmpty()) return
        builder.putJsonArray("alsoKnownAs") {
            document.alsoKnownAs.forEach { add(JsonPrimitive(it.toStringValue())) }
        }
    }

    private fun putVerificationMethod(builder: JsonObjectBuilder, document: DidDocument) {
        if (document.verificationMethod.isEmpty()) return
        builder.putJsonArray("verificationMethod") {
            document.verificationMethod.forEach { vm ->
                add(vmToJsonObject(vm))
            }
        }
    }

    private fun vmToJsonObject(vm: VerificationMethod): JsonObject {
        return buildJsonObject {
            put("id", JsonPrimitive(vm.id.value))
            put("type", JsonPrimitive(vm.type))
            put("controller", JsonPrimitive(vm.controller.value))
            vm.publicKeyJwk?.let { jwk ->
                put("publicKeyJwk", mapToJsonObject(jwk))
            }
            vm.publicKeyMultibase?.let { put("publicKeyMultibase", JsonPrimitive(it)) }
        }
    }

    private fun putRelationship(builder: JsonObjectBuilder, name: String, values: List<String>) {
        if (values.isEmpty()) return
        builder.put(name, JsonArray(values.map { JsonPrimitive(it) }))
    }

    private fun putService(builder: JsonObjectBuilder, document: DidDocument) {
        if (document.service.isEmpty()) return
        builder.putJsonArray("service") {
            document.service.forEach { s ->
                add(serviceToJsonObject(s))
            }
        }
    }

    private fun serviceToJsonObject(s: DidService): JsonObject {
        return buildJsonObject {
            put("id", JsonPrimitive(s.id))
            put("type", s.type.toServiceTypeJsonElement())
            put("serviceEndpoint", serviceEndpointToJsonElement(s.serviceEndpoint))
        }
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
        return buildJsonObject {
            map.forEach { (key, value) ->
                put(key, anyToJsonElement(value))
            }
        }
    }

    private fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value.toDouble())
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> mapToJsonObject(value as Map<String, Any?>)
            is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }
    }
}
