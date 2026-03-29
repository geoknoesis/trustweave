package org.trustweave.did.parser

import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidOrUrl
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.model.parseServiceTypesFromJson
import kotlinx.serialization.json.*

/**
 * Shared conforming consumer for DID document JSON per DID 1.1 §6.2.2.
 *
 * Used by [org.trustweave.did.resolver.DefaultUniversalResolver] and
 * [org.trustweave.did.registrar.adapter.StandardUniversalRegistrarAdapter] so that
 * parsing behavior is consistent and supports:
 * - [controller] (string or array)
 * - [alsoKnownAs] (array of URL or DID)
 * - Relationship arrays (authentication, assertionMethod, etc.) with **string refs** or **embedded VM objects**.
 *   Embedded VMs are normalized per strategy (B): merged into [verificationMethod] (dedupe by id),
 *   relationships stored as [VerificationMethodId] only.
 * - Relative refs (e.g. "#key-1") resolved against document [id].
 *
 * @throws DidException.InvalidDidFormat when required fields are missing or invalid
 */
object DidDocumentJsonParser {

    private val RELATIONSHIP_NAMES = listOf(
        "authentication",
        "assertionMethod",
        "keyAgreement",
        "capabilityInvocation",
        "capabilityDelegation"
    )

    /**
     * Parses a DID document from a JSON object.
     *
     * @param json JSON object containing the DID document (e.g. from Universal Resolver or registrar)
     * @return Parsed [DidDocument]
     * @throws DidException.InvalidDidFormat if document is missing required "id" or otherwise invalid
     */
    fun parse(json: JsonObject): DidDocument {
        val idString = json["id"]?.jsonPrimitive?.content
            ?: throw DidException.InvalidDidFormat(
                did = "unknown",
                reason = "DID document missing 'id' field"
            )
        val baseDid = Did(idString)

        val context = parseContext(json)
        val controller = parseController(json)
        val alsoKnownAs = parseAlsoKnownAs(json)

        // Parse top-level verificationMethod
        val vmFromTopLevel = parseVerificationMethodArray(json["verificationMethod"]?.jsonArray, idString)

        // Parse relationship arrays (string refs + embedded VMs); collect embedded VMs and refs
        val relationshipVmIds = mutableMapOf<String, MutableList<VerificationMethodId>>()
        val embeddedVmsById = mutableMapOf<String, VerificationMethod>()

        for (fieldName in RELATIONSHIP_NAMES) {
            val refs = parseRelationshipArray(
                json[fieldName],
                baseDid,
                idString,
                embeddedVmsById
            )
            if (refs.isNotEmpty()) {
                relationshipVmIds[fieldName] = refs.toMutableList()
            }
        }

        // Merge VMs: top-level first, then embedded (dedupe by id)
        val topLevelIds = vmFromTopLevel.map { it.id.value }.toSet()
        val additionalVms = embeddedVmsById.values.filter { it.id.value !in topLevelIds }
        val verificationMethod = vmFromTopLevel + additionalVms

        val authentication = relationshipVmIds["authentication"] ?: emptyList()
        val assertionMethod = relationshipVmIds["assertionMethod"] ?: emptyList()
        val keyAgreement = relationshipVmIds["keyAgreement"] ?: emptyList()
        val capabilityInvocation = relationshipVmIds["capabilityInvocation"] ?: emptyList()
        val capabilityDelegation = relationshipVmIds["capabilityDelegation"] ?: emptyList()

        val service = parseServices(json)

        return DidDocument(
            id = baseDid,
            context = context,
            alsoKnownAs = alsoKnownAs,
            controller = controller,
            verificationMethod = verificationMethod,
            authentication = authentication,
            assertionMethod = assertionMethod,
            keyAgreement = keyAgreement,
            capabilityInvocation = capabilityInvocation,
            capabilityDelegation = capabilityDelegation,
            service = service
        )
    }

    private fun parseContext(json: JsonObject): List<String> {
        return when {
            json["@context"] != null -> {
                when (val ctx = json["@context"]) {
                    is JsonPrimitive -> listOf(ctx.content)
                    is JsonArray -> ctx.mapNotNull { (it as? JsonPrimitive)?.content }
                    else -> listOf("https://www.w3.org/ns/did/v1")
                }
            }
            else -> listOf("https://www.w3.org/ns/did/v1")
        }
    }

