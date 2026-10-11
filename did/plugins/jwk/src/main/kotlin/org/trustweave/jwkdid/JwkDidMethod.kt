package org.trustweave.jwkdid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyPurpose
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import java.util.Base64

/**
 * did:jwk DID method implementation.
 *
 * Format: `did:jwk:<base64url-encoded-JWK-JSON>`
 *
 * The DID is self-certifying — the public key JWK is embedded in the DID identifier
 * itself. Resolution requires no external registry: the document is derived by
 * decoding the identifier.
 *
 * Spec: https://github.com/quartzjer/did-jwk/blob/main/spec.md
 */
class JwkDidMethod(
    kms: KeyManagementService,
) : AbstractDidMethod("jwk", kms) {
    private val json = Json { ignoreUnknownKeys = true }
    private val b64url = Base64.getUrlEncoder().withoutPadding()
    private val b64urlDec = Base64.getUrlDecoder()

    override suspend fun createDid(options: DidCreationOptions): DidDocument =
        withContext(Dispatchers.IO) {
            val algorithm = options.algorithm.algorithmName
            val keyHandle = generateKey(algorithm, options.additionalProperties)

            val jwk =
                keyHandle.publicKeyJwk
                    ?: throw TrustWeaveException.ValidationFailed(
                        field = "publicKeyJwk",
                        reason = "KMS must produce a JWK representation for did:jwk",
                        value = null,
                    )

            val jwkJson =
                buildJsonObject {
                    jwk.forEach { (k, v) ->
                        when (v) {
                            is String -> put(k, v)
                            else -> put(k, v.toString())
                        }
                    }
                }

            val encoded = b64url.encodeToString(jwkJson.toString().toByteArray(Charsets.UTF_8))
            val did = "did:jwk:$encoded"
            val vmId = "$did#0"
            val didObj = Did(did)

            val verificationMethod =
                VerificationMethod(
                    id = VerificationMethodId.parse(vmId, didObj),
                    type = DidMethodUtils.algorithmToVerificationMethodType(algorithm),
                    controller = didObj,
                    publicKeyJwk = jwk,
                )

            val document =
                DidMethodUtils.buildDidDocument(
                    did = did,
                    verificationMethod = listOf(verificationMethod),
                    authentication = listOf(vmId),
                    assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) listOf(vmId) else null,
                )

            storeDocument(did, document)
            document
        }

    override suspend fun resolveDid(did: Did): DidResolutionResult =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                val encoded = did.value.substringAfter("did:jwk:")
                val jwkBytes =
                    try {
                        b64urlDec.decode(encoded)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        return@withContext DidMethodUtils.createErrorResolutionResult(
                            "invalidDid",
                            "Invalid base64url encoding: ${e.message}",
                            method,
                            did.value,
                        )
                    }

                val jwkObj: JsonObject =
                    try {
                        json.parseToJsonElement(String(jwkBytes, Charsets.UTF_8)).jsonObject
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        return@withContext DidMethodUtils.createErrorResolutionResult(
                            "invalidDid",
                            "Invalid JWK JSON: ${e.message}",
                            method,
                            did.value,
                        )
                    }

                // did:jwk embeds a PUBLIC key. A JWK carrying private members is refused outright: it
                // would publish a private key inside a DID document.
                val privateMembers = jwkObj.keys.intersect(PRIVATE_JWK_MEMBERS)
                if (privateMembers.isNotEmpty()) {
                    return@withContext DidMethodUtils.createErrorResolutionResult(
                        "invalidDid",
                        "did:jwk must embed a public JWK, but it contains private member(s): ${privateMembers.sorted()}",
                        method,
                        did.value,
                    )
                }

                val stored = getStoredDocument(did)
                if (stored != null) {
                    val metadata = getDocumentMetadata(did)
                    return@withContext DidMethodUtils.createSuccessResolutionResult(
                        stored,
                        method,
                        metadata?.created,
                        metadata?.updated,
                        deactivated = metadata?.deactivated ?: false,
                        retrieved = getLastFetched(did),
                    )
                }

                val kty =
                    (jwkObj["kty"] as? JsonPrimitive)?.content
                        ?: return@withContext DidMethodUtils.createErrorResolutionResult(
                            "invalidDid",
                            "The JWK in the DID has no 'kty' member",
                            method,
                            did.value,
                        )
                val crv = (jwkObj["crv"] as? JsonPrimitive)?.content
                // An unknown key type is an error, never silently treated as Ed25519.
                val algorithm =
                    jwkAlgorithm(kty, crv)
                        ?: return@withContext DidMethodUtils.createErrorResolutionResult(
                            "invalidDid",
                            "Unsupported JWK key type in did:jwk (kty=$kty, crv=$crv)",
                            method,
                            did.value,
                        )

                // The embedded key must be a real public key, not merely something shaped like one.
                JwkPublicKeyValidator.validate(kty, crv) { name -> (jwkObj[name] as? JsonPrimitive)?.content }?.let { reason ->
                    return@withContext DidMethodUtils.createErrorResolutionResult(
                        "invalidDid",
                        "The JWK in the DID is not a valid public key: $reason",
                        method,
                        did.value,
                    )
                }

                // `use` and `key_ops` say what the key may be used for, hence which verification
                // relationships it can be listed under (did:jwk spec).
                val usage =
                    keyUsage(jwkObj)
                        ?: return@withContext DidMethodUtils.createErrorResolutionResult(
                            "invalidDid",
                            "The JWK in the DID has an unusable or contradictory 'use'/'key_ops'",
                            method,
                            did.value,
                        )

                val jwkMap: Map<String, Any?> =
                    jwkObj.entries.associate { (k, v) ->
                        k to
                            when (v) {
                                is JsonPrimitive -> v.content
                                else -> v.toString()
                            }
                    }

                val vmId = "${did.value}#0"
                val verificationMethod =
                    VerificationMethod(
                        id = VerificationMethodId.parse(vmId, did),
                        type = DidMethodUtils.algorithmToVerificationMethodType(algorithm),
                        controller = did,
                        publicKeyJwk = jwkMap,
                    )

                val document =
                    DidMethodUtils.buildDidDocument(
                        did = did.value,
                        verificationMethod = listOf(verificationMethod),
                        authentication = if (usage.signature) listOf(vmId) else emptyList(),
                        assertionMethod = if (usage.signature) listOf(vmId) else emptyList(),
                        keyAgreement = if (usage.agreement) listOf(vmId) else emptyList(),
                    )

                storeDocument(did.value, document)
                DidMethodUtils.createSuccessResolutionResult(document, method)
            } catch (e: CancellationException) {
                throw e
            } catch (e: TrustWeaveException) {
                DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
            } catch (e: Exception) {
                DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
            }
        }

    /** What a JWK may be used for: signing relationships, key agreement, or both. */
    private class KeyUsage(
        val signature: Boolean,
        val agreement: Boolean,
    )

    /**
     * Derives the allowed usage from `use` and `key_ops` (RFC 7517 section 4.2 and 4.3). Absent both,
     * the key is a signature key (as before). Returns `null` when the members are contradictory, name
     * no recognised use, or have the wrong shape.
     */
    private fun keyUsage(jwk: JsonObject): KeyUsage? {
        val useElement = jwk["use"]
        val use =
            when {
                useElement == null -> null
                useElement is JsonPrimitive && useElement.isString -> useElement.content
                else -> return null
            }
        val fromUse =
            when (use) {
                null -> null
                "sig" -> KeyUsage(signature = true, agreement = false)
                "enc" -> KeyUsage(signature = false, agreement = true)
                else -> return null
            }
        val opsElement = jwk["key_ops"]
        val fromOps =
            if (opsElement == null) {
                null
            } else {
                val ops =
                    (opsElement as? kotlinx.serialization.json.JsonArray)
                        ?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: return null }
                        ?: return null
                val sig = ops.any { it in SIGNATURE_OPS }
                val agree = ops.any { it in AGREEMENT_OPS }
                if (!sig && !agree) return null
                KeyUsage(signature = sig, agreement = agree)
            }
        return when {
            fromUse != null && fromOps != null ->
                // Both present: they must agree (RFC 7517 section 4.3).
                if (fromOps.signature && !fromUse.signature || fromOps.agreement && !fromUse.agreement) null else fromUse
            fromUse != null -> fromUse
            fromOps != null -> fromOps
            else -> KeyUsage(signature = true, agreement = false)
        }
    }

    /**
     * A did:jwk is self-certifying: its key is the identifier. An update may change nothing that the
     * identifier determines (the key and its relationships) and may not change the document id.
     */
    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument =
        withContext(Dispatchers.IO) {
            validateDidFormat(did)
            val current =
                when (val result = resolveDid(did)) {
                    is DidResolutionResult.Success -> result.document
                    else ->
                        throw org.trustweave.did.exception.DidException
                            .DidNotFound(did = did)
                }
            super.updateDid(did) { stored ->
                updater(stored).also {
                    requireSameDid(did, it)
                    requireKeysUnchanged(current, it)
                }
            }
        }

    /**
     * Deactivation is recorded locally (the identifier alone would otherwise re-derive a live
     * document on the next resolve).
     */
    override suspend fun deactivateDid(did: Did): Boolean =
        withContext(Dispatchers.IO) {
            validateDidFormat(did)
            val current =
                when (val result = resolveDid(did)) {
                    is DidResolutionResult.Success -> result.document
                    else -> return@withContext false
                }
            recordLocalDeactivation(did.value, current)
            true
        }

    private fun jwkAlgorithm(
        kty: String,
        crv: String?,
    ): String? =
        when {
            kty == "OKP" && crv == "Ed25519" -> "ED25519"
            kty == "EC" && crv == "P-256" -> "P-256"
            kty == "EC" && crv == "P-384" -> "P-384"
            kty == "EC" && crv == "P-521" -> "P-521"
            kty == "EC" && crv == "secp256k1" -> "SECP256K1"
            kty == "RSA" -> "RSA"
            else -> null
        }

    private companion object {
        /** JWK members that only a private key carries (RFC 7518 sections 6.2.2, 6.3.2, 6.4). */
        val PRIVATE_JWK_MEMBERS = setOf("d", "p", "q", "dp", "dq", "qi", "oth", "k")
        val SIGNATURE_OPS = setOf("sign", "verify")
        val AGREEMENT_OPS = setOf("encrypt", "decrypt", "wrapKey", "unwrapKey", "deriveKey", "deriveBits")
    }
}
