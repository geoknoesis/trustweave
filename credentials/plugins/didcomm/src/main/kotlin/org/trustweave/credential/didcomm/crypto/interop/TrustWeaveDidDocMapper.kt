package org.trustweave.credential.didcomm.crypto.interop

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.didcommx.didcomm.common.VerificationMaterial
import org.didcommx.didcomm.common.VerificationMaterialFormat
import org.didcommx.didcomm.common.VerificationMethodType
import org.didcommx.didcomm.diddoc.DIDCommService
import org.didcommx.didcomm.diddoc.DIDDoc
import org.didcommx.didcomm.diddoc.VerificationMethod as DidCommVerificationMethod
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidService
import org.trustweave.did.model.ServiceEndpoint
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.model.toAny

/**
 * Maps a TrustWeave [DidDocument] to a didcomm-java [DIDDoc].
 *
 * Verification methods with [VerificationMethod.publicKeyJwk] are exposed as
 * [VerificationMethodType.JSON_WEB_KEY_2020] (JWK string material), which didcomm-java uses for ECDH and signing.
 */
object TrustWeaveDidDocMapper {

    fun toDidComm(doc: DidDocument): DIDDoc {
        val did = doc.id.value
        val vms = doc.verificationMethod.map { toDidCommVm(it, did) }
        val auth = doc.authentication.map { it.value }
        val keyAgreement = doc.keyAgreement.map { it.value }
        val didCommServices = doc.service.mapNotNull { toDidCommService(it) }
        return DIDDoc(
            did = did,
            keyAgreements = keyAgreement,
            authentications = auth,
            verificationMethods = vms,
            didCommServices = didCommServices,
        )
    }

    private fun toDidCommVm(vm: VerificationMethod, documentDid: String): DidCommVerificationMethod {
        val id = normalizeVmId(vm.id.value, documentDid)
        val jwk = vm.publicKeyJwk
        if (jwk != null) {
            val jwkJson = jsonObjectFromAnyMap(jwk)
            val material = VerificationMaterial(
                format = VerificationMaterialFormat.JWK,
                value = kotlinx.serialization.json.Json.encodeToString(JsonObject.serializer(), jwkJson),
            )
            return DidCommVerificationMethod(
                id = id,
                type = VerificationMethodType.JSON_WEB_KEY_2020,
                verificationMaterial = material,
                controller = vm.controller.value,
            )
        }
        val multibase = vm.publicKeyMultibase
            ?: throw IllegalArgumentException(
                "Verification method '${vm.id}' has no publicKeyJwk or publicKeyMultibase; DIDComm requires key material",
            )
        val type = mapVmTypeString(vm.type)
        val format = when (type) {
            VerificationMethodType.X25519_KEY_AGREEMENT_KEY_2019,
            VerificationMethodType.ED25519_VERIFICATION_KEY_2018,
            -> VerificationMaterialFormat.BASE58
            VerificationMethodType.X25519_KEY_AGREEMENT_KEY_2020,
            VerificationMethodType.ED25519_VERIFICATION_KEY_2020,
            -> VerificationMaterialFormat.MULTIBASE
            else -> throw IllegalArgumentException("Unsupported verification method type '${vm.type}' without JWK")
        }
        return DidCommVerificationMethod(
            id = id,
            type = type,
            verificationMaterial = VerificationMaterial(format, multibase),
            controller = vm.controller.value,
        )
    }

    private fun normalizeVmId(id: String, documentDid: String): String =
        if (id.startsWith("did:")) id else "$documentDid${if (id.startsWith("#")) "" else "#"}$id"

    private fun mapVmTypeString(type: String): VerificationMethodType = when {
        type.equals("JsonWebKey2020", ignoreCase = true) -> VerificationMethodType.JSON_WEB_KEY_2020
        type.equals("X25519KeyAgreementKey2020", ignoreCase = true) -> VerificationMethodType.X25519_KEY_AGREEMENT_KEY_2020
        type.equals("Ed25519VerificationKey2020", ignoreCase = true) -> VerificationMethodType.ED25519_VERIFICATION_KEY_2020
        type.equals("X25519KeyAgreementKey2019", ignoreCase = true) -> VerificationMethodType.X25519_KEY_AGREEMENT_KEY_2019
        type.equals("Ed25519VerificationKey2018", ignoreCase = true) -> VerificationMethodType.ED25519_VERIFICATION_KEY_2018
        else -> VerificationMethodType.OTHER
    }

    private fun jsonObjectFromAnyMap(map: Map<String, Any?>): JsonObject =
        JsonObject(map.mapValues { (_, v) -> anyToJsonElement(v) })

    private fun anyToJsonElement(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v.toString())
        is Boolean -> JsonPrimitive(v)
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            jsonObjectFromAnyMap(v as Map<String, Any?>)
        }
        is List<*> -> JsonArray(v.map { anyToJsonElement(it) })
        else -> JsonPrimitive(v.toString())
    }

    private fun toDidCommService(s: DidService): DIDCommService? {
        val types = s.type.map { it.lowercase() }
        if (!types.any { it.contains("didcomm") }) return null
        val endpoint = when (val ep = s.serviceEndpoint) {
            is ServiceEndpoint.Url -> ep.url
            else -> ep.toAny().toString()
        }
        return DIDCommService(
            id = s.id,
            serviceEndpoint = endpoint,
            routingKeys = emptyList(),
            accept = null,
        )
    }
}
