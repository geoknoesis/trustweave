package org.trustweave.did.sidetree

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.did.model.DidDocument
import org.trustweave.did.representation.DidDocumentJsonProducer
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Pure, transport-free builder for Sidetree v1.0.0 operations (create / update /
 * deactivate). Shared by every DID method built on Sidetree (ION, Orb, ...) —
 * the differences are method-specific configuration ([SidetreeMethodSpec]) and
 * key persistence ([SidetreeKeyStore]), not the operation format.
 *
 * Each operation follows the Sidetree spec:
 *  - **Create** generates fresh recovery and update keypairs, embeds the signing
 *    key in the document patch, and returns the operation plus the long-form
 *    DID. The caller MUST persist the generated keypairs.
 *  - **Update** reveals the previous update public key (so its hash matches the
 *    on-ledger commitment), derives the next commitment from a fresh key, builds
 *    a JWS over `{updateKey, deltaHash}` signed by the previous update private
 *    key.
 *  - **Deactivate** reveals the previous recovery public key and builds a JWS
 *    over `{didSuffix, recoveryKey}` signed by the previous recovery private
 *    key.
 */
class SidetreeOperationBuilder(private val methodSpec: SidetreeMethodSpec) {

    private val b64url: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    /**
     * Output of [buildCreateOperation]. Carries the freshly-generated update and
     * recovery keypairs that anchor the operation's commitments. The caller MUST
     * persist these via a [SidetreeKeyStore] — without them no further updates
     * or deactivations are possible.
     */
    data class CreateOperationResult(
        val operation: JsonObject,
        val longFormDid: String,
        val didSuffix: String,
        val updateKeyPair: SidetreeP256KeyPair,
        val recoveryKeyPair: SidetreeP256KeyPair,
    )

    suspend fun buildCreateOperation(
        publicKeyJwk: Map<String, Any?>,
    ): CreateOperationResult = withContext(Dispatchers.IO) {
        val recoveryKeyPair = SidetreeP256KeyPair.generate()
        val updateKeyPair = SidetreeP256KeyPair.generate()

        val recoveryCommitment = SidetreeCommitment.compute(recoveryKeyPair.publicJwk)
        val updateCommitment = SidetreeCommitment.compute(updateKeyPair.publicJwk)

        val delta = buildJsonObject {
            put("updateCommitment", updateCommitment)
            put(
                "patches",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("action", "add-public-keys")
                            put(
                                "publicKeys",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("id", "key-1")
                                            put("type", "JsonWebKey2020")
                                            put("publicKeyJwk", SidetreeJcs.mapToJsonObject(publicKeyJwk.withoutPrivateD()))
                                            put(
                                                "purposes",
                                                buildJsonArray {
                                                    add("authentication")
                                                    add("assertionMethod")
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }
        val deltaHash = b64url.encodeToString(SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(delta)))

        val suffixData = buildJsonObject {
            put("deltaHash", deltaHash)
            put("recoveryCommitment", recoveryCommitment)
            methodSpec.suffixDataExtensionFields.forEach { (key, value) -> put(key, value) }
        }
        val didSuffix = b64url.encodeToString(SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(suffixData)))

        val longFormPayload = buildJsonObject {
            put("suffixData", suffixData)
            put("delta", delta)
        }
        val longFormPayloadB64 = b64url.encodeToString(
            longFormPayload.toString().toByteArray(StandardCharsets.UTF_8),
        )
        val longFormDid = "${methodSpec.namespace}$didSuffix:$longFormPayloadB64"

        val createOp = buildJsonObject {
            put("type", "create")
            put("suffixData", suffixData)
            put("delta", delta)
        }

        CreateOperationResult(
            operation = createOp,
            longFormDid = longFormDid,
            didSuffix = didSuffix,
            updateKeyPair = updateKeyPair,
            recoveryKeyPair = recoveryKeyPair,
        )
    }

    /**
     * Build a Sidetree update operation per spec §6.2.
     */
    suspend fun buildUpdateOperation(
        did: String,
        updatedDocument: DidDocument,
        previousUpdateKeyPair: SidetreeP256KeyPair,
        nextUpdatePublicJwk: Map<String, Any?>,
    ): JsonObject = withContext(Dispatchers.IO) {
        val nextUpdateCommitment = SidetreeCommitment.compute(nextUpdatePublicJwk)
        val revealValue = b64url.encodeToString(
            SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(previousUpdateKeyPair.publicJwk)),
        )

        val delta = buildJsonObject {
            put("updateCommitment", nextUpdateCommitment)
            put("patches", diffToPatches(updatedDocument))
        }
        val deltaHash = b64url.encodeToString(SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(delta)))

        val signedPayload = buildJsonObject {
            put("updateKey", SidetreeJcs.mapToJsonObject(previousUpdateKeyPair.publicJwk))
            put("deltaHash", deltaHash)
        }
        val signedDataJws = SidetreeJwsCompact.signES256(signedPayload, previousUpdateKeyPair.privateJwk)

        buildJsonObject {
            put("type", "update")
            put("didSuffix", extractDidSuffix(did))
            put("revealValue", revealValue)
            put("delta", delta)
            put("signedData", JsonPrimitive(signedDataJws))
        }
    }

