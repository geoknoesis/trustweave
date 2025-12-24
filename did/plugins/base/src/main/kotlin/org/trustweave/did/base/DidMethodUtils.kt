package org.trustweave.did.base

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.*
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.model.DidService
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyHandle
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Common utilities for DID method implementations.
 *
 * Provides helper functions for:
 * - DID parsing and validation
 * - Verification method type mapping (algorithm → verification method type)
 * - DID document builder helpers
 * - Resolution metadata helpers
 */
object DidMethodUtils {

    /**
     * Parses a DID string into method and identifier parts.
     *
     * @param did The DID string (e.g., "did:web:example.com")
     * @return Pair of (method, identifier) or null if invalid
     */
    fun parseDid(did: String): Pair<String, String>? {
        if (!did.startsWith("did:")) {
            return null
        }
        val parts = did.substring(4).split(":", limit = 2)
        if (parts.size != 2) {
            return null
        }
        return parts[0] to parts[1]
    }

    /**
     * Validates that a DID matches a specific method.
     *
     * @param did The DID to validate
     * @param expectedMethod The expected method name
     * @throws IllegalArgumentException if the DID doesn't match the method
     */
    fun validateDidMethod(did: String, expectedMethod: String) {
        val parsed = parseDid(did)
            ?: throw IllegalArgumentException("Invalid DID format: $did")

        if (parsed.first != expectedMethod) {
            throw IllegalArgumentException("DID method mismatch: expected $expectedMethod, got ${parsed.first}")
        }
    }

    /**
     * Maps a cryptographic algorithm to its verification method type.
     *
     * @param algorithm Algorithm name (e.g., "Ed25519", "secp256k1")
     * @return Verification method type (e.g., "Ed25519VerificationKey2020")
     */
    fun algorithmToVerificationMethodType(algorithm: String): String {
        return when (algorithm.uppercase()) {
            "ED25519" -> "Ed25519VerificationKey2020"
            "SECP256K1" -> "EcdsaSecp256k1VerificationKey2019"
            "P-256", "P256" -> "EcdsaSecp256r1VerificationKey2019"
            "P-384", "P384" -> "EcdsaSecp384r1VerificationKey2019"
            "P-521", "P521" -> "EcdsaSecp521r1VerificationKey2019"
            "RSA" -> "RsaVerificationKey2018"
            else -> "JsonWebKey2020"
        }
    }

    /**
     * Maps a KeyAlgorithm enum to its verification method type.
     *
     * @param algorithm The KeyAlgorithm enum value
     * @return Verification method type
     */
    fun algorithmToVerificationMethodType(algorithm: KeyAlgorithm): String {
        return algorithmToVerificationMethodType(algorithm.algorithmName)
    }

    /**
     * Creates a verification method reference from a key handle.
     *
     * @param did The DID identifier
     * @param keyHandle The key handle from KMS
     * @param algorithm The algorithm name
     * @param controller Optional controller DID (defaults to the DID itself)
     * @return VerificationMethod
     */
    fun createVerificationMethod(
        did: String,
        keyHandle: KeyHandle,
        algorithm: String,
        controller: String? = null
    ): VerificationMethod {
        val didObj = Did(did)
        val verificationMethodIdStr = "$did#${keyHandle.id.value}"
        val verificationMethodId = VerificationMethodId.parse(verificationMethodIdStr, didObj)
        val verificationMethodType = algorithmToVerificationMethodType(algorithm)
        val controllerDid = if (controller != null) Did(controller) else didObj

        return VerificationMethod(
            id = verificationMethodId,
            type = verificationMethodType,
            controller = controllerDid,
            publicKeyJwk = keyHandle.publicKeyJwk,
            publicKeyMultibase = keyHandle.publicKeyMultibase
        )
    }

    /**
     * Creates a verification method reference from a key handle using KeyAlgorithm enum.
     *
     * @param did The DID identifier
     * @param keyHandle The key handle from KMS
     * @param algorithm The KeyAlgorithm enum value
     * @param controller Optional controller DID (defaults to the DID itself)
     * @return VerificationMethod
     */
    fun createVerificationMethod(
        did: String,
        keyHandle: KeyHandle,
        algorithm: KeyAlgorithm,
        controller: String? = null
    ): VerificationMethod {
        return createVerificationMethod(did, keyHandle, algorithm.algorithmName, controller)
    }

