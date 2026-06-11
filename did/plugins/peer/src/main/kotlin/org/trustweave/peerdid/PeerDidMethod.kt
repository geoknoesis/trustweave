package org.trustweave.peerdid

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.util.decodeBase58
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.*
import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidService
import org.trustweave.did.model.ServiceEndpoint
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.did.model.parseServiceTypesFromJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.util.Base64

/**
 * Implementation of did:peer method for peer-to-peer DIDs.
 *
 * Implements [DID Peer Specification](https://identity.foundation/peer-did-method-spec/):
 * - **Numalgo 0**: `did:peer:0{mb-multicodec-pubkey}` — genesis key encodes the DID (self-certifying)
 * - **Numalgo 1**: `did:peer:1{mb-multihash-genesis-doc}` — multihash (0x12 0x20 + SHA-256) of genesis document
 * - **Numalgo 2**: `did:peer:2{.X{mb}}*` — multiple keys/services encoded as segments
 *   (`.S` service segments are unpadded base64url of abbreviated service JSON)
 */
class PeerDidMethod(
    kms: KeyManagementService,
    private val config: PeerDidConfig = PeerDidConfig.numalgo2()
) : AbstractDidMethod("peer", kms) {

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            val algorithm = options.algorithm.algorithmName
            val keyHandle = generateKey(algorithm, options.additionalProperties)

            val serviceEndpoint = if (config.includeServices) {
                options.additionalProperties["serviceEndpoint"] as? String
            } else null

            val did = when (config.numalgo) {
                PeerDidConfig.NUMALGO_0 -> generateNumalgo0Did(keyHandle, algorithm)
                PeerDidConfig.NUMALGO_1 -> generateNumalgo1Did(keyHandle, algorithm, serviceEndpoint)
                PeerDidConfig.NUMALGO_2 -> generateNumalgo2Did(keyHandle, algorithm, serviceEndpoint, options.purposes)
                else -> throw IllegalArgumentException("Unsupported numalgo: ${config.numalgo}")
            }

            // Single source of truth: for the self-describing numalgos (0 and 2), derive
            // the stored document by running the SAME parser any third party uses on the
            // DID string. This guarantees the creator's stored document is identical to
            // what spec-compliant external resolvers produce (VM ids #key-N, purpose-code
            // relationships) — proofs referencing the stored VM ids verify everywhere.
            // Numalgo 1 cannot be derived from the DID string (it encodes only a hash of
            // the genesis document), so its document is built directly.
            val document = when (config.numalgo) {
                PeerDidConfig.NUMALGO_0, PeerDidConfig.NUMALGO_2 ->
                    resolveEmbeddedDocument(did) ?: throw TrustWeaveException.Unknown(
                        code = "CREATE_FAILED",
                        message = "Generated did:peer is not parseable by its own resolver: $did"
                    )
                else -> buildNumalgo1Document(did, keyHandle, options, serviceEndpoint)
            }

            storeDocument(document.id, document)
            document
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "CREATE_FAILED",
                message = "Failed to create did:peer: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            val didString = did.value

            // Check local cache first
            val stored = getStoredDocument(did)
            if (stored != null) {
                return@withContext DidMethodUtils.createSuccessResolutionResult(
                    stored, method,
                    getDocumentMetadata(did)?.created,
                    getDocumentMetadata(did)?.updated
                )
            }

            // Numalgo 1 requires stored document lookup — cannot derive from DID alone
            val numalgo = extractNumalgo(didString)
            if (numalgo == PeerDidConfig.NUMALGO_1) {
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "notFound",
                    "Numalgo 1 peer DID requires stored genesis document which was not found locally",
                    method, didString
                )
            }

            // Numalgo 0 and 2 are self-describing — derive document from DID string
            val embedded = resolveEmbeddedDocument(didString)
            if (embedded != null) {
                storeDocument(embedded.id, embedded)
                return@withContext DidMethodUtils.createSuccessResolutionResult(embedded, method)
            }

            DidMethodUtils.createErrorResolutionResult("notFound", "DID document not found", method, didString)
        } catch (e: TrustWeaveException) {
            DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
        } catch (e: Exception) {
            DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
        }
    }

    override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument =
        withContext(Dispatchers.IO) {
            validateDidFormat(did)
            val currentResult = resolveDid(did)
            val currentDocument = when (currentResult) {
                is DidResolutionResult.Success -> currentResult.document
                else -> throw DidException.DidNotFound(did = did)
            }
            val updatedDocument = updater(currentDocument)
            storeDocument(updatedDocument.id.value, updatedDocument)
            updatedDocument
        }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        validateDidFormat(did)
        val exists = getStoredDocument(did) != null
        if (exists) {
            documents.remove(did.value)
            documentMetadata.remove(did.value)
        }
        exists
    }

    // ─── Numalgo implementations ────────────────────────────────────────────────

    /**
     * Numalgo 0: `did:peer:0{mb}` — multibase-encoded multicodec-prefixed public key.
     * Identical encoding to did:key; self-certifying.
     */
    private fun generateNumalgo0Did(keyHandle: KeyHandle, algorithm: String): String {
        val publicKeyBytes = extractPublicKeyBytes(keyHandle, algorithm)
        val prefixed = getMulticodecPrefix(algorithm) + publicKeyBytes
        val mb = "z" + encodeBase58(prefixed)
        return "did:peer:0$mb"
    }

    /**
     * Numalgo 1: `did:peer:1{mb}` — multibase-encoded MULTIHASH of the genesis document bytes.
     *
     * Per the did:peer spec the encoded numeric basis is a multihash, i.e. the
     * SHA-256 digest prefixed with the multihash header `0x12` (sha2-256) and
     * `0x20` (32-byte digest length) — not a raw SHA-256 hash.
     */
    private fun generateNumalgo1Did(keyHandle: KeyHandle, algorithm: String, serviceEndpoint: String?): String {
        // Build a minimal genesis document (id placeholder stripped before hashing).
        // Serialize via the canonical JSON producer — DidDocument.serializer() cannot
        // handle the @Contextual publicKeyJwk map without a registered module.
        val genesisDoc = buildMinimalDocument(keyHandle, algorithm, "did:peer:1:genesis", serviceEndpoint)
        val docJson = documentToJsonElement(genesisDoc).toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(docJson.toByteArray(Charsets.UTF_8))
        val multihash = byteArrayOf(0x12, 0x20) + hash
        val mb = "z" + encodeBase58(multihash)
        return "did:peer:1$mb"
    }

    /**
     * Numalgo 2: `did:peer:2{.X{mb}}*` — multiple keys/services as labelled segments.
     *
     * Segment purpose codes per the did:peer spec:
     * - `V` = authentication
     * - `A` = assertionMethod
     * - `E` = keyAgreement (encryption)
     * - `I` = capabilityInvocation
     * - `D` = capabilityDelegation
     * - `S` = service (`base64url(abbreviated-service-json)`, per spec — NOT multibase)
     *
     * The single generated signing key is emitted once per requested purpose.
     * [KeyPurpose.KEY_AGREEMENT] (`E`) is intentionally NOT encoded here: key agreement
     * requires a separate X25519 key, which this single-key creation flow does not
     * generate. If no encodable purpose is requested (e.g. KEY_AGREEMENT-only), `.V`
     * is emitted so the DID remains resolvable — the resulting document (stored AND
     * externally resolved, since both come from the same parser) therefore carries
     * `authentication = [#key-1]` and an empty `keyAgreement`.
     */
    private fun generateNumalgo2Did(
        keyHandle: KeyHandle,
        algorithm: String,
        serviceEndpoint: String?,
        purposes: List<KeyPurpose>
    ): String {
        val publicKeyBytes = extractPublicKeyBytes(keyHandle, algorithm)
        val prefixed = getMulticodecPrefix(algorithm) + publicKeyBytes
        val keyMb = "z" + encodeBase58(prefixed)

        val codes = buildList {
            if (KeyPurpose.AUTHENTICATION in purposes) add('V')
            if (KeyPurpose.ASSERTION in purposes) add('A')
            if (KeyPurpose.CAPABILITY_INVOCATION in purposes) add('I')
            if (KeyPurpose.CAPABILITY_DELEGATION in purposes) add('D')
        }.ifEmpty { listOf('V') }

        val sb = StringBuilder("did:peer:2")
        for (code in codes) {
            sb.append('.').append(code).append(keyMb)
        }

        if (serviceEndpoint != null) {
            sb.append(".S").append(encodeServiceSegment(serviceEndpoint))
        }

        return sb.toString()
    }

    /**
     * Encodes a did:peer:2 service segment per spec: the service JSON with
     * abbreviated keys (`type`→`t`, `serviceEndpoint`→`s`, `routingKeys`→`r`,
     * `accept`→`a`, type value `DIDCommMessaging`→`dm`) encoded as unpadded
     * base64url — `.S<base64url(json)>`.
     */
    private fun encodeServiceSegment(serviceEndpoint: String): String {
        val abbreviated = JsonObject(
            mapOf(
                "t" to JsonPrimitive("dm"),
                "s" to JsonPrimitive(serviceEndpoint)
            )
        )
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(abbreviated.toString().toByteArray(Charsets.UTF_8))
    }

    // ─── Embedded document resolution ───────────────────────────────────────────

    /**
     * Resolves a numalgo 0 or numalgo 2 peer DID by deriving the document from the DID string.
     */
    private fun resolveEmbeddedDocument(didString: String): DidDocument? {
        val numalgo = extractNumalgo(didString) ?: return null
        return when (numalgo) {
            PeerDidConfig.NUMALGO_0 -> resolveNumalgo0(didString)
            PeerDidConfig.NUMALGO_2 -> resolveNumalgo2(didString)
            else -> null
        }
    }

    private fun resolveNumalgo0(didString: String): DidDocument? {
        // did:peer:0{mb}
        val mb = didString.substringAfter("did:peer:0")
        if (!mb.startsWith("z")) return null
        val prefixedBytes = decodeBase58(mb.substring(1))
        if (prefixedBytes.size < 2) return null
        val (algorithm, publicKeyBytes) = parseMulticodecKey(prefixedBytes) ?: return null

        val vmType = DidMethodUtils.algorithmToVerificationMethodType(algorithm)
        val didObj = Did(didString)
        val vmId = VerificationMethodId.parse("$didString#key-1", didObj)
        val verificationMethod = VerificationMethod(
            id = vmId,
            type = vmType,
            controller = didObj,
            publicKeyMultibase = mb
        )
        return DidMethodUtils.buildDidDocument(
            did = didString,
            verificationMethod = listOf(verificationMethod),
            authentication = listOf(vmId.value),
            assertionMethod = listOf(vmId.value)
        )
    }

    private fun resolveNumalgo2(didString: String): DidDocument? {
        // did:peer:2{.X{mb}}*
        val segments = didString.substringAfter("did:peer:2").split(".").filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        val verificationMethods = mutableListOf<VerificationMethod>()
        val authentication = mutableListOf<VerificationMethodId>()
        val assertionMethod = mutableListOf<VerificationMethodId>()
        val keyAgreement = mutableListOf<VerificationMethodId>()
        val capabilityInvocation = mutableListOf<VerificationMethodId>()
        val capabilityDelegation = mutableListOf<VerificationMethodId>()
        val services = mutableListOf<DidService>()
        val didObj = Did(didString)
        var keyIndex = 1
        var serviceIndex = 0

        for (segment in segments) {
            if (segment.length < 2) continue
            val purpose = segment[0]
            val mb = segment.substring(1)

            when (purpose) {
                // Key purpose codes per the did:peer spec:
                // V → authentication, A → assertionMethod, E → keyAgreement,
                // I → capabilityInvocation, D → capabilityDelegation
                'V', 'A', 'E', 'I', 'D' -> {
                    if (!mb.startsWith("z")) continue
                    val prefixedBytes = decodeBase58(mb.substring(1))
                    val (algorithm, _) = parseMulticodecKey(prefixedBytes) ?: continue
                    val vmType = DidMethodUtils.algorithmToVerificationMethodType(algorithm)
                    val vmId = VerificationMethodId.parse("$didString#key-$keyIndex", didObj)
                    verificationMethods.add(VerificationMethod(
                        id = vmId, type = vmType, controller = didObj, publicKeyMultibase = mb
                    ))
                    when (purpose) {
                        'V' -> authentication.add(vmId)
                        'A' -> assertionMethod.add(vmId)
                        'E' -> keyAgreement.add(vmId)
                        'I' -> capabilityInvocation.add(vmId)
                        'D' -> capabilityDelegation.add(vmId)
                    }
                    keyIndex++
                }
                'S' -> {
                    val serviceJson = decodeServiceSegment(mb) ?: continue
                    val service = parseServiceSegmentJson(didString, serviceJson, serviceIndex) ?: continue
                    services.add(service)
                    serviceIndex++
                }
            }
        }

        if (verificationMethods.isEmpty()) return null

        return DidDocument(
            id = didObj,
            verificationMethod = verificationMethods,
            authentication = authentication,
            assertionMethod = assertionMethod,
            keyAgreement = keyAgreement,
            capabilityInvocation = capabilityInvocation,
            capabilityDelegation = capabilityDelegation,
            service = services
        )
    }

    // ─── Service segment encoding/decoding ──────────────────────────────────────

    /**
     * Decodes a did:peer:2 `.S` segment to its JSON string.
     *
     * Per spec the segment is unpadded base64url. Legacy 'z'-prefixed base58btc
     * segments (produced by earlier versions of this plugin) are still accepted.
     * Returns null if the segment cannot be decoded.
     */
    private fun decodeServiceSegment(segment: String): String? = try {
        val bytes = if (segment.startsWith("z")) {
            // Legacy non-spec encoding (multibase base58btc) — backward compatibility
            decodeBase58(segment.substring(1))
        } else {
            Base64.getUrlDecoder().decode(segment)
        }
        bytes.toString(Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * Parses an abbreviated did:peer:2 service JSON into a [DidService], expanding
     * the spec abbreviations: `t`→`type` (value `dm`→`DIDCommMessaging`),
     * `s`→`serviceEndpoint`, `r`→`routingKeys`, `a`→`accept`.
     *
     * Service ids follow the spec convention: `#service` for the first service,
     * `#service-1`, `#service-2`, ... for subsequent ones.
     *
     * Returns null if the JSON is malformed or lacks type/serviceEndpoint.
     */
    private fun parseServiceSegmentJson(didString: String, json: String, index: Int): DidService? {
        val parsed = try {
            expandServiceAbbreviations(Json.parseToJsonElement(json))
        } catch (e: Exception) {
            return null
        }
        val expanded = parsed as? JsonObject ?: return null

        val types = parseServiceTypesFromJson(expanded["type"]) ?: return null
        val endpointElement = expanded["serviceEndpoint"] ?: return null
        val endpointValue = jsonElementToValue(endpointElement) ?: return null

        // Legacy abbreviated form carries routingKeys/accept at the top level next
        // to a string endpoint; fold them into an object endpoint so they survive.
        val routingKeys = (expanded["routingKeys"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
        val accept = (expanded["accept"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
        val serviceEndpoint = if ((routingKeys != null || accept != null) && endpointValue is String) {
            ServiceEndpoint.ObjectEndpoint(
                buildMap {
                    put("uri", endpointValue)
                    routingKeys?.let { put("routingKeys", it) }
                    accept?.let { put("accept", it) }
                }
            )
        } else {
            ServiceEndpoint.ofOrNull(endpointValue) ?: return null
        }

        val id = if (index == 0) "$didString#service" else "$didString#service-$index"
        return DidService(id = id, type = types, serviceEndpoint = serviceEndpoint)
    }

    /**
     * Recursively expands did:peer:2 service abbreviations in a JSON tree.
     */
    private fun expandServiceAbbreviations(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.entries.associate { (key, value) ->
                val expandedKey = SERVICE_KEY_ABBREVIATIONS[key] ?: key
                val expandedValue = if (expandedKey == "type" && value is JsonPrimitive && value.isString) {
                    JsonPrimitive(SERVICE_TYPE_ABBREVIATIONS[value.content] ?: value.content)
                } else {
                    expandServiceAbbreviations(value)
                }
                expandedKey to expandedValue
            }
        )
        is JsonArray -> JsonArray(element.map { expandServiceAbbreviations(it) })
        else -> element
    }

    /**
     * Converts a JsonElement to plain Kotlin values (String/Map/List) for [ServiceEndpoint.ofOrNull].
     */
    private fun jsonElementToValue(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> element.content
        is JsonObject -> element.entries.associate { it.key to jsonElementToValue(it.value) }
        is JsonArray -> element.map { jsonElementToValue(it) }
    }

    private companion object {
        val SERVICE_KEY_ABBREVIATIONS = mapOf(
            "t" to "type",
            "s" to "serviceEndpoint",
            "r" to "routingKeys",
            "a" to "accept"
        )
        val SERVICE_TYPE_ABBREVIATIONS = mapOf(
            "dm" to "DIDCommMessaging"
        )
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private fun extractNumalgo(didString: String): Int? {
        val after = didString.substringAfter("did:peer:")
        return after.firstOrNull()?.digitToIntOrNull()
    }

    /**
     * Builds the stored document for a numalgo-1 DID.
     *
     * Numalgo 1 is the only numalgo whose document cannot be re-derived from the DID
     * string (the DID encodes a multihash of the genesis document), so the creator's
     * document is authoritative and is built directly from the creation options.
     */
    private fun buildNumalgo1Document(
        did: String,
        keyHandle: KeyHandle,
        options: DidCreationOptions,
        serviceEndpoint: String?
    ): DidDocument {
        val verificationMethod = DidMethodUtils.createVerificationMethod(
            did = did,
            keyHandle = keyHandle,
            algorithm = options.algorithm
        )
        val service = if (serviceEndpoint != null) {
            listOf(
                DidService(
                    id = "$did#didcomm",
                    type = listOf("DIDCommMessaging"),
                    serviceEndpoint = ServiceEndpoint.Url(serviceEndpoint)
                )
            )
        } else emptyList()

        val vmIds = listOf(verificationMethod.id)
        val purposes = options.purposes
        return DidDocument(
            id = Did(did),
            verificationMethod = listOf(verificationMethod),
            authentication = if (KeyPurpose.AUTHENTICATION in purposes) vmIds else emptyList(),
            assertionMethod = if (KeyPurpose.ASSERTION in purposes) vmIds else emptyList(),
            capabilityInvocation = if (KeyPurpose.CAPABILITY_INVOCATION in purposes) vmIds else emptyList(),
            capabilityDelegation = if (KeyPurpose.CAPABILITY_DELEGATION in purposes) vmIds else emptyList(),
            service = service
        )
    }

    private fun buildMinimalDocument(
        keyHandle: KeyHandle, algorithm: String, did: String, serviceEndpoint: String?
    ): DidDocument {
        val vm = DidMethodUtils.createVerificationMethod(did = did, keyHandle = keyHandle, algorithm = algorithm)
        val svc = if (serviceEndpoint != null) {
            listOf(DidService(id = "$did#didcomm", type = listOf("DIDCommMessaging"), serviceEndpoint = ServiceEndpoint.Url(serviceEndpoint)))
        } else emptyList()
        return DidMethodUtils.buildDidDocument(
            did = did,
            verificationMethod = listOf(vm),
            authentication = listOf(vm.id.value),
            service = svc
        )
    }

    private fun extractPublicKeyBytes(keyHandle: KeyHandle, algorithm: String): ByteArray {
        val mb = keyHandle.publicKeyMultibase
        if (mb != null && mb.startsWith("z")) {
            val decoded = decodeBase58(mb.substring(1))
            // If it already has multicodec prefix, strip it
            if (decoded.size > 2) {
                val (_, keyBytes) = parseMulticodecKey(decoded) ?: return decoded
                return keyBytes
            }
            return decoded
        }
        val jwk = keyHandle.publicKeyJwk ?: throw TrustWeaveException.Unknown(
            code = "MISSING_PUBLIC_KEY",
            message = "KeyHandle must have publicKeyMultibase or publicKeyJwk for did:peer"
        )
        val x = jwk["x"] as? String ?: throw TrustWeaveException.Unknown(
            code = "MISSING_JWK_X",
            message = "JWK missing 'x' field"
        )
        return Base64.getUrlDecoder().decode(x)
    }

    private fun getMulticodecPrefix(algorithm: String): ByteArray =
        DidMethodUtils.getMulticodecPrefix(algorithm)

    private fun parseMulticodecKey(prefixedKey: ByteArray): Pair<String, ByteArray>? =
        DidMethodUtils.parseMulticodecKey(prefixedKey)

    private fun encodeBase58(bytes: ByteArray): String = bytes.encodeBase58()

    private fun decodeBase58(encoded: String): ByteArray = encoded.decodeBase58()
}