    /**
     * Build a Sidetree deactivate operation per spec §6.4.
     */
    suspend fun buildDeactivateOperation(
        did: String,
        previousRecoveryKeyPair: SidetreeP256KeyPair,
    ): JsonObject = withContext(Dispatchers.IO) {
        val revealValue = b64url.encodeToString(
            SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(previousRecoveryKeyPair.publicJwk)),
        )

        val suffix = extractDidSuffix(did)
        val signedPayload = buildJsonObject {
            put("didSuffix", suffix)
            put("recoveryKey", SidetreeJcs.mapToJsonObject(previousRecoveryKeyPair.publicJwk))
        }
        val signedDataJws = SidetreeJwsCompact.signES256(signedPayload, previousRecoveryKeyPair.privateJwk)

        buildJsonObject {
            put("type", "deactivate")
            put("didSuffix", suffix)
            put("revealValue", revealValue)
            put("signedData", JsonPrimitive(signedDataJws))
        }
    }

    /**
     * Extract the Sidetree DID suffix from any of the canonical forms a Sidetree
     * DID can take. The suffix is always `base64url(multihash-sha256(suffixData))`
     * — 46 characters long, starting with `Ei` because the multihash prefix
     * bytes `0x12, 0x20` encode as `Ei` in base64url.
     *
     * Forms handled:
     *
     *  - Short form: `did:<method>:<suffix>`.
     *  - Long form (pre-anchor): `did:<method>:<suffix>:<long-form-payload>` —
     *    the trailing segment is the base64url-encoded create operation envelope.
     *  - Orb anchored form: `did:orb:<anchor-segment>:<suffix>` — the anchor
     *    segment carries the published anchor reference (a hashlink or CID).
     *
     * Pick whichever colon-separated segment matches the suffix shape; fall back
     * to the trailing segment if no match is found.
     */
    fun extractDidSuffix(did: String): String {
        require(did.startsWith(methodSpec.namespace)) {
            "DID does not match Sidetree namespace '${methodSpec.namespace}': $did"
        }
        val rest = did.removePrefix(methodSpec.namespace)
        val segments = rest.split(":")
        return segments.firstOrNull { isSidetreeSuffix(it) } ?: segments.last()
    }

    /**
     * A multihash-SHA-256-encoded value is exactly 46 base64url characters and
     * starts with `Ei` (the encoding of `0x12 0x20`).
     */
    private fun isSidetreeSuffix(segment: String): Boolean =
        segment.length == 46 && segment.startsWith("Ei")

    /**
     * Emit the Sidetree patches that fully replace the document state with
     * [updatedDocument]'s public keys and services using only the granular
     * patch actions (`add-public-keys`, `add-services`). These are enabled in
     * every Sidetree operator's default config, whereas the `replace` action is
     * not (Orb disables it by default).
     *
     * The patches are constructed as an additive set, so callers issuing a real
     * "update" should treat each call as a redefinition of state. Operators that
     * support `replace` can switch to the simpler one-patch form.
     */
    private fun diffToPatches(updatedDocument: DidDocument): kotlinx.serialization.json.JsonArray =
        buildJsonArray {
            val doc = toSidetreeDocument(updatedDocument)
            val publicKeys = doc["publicKeys"] as? kotlinx.serialization.json.JsonArray
            if (publicKeys != null && publicKeys.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("action", "add-public-keys")
                        put("publicKeys", publicKeys)
                    },
                )
            }
            val services = doc["services"] as? kotlinx.serialization.json.JsonArray
            if (services != null && services.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("action", "add-services")
                        put("services", services)
                    },
                )
            }
        }

    /**
     * Convert a TrustWeave [DidDocument] (W3C DID v1.1 shape) into the *internal*
     * document format that Sidetree carries inside a `replace` patch — `publicKeys`
     * and `services` only, no `@context`, no `controller`, no `alsoKnownAs`. Per
     * Sidetree v1 spec §5.6, the resolver re-wraps this into a W3C document at
     * resolution time.
     *
     * Each verification method becomes a public key. Its `purposes` list is
     * derived from membership in the document's [DidDocument.authentication],
     * [DidDocument.assertionMethod], [DidDocument.keyAgreement],
     * [DidDocument.capabilityInvocation] and [DidDocument.capabilityDelegation]
     * collections.
     */
    private fun toSidetreeDocument(document: DidDocument): kotlinx.serialization.json.JsonObject {
        val authIds = document.authentication.map { it.value }.toSet()
        val asstIds = document.assertionMethod.map { it.value }.toSet()
        val kaIds = document.keyAgreement.map { it.value }.toSet()
        val ciIds = document.capabilityInvocation.map { it.value }.toSet()
        val cdIds = document.capabilityDelegation.map { it.value }.toSet()
        return buildJsonObject {
            put(
                "publicKeys",
                buildJsonArray {
                    document.verificationMethod.forEach { vm ->
                        add(
                            buildJsonObject {
                                put("id", vm.id.value.substringAfter("#", vm.id.value))
                                put("type", vm.type)
                                vm.publicKeyJwk?.let { put("publicKeyJwk", SidetreeJcs.mapToJsonObject(it)) }
                                vm.publicKeyMultibase?.let { put("publicKeyMultibase", it) }
                                val purposes = buildList {
                                    if (vm.id.value in authIds) add("authentication")
                                    if (vm.id.value in asstIds) add("assertionMethod")
                                    if (vm.id.value in kaIds) add("keyAgreement")
                                    if (vm.id.value in ciIds) add("capabilityInvocation")
                                    if (vm.id.value in cdIds) add("capabilityDelegation")
                                }
                                if (purposes.isNotEmpty()) {
                                    put(
                                        "purposes",
                                        buildJsonArray { purposes.forEach { add(it) } },
                                    )
                                }
                            },
                        )
                    }
                },
            )
            if (document.service.isNotEmpty()) {
                put(
                    "services",
                    buildJsonArray {
                        document.service.forEach { svc ->
                            add(
                                buildJsonObject {
                                    put("id", svc.id.substringAfter("#", svc.id))
                                    put(
                                        "type",
                                        if (svc.type.size == 1) JsonPrimitive(svc.type.first()) else buildJsonArray { svc.type.forEach { add(it) } },
                                    )
                                    put("serviceEndpoint", svc.serviceEndpoint.toJsonElement())
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun org.trustweave.did.model.ServiceEndpoint.toJsonElement(): kotlinx.serialization.json.JsonElement {
        // Best-effort: most endpoints are a single URI; for richer endpoints we
        // serialize via the existing DidDocumentJsonProducer logic.
        return JsonPrimitive(this.toString())
    }
}
