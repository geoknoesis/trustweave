package org.trustweave.did.verification

import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionMetadata
import kotlinx.datetime.Clock

/**
 * DID Document Verification Service.
 *
 * Verifies cryptographic proofs and signatures on DID documents to ensure
 * authenticity and integrity.
 *
 * **Verification Checks:**
 * - Document structure validation
 * - Verification method structure validation
 * - Service structure validation
 * - Integrity verification (digest checking)
 * - Signature verification (method-specific)
 *
 * **Example Usage:**
 * ```kotlin
 * val verificationService = DefaultDidDocumentVerificationService(
 *     canonicalizationService = JsonLdCanonicalizationService()
 * )
 *
 * val result = verificationService.verifyDocument(
 *     document = didDocument,
 *     resolutionMetadata = resolutionMetadata
 * )
 *
 * if (!result.valid) {
 *     println("Verification failed: ${result.errors}")
 * }
 * ```
 */
interface DidDocumentVerificationService {
    /**
     * Verify DID document integrity and authenticity.
     *
     * @param document The DID document to verify
     * @param resolutionMetadata Resolution metadata (may contain digest for integrity check)
     * @return Verification result with validity status and any errors/warnings
     */
    suspend fun verifyDocument(
        document: DidDocument,
        resolutionMetadata: DidResolutionMetadata
    ): VerificationResult
    
    /**
     * Verify document signature (if method supports it).
     *
     * @param document The DID document
     * @param method The DID method name
     * @return true if signature is valid, false otherwise
     */
    suspend fun verifyDocumentSignature(
        document: DidDocument,
        method: String
    ): Boolean
    
    /**
     * Verify verification method signatures.
     *
     * @param method The verification method
     * @param signature The signature to verify
     * @param data The data that was signed
     * @return true if signature is valid
     */
    suspend fun verifyVerificationMethod(
        method: org.trustweave.did.model.VerificationMethod,
        signature: String,
        data: ByteArray
    ): Boolean
}

/**
 * Verification result.
 */
data class VerificationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val verifiedAt: kotlinx.datetime.Instant = Clock.System.now(),
    val verificationMethod: String? = null
)

/**
 * Default implementation of document verification service.
 */
