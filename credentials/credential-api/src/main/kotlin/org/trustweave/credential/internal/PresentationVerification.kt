package org.trustweave.credential.internal

import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.model.VerificationMethod
import org.trustweave.credential.proof.internal.engines.ProofEngineUtils
import kotlinx.serialization.json.*
import java.util.Base64
import java.security.PublicKey

/**
 * Presentation verification utilities.
 * 
 * Extracted from DefaultCredentialService to improve maintainability and testability.
 */
internal object PresentationVerification {
    /**
     * Verify challenge if required.
     * 
     * @param presentation The presentation to verify
     * @param options Verification options
     * @return VerificationResult.Invalid if challenge verification fails, null if valid
     */
    fun verifyChallenge(
        presentation: VerifiablePresentation,
        options: VerificationOptions
    ): VerificationResult.Invalid.InvalidProof? {
        if (!options.verifyChallenge) {
            return null
        }
        
        if (options.expectedChallenge != null) {
            if (presentation.challenge != options.expectedChallenge) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = presentation.verifiableCredential.first(),
                    reason = "Challenge mismatch",
                    errors = listOf(
                        "Expected challenge '${options.expectedChallenge}', " +
                        "but got '${presentation.challenge}'"
                    )
                )
            }
        }
        // Note: If verifyChallenge is true but no expectedChallenge is provided,
        // we continue verification (might want to add a warning in the future)
        
        return null
    }
    
    /**
     * Verify domain if required.
     * 
     * @param presentation The presentation to verify
     * @param options Verification options
     * @return VerificationResult.Invalid if domain verification fails, null if valid
     */
    fun verifyDomain(
        presentation: VerifiablePresentation,
        options: VerificationOptions
    ): VerificationResult.Invalid.InvalidProof? {
        if (!options.verifyDomain) {
            return null
        }
        
        if (options.expectedDomain != null) {
            if (presentation.domain != options.expectedDomain) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = presentation.verifiableCredential.first(),
                    reason = "Domain mismatch",
                    errors = listOf(
                        "Expected domain '${options.expectedDomain}', " +
                        "but got '${presentation.domain}'"
                    )
                )
            }
        }
        
        return null
    }
    
    /**
     * Verify presentation proof format is supported.
     * 
     * @param proofFormat The proof format
     * @param engines Available proof engines
     * @param presentation The presentation (for error context)
     * @return VerificationResult.Invalid if format not supported, null if supported
     */
    fun verifyProofFormatSupported(
        proofFormat: ProofSuiteId,
        engines: Map<ProofSuiteId, ProofEngine>,
        presentation: VerifiablePresentation
    ): VerificationResult.Invalid.UnsupportedFormat? {
        if (engines[proofFormat] == null) {
            return VerificationResult.Invalid.UnsupportedFormat(
                credential = presentation.verifiableCredential.first(),
                format = proofFormat,
                errors = listOf(
                    "Presentation proof format '${proofFormat.value}' is not supported. " +
                    "Supported formats: ${engines.keys.map { it.value }}"
                )
            )
        }
        return null
    }
    
    /**
     * Resolve verification method for presentation proof.
     * 
     * @param holderIri The holder IRI
     * @param verificationMethodId The verification method ID
     * @param didResolver DID resolver
     * @return VerificationMethod if resolved, null otherwise
     */
    suspend fun resolvePresentationProofVerificationMethod(
        holderIri: Iri,
        verificationMethodId: String,
        didResolver: DidResolver
    ): VerificationMethod? {
        if (!holderIri.isDid) {
            return null
        }
        
        return ProofEngineUtils.resolveVerificationMethod(
            issuerIri = holderIri,
            verificationMethodId = verificationMethodId,
            didResolver = didResolver
        )
    }
    
    /**
     * Verify presentation signature.
     * 
     * Validates the cryptographic signature on a verifiable presentation.
     * Currently only supports Ed25519Signature2020 proof suite.
     * 
     * **Security Considerations:**
     * - Validates signature format before verification
     * - Uses constant-time operations where possible (Java Signature API)
     * - Validates input sizes to prevent DoS attacks
     * 
     * @param canonical Canonicalized presentation document (without proof)
     * @param proofValue Base64url-encoded signature
     * @param verificationMethod Verification method containing the public key
     * @param proofType Proof type (e.g., "Ed25519Signature2020")
     * @return true if signature is valid, false otherwise
     * @throws IllegalArgumentException if input validation fails
     */
    fun verifyPresentationSignature(
        canonical: String,
        proofValue: String,
        verificationMethod: VerificationMethod,
        proofType: String
    ): Boolean {
        // Input validation
        if (proofValue.isBlank()) {
            return false
        }
        
        // Validate canonical document size
        val canonicalBytes = canonical.toByteArray(Charsets.UTF_8)
        if (canonicalBytes.size > SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES) {
            throw IllegalArgumentException(
                "Canonicalized presentation document exceeds maximum size of " +
                "${SecurityConstants.MAX_CANONICALIZED_DOCUMENT_SIZE_BYTES} bytes: " +
                "${canonicalBytes.size} bytes"
            )
        }
        
        // Only support Ed25519Signature2020 for now
        if (proofType != CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020) {
            return false
        }
        
        return try {
            // Extract public key from verification method
            val publicKey = ProofEngineUtils.extractPublicKey(verificationMethod) ?: return false
            
            // Validate public key type
            if (publicKey !is PublicKey) {
                return false
            }
            
            // Decode signature (base64url)
            val signatureBytes = try {
                Base64.getUrlDecoder().decode(proofValue)
            } catch (e: IllegalArgumentException) {
                // Invalid base64url encoding
                return false
            } catch (e: Exception) {
                // Other decoding errors
                return false
            }
            
            // Validate signature length (Ed25519 signatures are always 64 bytes)
            if (signatureBytes.size != SecurityConstants.ED25519_SIGNATURE_LENGTH_BYTES) {
                return false
            }
            
            // Verify Ed25519 signature using Java Security API
            // Note: Java's Signature API uses constant-time operations internally
            val signature = java.security.Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(canonicalBytes)
            signature.verify(signatureBytes)
        } catch (e: IllegalArgumentException) {
            // Re-throw validation exceptions
            throw e
        } catch (e: java.security.InvalidKeyException) {
            // Invalid public key format
            false
        } catch (e: java.security.SignatureException) {
            // Signature verification failure
            false
        } catch (e: Exception) {
            // Other errors (e.g., unsupported algorithm)
            false
        }
    }
}

