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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64

/**
 * Implementation of did:peer method for peer-to-peer DIDs.
 *
 * Implements [DID Peer Specification](https://identity.foundation/peer-did-method-spec/):
 * - **Numalgo 0**: `did:peer:0{mb-multicodec-pubkey}` — genesis key encodes the DID (self-certifying)
 * - **Numalgo 1**: `did:peer:1{mb-sha256-genesis-doc}` — hash of genesis document
 * - **Numalgo 2**: `did:peer:2{.X{mb}}*` — multiple keys/services encoded as segments
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
                PeerDidConfig.NUMALGO_2 -> generateNumalgo2Did(keyHandle, algorithm, serviceEndpoint)
                else -> throw IllegalArgumentException("Unsupported numalgo: ${config.numalgo}")
            }

            val verificationMethod = DidMethodUtils.createVerificationMethod(
                did = did,
                keyHandle = keyHandle,
                algorithm = options.algorithm
            )

            val service = if (serviceEndpoint != null) {
                listOf(DidService(
                    id = "$did#didcomm",
                    type = listOf("DIDCommMessaging"),
                    serviceEndpoint = ServiceEndpoint.Url(serviceEndpoint)
                ))
            } else emptyList()

            val document = DidMethodUtils.buildDidDocument(
                did = did,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(verificationMethod.id.value),
                assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id.value)
                } else null,
                service = service
            )

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
     * Numalgo 1: `did:peer:1{mb}` — multibase-encoded SHA-256 hash of the genesis document bytes.
     */
    private fun generateNumalgo1Did(keyHandle: KeyHandle, algorithm: String, serviceEndpoint: String?): String {
        // Build a minimal genesis document (id placeholder stripped before hashing)
        val genesisDoc = buildMinimalDocument(keyHandle, algorithm, "did:peer:1:genesis", serviceEndpoint)
        val docJson = Json { prettyPrint = false }.encodeToString(DidDocument.serializer(), genesisDoc)
        val hash = MessageDigest.getInstance("SHA-256").digest(docJson.toByteArray(Charsets.UTF_8))
        val mb = "z" + encodeBase58(hash)
        return "did:peer:1$mb"
    }

    /**
     * Numalgo 2: `did:peer:2{.X{mb}}*` — multiple keys/services as labelled segments.
     *
     * Segment labels:
     * - `V` = verificationMethod / authentication key
     * - `E` = key agreement key (encryption)
     * - `S` = service (base58btc-encoded JSON service object)
     */
    private fun generateNumalgo2Did(keyHandle: KeyHandle, algorithm: String, serviceEndpoint: String?): String {
        val publicKeyBytes = extractPublicKeyBytes(keyHandle, algorithm)
        val prefixed = getMulticodecPrefix(algorithm) + publicKeyBytes
        val keyMb = "z" + encodeBase58(prefixed)

        val sb = StringBuilder("did:peer:2")
        sb.append(".V").append(keyMb) // verification / authentication

        if (serviceEndpoint != null) {
            // Encode service as abbreviated JSON → base58btc
            val serviceJson = """{"t":"dm","s":"$serviceEndpoint"}"""
            val serviceMb = "z" + encodeBase58(serviceJson.toByteArray(Charsets.UTF_8))
            sb.append(".S").append(serviceMb)
        }

        return sb.toString()
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
        val authentication = mutableListOf<String>()
        val keyAgreement = mutableListOf<String>()
        val services = mutableListOf<DidService>()
        val didObj = Did(didString)
        var keyIndex = 1

        for (segment in segments) {
            if (segment.length < 2) continue
            val purpose = segment[0]
            val mb = segment.substring(1)

            when (purpose) {
                'V', 'A' -> {
                    if (!mb.startsWith("z")) continue
                    val prefixedBytes = decodeBase58(mb.substring(1))
                    val (algorithm, _) = parseMulticodecKey(prefixedBytes) ?: continue
                    val vmType = DidMethodUtils.algorithmToVerificationMethodType(algorithm)
                    val vmId = VerificationMethodId.parse("$didString#key-$keyIndex", didObj)
                    verificationMethods.add(VerificationMethod(
                        id = vmId, type = vmType, controller = didObj, publicKeyMultibase = mb
                    ))
                    authentication.add(vmId.value)
                    keyIndex++
                }
                'E' -> {
                    if (!mb.startsWith("z")) continue
                    val prefixedBytes = decodeBase58(mb.substring(1))
                    val (algorithm, _) = parseMulticodecKey(prefixedBytes) ?: continue
                    val vmType = DidMethodUtils.algorithmToVerificationMethodType(algorithm)
                    val vmId = VerificationMethodId.parse("$didString#key-$keyIndex", didObj)
                    verificationMethods.add(VerificationMethod(
                        id = vmId, type = vmType, controller = didObj, publicKeyMultibase = mb
                    ))
                    keyAgreement.add(vmId.value)
                    keyIndex++
                }
                'S' -> {
                    if (!mb.startsWith("z")) continue
                    val serviceBytes = decodeBase58(mb.substring(1))
                    val serviceJson = serviceBytes.toString(Charsets.UTF_8)
                    // Parse abbreviated {"t":"dm","s":"<endpoint>"} format
                    val endpoint = Regex(""""s"\s*:\s*"([^"]+)"""").find(serviceJson)?.groupValues?.get(1)
                    if (endpoint != null) {
                        services.add(DidService(
                            id = "$didString#didcomm",
                            type = listOf("DIDCommMessaging"),
                            serviceEndpoint = ServiceEndpoint.Url(endpoint)
                        ))
                    }
                }
            }
        }

        if (verificationMethods.isEmpty()) return null

        return DidDocument(
            id = didObj,
            verificationMethod = verificationMethods,
            authentication = authentication.map { VerificationMethodId.parse(it, didObj) },
            keyAgreement = keyAgreement.map { VerificationMethodId.parse(it, didObj) },
            assertionMethod = authentication.map { VerificationMethodId.parse(it, didObj) },
            service = services
        )
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private fun extractNumalgo(didString: String): Int? {
        val after = didString.substringAfter("did:peer:")
        return after.firstOrNull()?.digitToIntOrNull()
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