    private fun parseController(json: JsonObject): List<Did> {
        val c = json["controller"] ?: return emptyList()
        return when (c) {
            is JsonPrimitive -> c.content?.let { listOf(Did(it)) } ?: emptyList()
            is JsonArray -> c.mapNotNull { (it as? JsonPrimitive)?.content?.let { Did(it) } }
            else -> emptyList()
        }
    }

    private fun parseAlsoKnownAs(json: JsonObject): List<DidOrUrl> {
        return json["alsoKnownAs"]?.jsonArray?.mapNotNull { el ->
            (el as? JsonPrimitive)?.content?.let { DidOrUrl.tryParse(it) }
        } ?: emptyList()
    }

    private fun parseVerificationMethodArray(
        arr: JsonArray?,
        baseDid: String
    ): List<VerificationMethod> {
        if (arr == null) return emptyList()
        return arr.mapNotNull { el ->
            if (el is JsonObject) parseOneVerificationMethod(el, baseDid) else null
        }
    }

    private fun parseOneVerificationMethod(vmObj: JsonObject, baseDid: String): VerificationMethod? {
        val vmIdString = vmObj["id"]?.jsonPrimitive?.content ?: return null
        val vmType = vmObj["type"]?.jsonPrimitive?.content ?: return null
        val controllerString = vmObj["controller"]?.jsonPrimitive?.content ?: baseDid
        val vmId = try {
            VerificationMethodId.parse(vmIdString, Did(baseDid))
        } catch (_: IllegalArgumentException) {
            return null
        }
        val publicKeyJwk = vmObj["publicKeyJwk"]?.jsonObject?.let { jwk ->
            jwk.entries.associate { it.key to convertJsonElement(it.value) }
        }
        val publicKeyMultibase = vmObj["publicKeyMultibase"]?.jsonPrimitive?.content
        return VerificationMethod(
            id = vmId,
            type = vmType,
            controller = Did(controllerString),
            publicKeyJwk = publicKeyJwk,
            publicKeyMultibase = publicKeyMultibase
        )
    }

    /**
     * Parse a relationship array: each entry is either a string (VM id ref) or a full VM object.
     * For objects we parse as VM, add to [embeddedVmsById] (dedupe by id), and add its id to the returned list.
     */
    private fun parseRelationshipArray(
        element: JsonElement?,
        baseDid: Did,
        baseDidString: String,
        embeddedVmsById: MutableMap<String, VerificationMethod>
    ): List<VerificationMethodId> {
        if (element == null) return emptyList()
        return when (element) {
            is JsonPrimitive -> element.content?.let { idStr ->
                try {
                    listOf(VerificationMethodId.parse(idStr, baseDid))
                } catch (_: IllegalArgumentException) {
                    emptyList()
                }
            } ?: emptyList()
            is JsonArray -> element.flatMap { entry ->
                when (entry) {
                    is JsonPrimitive -> entry.content?.let { idStr ->
                        try {
                            listOf(VerificationMethodId.parse(idStr, baseDid))
                        } catch (_: IllegalArgumentException) {
                            emptyList()
                        }
                    } ?: emptyList()
                    is JsonObject -> {
                        val vm = parseOneVerificationMethod(entry, baseDidString) ?: return@flatMap emptyList()
                        embeddedVmsById.putIfAbsent(vm.id.value, vm)
                        listOf(vm.id)
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun parseServices(json: JsonObject): List<DidService> {
        return json["service"]?.jsonArray?.mapNotNull { sJson ->
            val sObj = sJson.jsonObject
            val sId = sObj["id"]?.jsonPrimitive?.content
            val sTypes = parseServiceTypesFromJson(sObj["type"])
            val sEndpoint = sObj["serviceEndpoint"]
            if (sId != null && sTypes != null && sEndpoint != null) {
                val endpoint = convertJsonElement(sEndpoint) ?: return@mapNotNull null
                DidService(
                    id = sId,
                    type = sTypes,
                    serviceEndpoint = endpoint
                )
            } else null
        } ?: emptyList()
    }

    private fun convertJsonElement(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonArray -> element.map { convertJsonElement(it) }
            is JsonObject -> element.entries.associate { it.key to convertJsonElement(it.value) }
            is JsonNull -> null
        }
    }
}