    /**
     * Builds a DID document with standard structure.
     *
     * @param did The DID identifier
     * @param verificationMethod The verification methods
     * @param authentication Optional authentication methods (defaults to first verification method)
     * @param assertionMethod Optional assertion methods
     * @param keyAgreement Optional key agreement methods
     * @param service Optional service endpoints
     * @return DidDocument
     */
    fun buildDidDocument(
        did: String,
        verificationMethod: List<VerificationMethod>,
        authentication: List<String>? = null,
        assertionMethod: List<String>? = null,
        keyAgreement: List<String>? = null,
        service: List<DidService>? = null
    ): DidDocument {
        require(verificationMethod.isNotEmpty()) { "DID document must have at least one verification method" }

        val didObj = Did(did)
        val defaultAuth = listOf(verificationMethod.first().id)
        val auth = authentication?.map { VerificationMethodId.parse(it, didObj) } ?: defaultAuth
        val assertion = assertionMethod?.map { VerificationMethodId.parse(it, didObj) } ?: emptyList()
        val keyAgr = keyAgreement?.map { VerificationMethodId.parse(it, didObj) } ?: emptyList()

        return DidDocument(
            id = didObj,
            verificationMethod = verificationMethod,
            authentication = auth,
            assertionMethod = assertion,
            keyAgreement = keyAgr,
            service = service ?: emptyList()
        )
    }

    /**
     * Creates a successful DID resolution result.
     *
     * @param document The resolved DID document
     * @param method The DID method name
     * @param created Optional creation timestamp (defaults to now)
     * @param updated Optional update timestamp (defaults to now)
     * @return DidResolutionResult.Success
     */
    fun createSuccessResolutionResult(
        document: DidDocument,
        method: String,
        created: Instant? = null,
        updated: Instant? = null
    ): DidResolutionResult {
        val now = Clock.System.now()
        return DidResolutionResult.Success(
            document = document,
            documentMetadata = DidDocumentMetadata(
                created = created ?: now,
                updated = updated ?: now
            ),
            resolutionMetadata = mapOf(
                "method" to method,
                "driver" to "TrustWeave"
            )
        )
    }

    /**
     * Creates an error DID resolution result.
     *
     * @param error Error code (e.g., "notFound", "invalidDid")
     * @param message Optional error message
     * @param method The DID method name
     * @param did Optional DID string for error context
     * @return DidResolutionResult.Failure
     */
    fun createErrorResolutionResult(
        error: String,
        message: String? = null,
        method: String? = null,
        did: String? = null
    ): DidResolutionResult {
        val metadata = buildMap<String, Any?> {
            put("error", error)
            if (message != null) {
                put("errorMessage", message)
            }
            if (method != null) {
                put("method", method)
            }
        }

        return when (error.lowercase()) {
            "notfound", "did_not_found" -> DidResolutionResult.Failure.NotFound(
                did = Did(did ?: "did:unknown:unknown"),
                reason = message,
                resolutionMetadata = metadata
            )
            "invalidformat", "invalid_did_format", "invaliddid" -> DidResolutionResult.Failure.InvalidFormat(
                did = did ?: "unknown",
                reason = message ?: "Invalid DID format",
                resolutionMetadata = metadata
            )
            "methodnotregistered", "method_not_registered" -> DidResolutionResult.Failure.MethodNotRegistered(
                method = method ?: "unknown",
                availableMethods = emptyList(),
                resolutionMetadata = metadata
            )
            else -> DidResolutionResult.Failure.ResolutionError(
                did = Did(did ?: "did:unknown:unknown"),
                reason = message ?: error,
                resolutionMetadata = metadata
            )
        }
    }

    /**
     * Normalizes a domain name for did:web.
     *
     * Converts domain to lowercase and validates format.
     *
     * @param domain The domain name
     * @return Normalized domain name
     * @throws IllegalArgumentException if domain is invalid
     */
    fun normalizeDomain(domain: String): String {
        val normalized = domain.lowercase().trim()

        if (normalized.isEmpty()) {
            throw IllegalArgumentException("Domain cannot be empty")
        }

        // Basic validation - domain should not contain protocol or path
        if (normalized.contains("://") || normalized.startsWith("/")) {
            throw IllegalArgumentException("Domain should not contain protocol or path: $domain")
        }

        return normalized
    }

    /**
     * Builds a did:web identifier from a domain.
     *
     * @param domain The domain name (e.g., "example.com")
     * @param path Optional path (e.g., "user:alice")
     * @return DID string (e.g., "did:web:example.com:user:alice")
     */
    fun buildWebDid(domain: String, path: String? = null): String {
        val normalized = normalizeDomain(domain)
        return if (path != null && path.isNotBlank()) {
            "did:web:$normalized:$path"
        } else {
            "did:web:$normalized"
        }
    }
}

