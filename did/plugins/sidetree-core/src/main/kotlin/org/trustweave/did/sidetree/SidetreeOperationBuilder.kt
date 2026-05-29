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
                            put("action", "replace")
                            put(
                                "document",
                                buildJsonObject {
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
                },
            )
        }
        val deltaHash = b64url.encodeToString(SidetreeJcs.sha256(SidetreeJcs.canonicalize(delta)))

        val suffixData = buildJsonObject {
            put("deltaHash", deltaHash)
            put("recoveryCommitment", recoveryCommitment)
        }
        val didSuffix = b64url.encodeToString(SidetreeJcs.sha256(SidetreeJcs.canonicalize(suffixData)))

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
            SidetreeJcs.sha256(SidetreeJcs.canonicalize(previousUpdateKeyPair.publicJwk)),
        )

        val delta = buildJsonObject {
            put("updateCommitment", nextUpdateCommitment)
            put(
                "patches",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("action", "replace")
                            put("document", DidDocumentJsonProducer.toJsonObject(updatedDocument, useV1_1Context = true))
                        },
                    )
                },
            )
        }
        val deltaHash = b64url.encodeToString(SidetreeJcs.sha256(SidetreeJcs.canonicalize(delta)))

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
            SidetreeJcs.sha256(SidetreeJcs.canonicalize(previousRecoveryKeyPair.publicJwk)),
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
     * Extract the DID suffix from a Sidetree-form DID.
     *
     *  - `did:<method>:<suffix>` returns `<suffix>`.
     *  - `did:<method>:<suffix>:<long-form-payload>` also returns `<suffix>`.
     */
    fun extractDidSuffix(did: String): String {
        require(did.startsWith(methodSpec.namespace)) {
            "DID does not match Sidetree namespace '${methodSpec.namespace}': $did"
        }
        val rest = did.removePrefix(methodSpec.namespace)
        val colon = rest.indexOf(':')
        return if (colon >= 0) rest.substring(0, colon) else rest
    }
}