class DefaultDidDocumentVerificationService(
    private val canonicalizationService: DidDocumentCanonicalizationService
) : DidDocumentVerificationService {

    private val logger = org.trustweave.did.util.DidLogging.getLogger(DefaultDidDocumentVerificationService::class.java)
    
    override suspend fun verifyDocument(
        document: DidDocument,
        resolutionMetadata: DidResolutionMetadata
    ): VerificationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        // 1. Verify document structure
        if (document.id.value.isEmpty()) {
            errors.add("Document ID is empty")
        }
        
        // 2. Verify verification methods
        document.verificationMethod.forEach { method ->
            if (!verifyVerificationMethodStructure(method)) {
                errors.add("Invalid verification method structure: ${method.id}")
            }
        }
        
        // 3. Verify services
        document.service.forEach { service ->
            if (!verifyServiceStructure(service)) {
                warnings.add("Invalid service structure: ${service.id}")
            }
        }
        
        // 4. Verify integrity (if digest provided)
        // Both computedDigest and expectedDigest are multibase-encoded strings (base64url
        // with 'u' prefix). Decode both to raw bytes before comparing so that the comparison
        // operates on the actual digest bytes, not on the ASCII encoding of the encoded form.
        resolutionMetadata.properties["digest"]?.let { expectedDigest ->
            if (!canonicalizationService.isConformant) {
                // Skip digest check — the canonicalization service is not URDNA2015-conformant.
                // Performing the check with a non-conformant implementation would always produce
                // a misleading "digest mismatch" error against any external resolver.
                warnings.add(
                    "Document digest check skipped: canonicalization service is not URDNA2015-conformant. " +
                    "Digest verification requires a conformant implementation."
                )
            } else {
                // digestChecked tracks whether a byte-level comparison was actually performed so
                // that "digest mismatch" is only added when the digest was computed and found
                // unequal — not when an earlier exception already added a more specific error.
                var digestChecked = false
                val digestOk = try {
                    val computedDigest = canonicalizationService.computeDigest(document)
                    val computedBytes = decodeMultibaseDigest(computedDigest)
                    val expectedBytes = decodeMultibaseDigest(expectedDigest)
                    digestChecked = true
                    java.security.MessageDigest.isEqual(computedBytes, expectedBytes)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: IllegalArgumentException) {
                    // An unsupported multibase prefix means we cannot decode the digest.
                    // Fail closed: do not attempt a comparison on raw encoded strings.
                    errors.add("Document digest uses unsupported encoding: ${e.message}")
                    false
                } catch (e: Exception) {
                    // computeDigest (or any other step) threw unexpectedly — record the failure
                    // rather than propagating an exception out of verifyDocument.
                    errors.add("Document digest computation failed: ${e.message}")
                    false
                }
                // Only add "digest mismatch" when the comparison was actually performed and failed.
                // When digestChecked is false an error was already recorded above.
                if (digestChecked && !digestOk) errors.add("Document digest mismatch")
            }
        }
        
        return VerificationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
    
    override suspend fun verifyDocumentSignature(
        document: DidDocument,
        method: String
    ): Boolean {
        if (!document.id.value.startsWith("did:$method:")) {
            logger.warn(
                "verifyDocumentSignature called with method '$method' but document id is " +
                    "'${document.id.value}' — returning false"
            )
            return false
        }
        // Method-specific signature verification
        return when (method) {
            "ion" -> verifyIonDocumentSignature(document)
            "key" -> verifyKeyDocumentSignature(document)
            else -> false  // Unknown DID method — cannot verify, fail-closed
        }
    }
    
    override suspend fun verifyVerificationMethod(
        method: org.trustweave.did.model.VerificationMethod,
        signature: String,
        data: ByteArray
    ): Boolean {
        // Extract public key and verify signature
        // Implementation depends on key type
        return when (method.type) {
            "Ed25519VerificationKey2020" -> {
                verifyEd25519Signature(method, signature, data)
            }
            else -> {
                false  // Unsupported key type
            }
        }
    }
    
    private fun verifyVerificationMethodStructure(
        method: org.trustweave.did.model.VerificationMethod
    ): Boolean {
        return method.id.value.isNotEmpty() &&
               method.type.isNotEmpty() &&
               (method.publicKeyJwk != null || method.publicKeyMultibase != null)
    }
    
    private fun verifyServiceStructure(
        service: org.trustweave.did.model.DidService
    ): Boolean {
        return service.id.isNotEmpty() && service.type.isNotEmpty()
    }
    
    private suspend fun verifyIonDocumentSignature(document: DidDocument): Boolean {
        return false  // ION proof chain verification not implemented — fail-closed
    }
    
    private suspend fun verifyKeyDocumentSignature(document: DidDocument): Boolean {
        // did:key is self-certifying: the DID encodes the public key, so any alteration
        // to the verification method would produce a different DID. Verify by checking
        // that at least one verification method's publicKeyMultibase matches the DID identifier
        // AND that the verification method's controller matches the document's own DID.
        val multibaseFromDid = document.id.value
            .substringAfter("did:key:", "")
            .substringBefore("?")
            .substringBefore("#")
        if (multibaseFromDid.isEmpty()) return false
        // did:key is self-certifying: exactly one VM controlled by the DID itself must exist,
        // and its public key must match the key encoded in the DID. Allowing any() would
        // permit an attacker to inject additional VMs that pass the check.
        val matchingVms = document.verificationMethod.filter { vm ->
            vm.controller.value == document.id.value
        }
        if (matchingVms.isEmpty()) return false
        // All matching VMs have null publicKeyMultibase — JWK-only key, cannot verify via multibase
        if (matchingVms.all { it.publicKeyMultibase == null }) {
            logger.warn(
                "verifyKeyDocumentSignature: all VMs for ${document.id.value} use JWK-only encoding; " +
                    "multibase self-certification check cannot be performed — returning false"
            )
            return false
        }
        return matchingVms.size == 1 && matchingVms.first().publicKeyMultibase == multibaseFromDid
    }

    /**
     * Decodes a multibase-encoded digest string to raw bytes.
     *
     * Supports:
     * - 'u' prefix: base64url without padding (RFC 4648 §5)
     * - 'U' prefix: base64url with padding
     * - 'f' prefix: lowercase hex
     * - 'F' prefix: uppercase hex
     *
     * @throws IllegalArgumentException for unsupported multibase prefixes
     */
    private fun decodeMultibaseDigest(encoded: String): ByteArray {
        require(encoded.isNotEmpty()) { "Digest string is empty" }
        return when (encoded[0]) {
            'u' -> {
                // base64url without padding: JDK's getUrlDecoder() requires canonical padding,
                // so strip any accidental trailing '=' then add the correct amount.
                val s = encoded.substring(1).trimEnd('=')
                val padded = s + "=".repeat((4 - s.length % 4) % 4)
                java.util.Base64.getUrlDecoder().decode(padded)
            }
            'U' -> {
                // base64url with padding: JDK's getUrlDecoder() does NOT accept '=' padding
                // per the JDK Javadoc, so normalise by stripping existing padding and re-adding
                // the canonical amount.
                val s = encoded.substring(1).trimEnd('=')
                val padded = s + "=".repeat((4 - s.length % 4) % 4)
                java.util.Base64.getUrlDecoder().decode(padded)
            }
            'f' -> {
                val hex = encoded.substring(1)
                require(hex.length % 2 == 0) { "Hex digest must have even number of digits, got ${hex.length}" }
                hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            'F' -> {
                val hex = encoded.substring(1)
                require(hex.length % 2 == 0) { "Hex digest must have even number of digits, got ${hex.length}" }
                hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            else -> throw IllegalArgumentException("Unsupported multibase prefix: ${encoded[0]}")
        }
    }
    
    private suspend fun verifyEd25519Signature(
        method: org.trustweave.did.model.VerificationMethod,
        signature: String,
        data: ByteArray
    ): Boolean {
        logger.warn("Ed25519 signature verification is not yet implemented. Returning false.")
        return false
    }
}

