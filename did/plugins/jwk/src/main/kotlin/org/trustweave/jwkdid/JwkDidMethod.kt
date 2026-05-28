package org.trustweave.jwkdid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
class JwkDidMethod(kms: KeyManagementService) : AbstractDidMethod("jwk", kms) {

    private val json = Json { ignoreUnknownKeys = true }
    private val b64url = Base64.getUrlEncoder().withoutPadding()
    private val b64urlDec = Base64.getUrlDecoder()

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        val algorithm = options.algorithm.algorithmName
        val keyHandle = generateKey(algorithm, options.additionalProperties)

        val jwk = keyHandle.publicKeyJwk
            ?: throw TrustWeaveException.ValidationFailed(
                field = "publicKeyJwk",
                reason = "KMS must produce a JWK representation for did:jwk",
                value = null,
            )

        val jwkJson = buildJsonObject {
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

        val verificationMethod = VerificationMethod(
            id = VerificationMethodId.parse(vmId, didObj),
            type = DidMethodUtils.algorithmToVerificationMethodType(algorithm),
            controller = didObj,
            publicKeyJwk = jwk,
        )

        val document = DidMethodUtils.buildDidDocument(
            did = did,
            verificationMethod = listOf(verificationMethod),
            authentication = listOf(vmId),
            assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) listOf(vmId) else null,
        )

        storeDocument(did, document)
        document
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val encoded = did.value.substringAfter("did:jwk:")
            val jwkBytes = try {
                b64urlDec.decode(encoded)
            } catch (e: Exception) {
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "invalidDid",
                    "Invalid base64url encoding: ${e.message}",
                    method,
                    did.value,
                )
            }

            val jwkObj: JsonObject = try {
                json.parseToJsonElement(String(jwkBytes, Charsets.UTF_8)).jsonObject
            } catch (e: Exception) {
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "invalidDid",
                    "Invalid JWK JSON: ${e.message}",
                    method,
                    did.value,
                )
            }

            val stored = getStoredDocument(did)
            if (stored != null) {
                return@withContext DidMethodUtils.createSuccessResolutionResult(stored, method)
            }

            val kty = (jwkObj["kty"] as? JsonPrimitive)?.content ?: "OKP"
            val crv = (jwkObj["crv"] as? JsonPrimitive)?.content
            val algorithm = jwkAlgorithm(kty, crv)

            val jwkMap: Map<String, Any?> = jwkObj.entries.associate { (k, v) ->
                k to when (v) {
                    is JsonPrimitive -> v.content
                    else -> v.toString()
                }
            }

            val vmId = "${did.value}#0"
            val verificationMethod = VerificationMethod(
                id = VerificationMethodId.parse(vmId, did),
                type = DidMethodUtils.algorithmToVerificationMethodType(algorithm),
                controller = did,
                publicKeyJwk = jwkMap,
            )

            val document = DidMethodUtils.buildDidDocument(
                did = did.value,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(vmId),
                assertionMethod = listOf(vmId),
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

    private fun jwkAlgorithm(kty: String, crv: String?): String = when {
        kty == "OKP" && crv == "Ed25519" -> "ED25519"
        kty == "EC" && crv == "P-256" -> "P-256"
        kty == "EC" && crv == "P-384" -> "P-384"
        kty == "EC" && crv == "P-521" -> "P-521"
        kty == "EC" && crv == "secp256k1" -> "SECP256K1"
        kty == "RSA" -> "RSA"
        else -> "ED25519"
    }
}
