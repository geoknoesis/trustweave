/**
 * Canonical "Hello World" Credential Issuance and Verification Flow
 * 
 * This example demonstrates the core TrustWeave credential API usage pattern:
 * 1. Create a CredentialService with auto-discovery
 * 2. Issue a credential
 * 3. Verify a credential
 * 
 * This is the recommended entry point for new TrustWeave users.
 */

@file:Suppress("unused", "RemoveRedundantQualifierName")

package com.trustweave.credential

import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.identifiers.*
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.requests.IssuanceRequest
import com.trustweave.credential.requests.VerificationOptions
import com.trustweave.credential.results.VerificationResult
import com.trustweave.credential.results.IssuanceResult
import com.trustweave.did.identifiers.Did
import com.trustweave.did.resolver.DidResolver
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Example: Complete credential issuance and verification flow.
 * 
 * This demonstrates the canonical usage pattern:
 * 
 * ```kotlin
 * // 1. Create service (all proof suites built-in)
 * val service = credentialService(didResolver)
 * 
 * // 2. Issue a credential
 * val credential = service.issue(
 *     IssuanceRequest(
 *         format = ProofSuiteId.VC_LD,
 *         issuer = IssuerId.fromDid(issuerDid),
 *         subject = SubjectId.fromDid(subjectDid),
 *         type = listOf(CredentialType.VerifiableCredential),
 *         claims = mapOf("email" to JsonPrimitive("alice@example.com")),
 *         issuedAt = Instant.now()
 *     )
 * )
 * 
 * // 3. Verify the credential
 * val result = service.verify(credential)
 * when (result) {
 *     is VerificationResult.Valid -> {
 *         println("Credential is valid! Issuer: ${result.issuerDid}")
 *     }
 *     is VerificationResult.Invalid -> {
 *         println("Credential is invalid: ${result.errors}")
 *     }
 * }
 * ```
 */
suspend fun exampleBasicCredentialFlow(
    didResolver: DidResolver,
    issuerDid: Did,
    subjectDid: Did
) {
    // Step 1: Create service with all built-in proof suites
    // All proof suites (VC-LD, VC-JWT, SD-JWT-VC) are always available
    val service = credentialService(didResolver)
    
    // Step 2: Issue a credential
    val issuanceResult: IssuanceResult = service.issue(
        IssuanceRequest(
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(issuerDid),
            credentialSubject = CredentialSubject.fromDid(
                did = subjectDid,
                claims = mapOf(
                    "email" to JsonPrimitive("alice@example.com"),
                    "degree" to JsonPrimitive("Bachelor of Science"),
                    "major" to JsonPrimitive("Computer Science")
                )
            ),
            type = listOf(
                CredentialType.VerifiableCredential,
                CredentialType.Education
            ),
            issuedAt = Clock.System.now(),
            validUntil = Clock.System.now().plus(kotlin.time.Duration.parse("PT${86400 * 365}S")) // 1 year
        )
    )
    
    // Handle issuance result
    val credential: VerifiableCredential = when (issuanceResult) {
        is IssuanceResult.Success -> issuanceResult.credential
        is IssuanceResult.Failure -> throw IllegalStateException("Failed to issue credential: ${issuanceResult.allErrors.joinToString()}")
    }
    
    println("Issued credential: ${credential.id}")
    
    // Step 3: Verify the credential
    val result: VerificationResult = service.verify(
        credential = credential,
        trustPolicy = null,
        options = VerificationOptions(
            checkRevocation = true,
            checkExpiration = true,
            resolveIssuerDid = true
        )
    )
    
    // Step 4: Handle verification result (exhaustive when expression)
    when (result) {
        is VerificationResult.Valid -> {
            println("✓ Credential is valid!")
            println("  Issuer: ${result.issuerDid}")
            println("  Subject: ${result.subjectDid}")
            println("  Issued: ${result.issuedAt}")
            println("  Expires: ${result.expiresAt}")
            
            if (result.warnings.isNotEmpty()) {
                println("  Warnings: ${result.warnings}")
            }
        }
        is VerificationResult.Invalid.Expired -> {
            println("✗ Credential has expired at ${result.expiredAt}")
            println("  Errors: ${result.errors}")
        }
        is VerificationResult.Invalid.Revoked -> {
            println("✗ Credential has been revoked")
            println("  Revoked at: ${result.revokedAt}")
            println("  Reason: ${result.revocationReason}")
        }
        is VerificationResult.Invalid.InvalidProof -> {
            println("✗ Cryptographic proof verification failed")
            println("  Reason: ${result.reason}")
        }
        is VerificationResult.Invalid.UnsupportedFormat -> {
            println("✗ Proof suite not supported: ${result.format}")
            println("  Supported proof suites: ${service.supportedFormats()}")
        }
        is VerificationResult.Invalid.NotYetValid -> {
            println("✗ Credential not yet valid until ${result.validFrom}")
        }
        is VerificationResult.Invalid.InvalidIssuer -> {
            println("✗ Issuer validation failed: ${result.reason}")
        }
        is VerificationResult.Invalid.UntrustedIssuer -> {
            println("✗ Untrusted issuer: ${result.issuerDid.value}")
        }
        is VerificationResult.Invalid.SchemaValidationFailed -> {
            println("✗ Schema validation failed")
            println("  Errors: ${result.validationErrors}")
        }
        is VerificationResult.Invalid.MultipleFailures -> {
            println("✗ Multiple validation failures:")
            result.failures.forEach { failure ->
                println("  - ${failure.javaClass.simpleName}: ${failure.errors}")
            }
        }
    }
}

/**
 * Example: Check service capabilities before using.
 */
suspend fun exampleCapabilityCheck(
    service: CredentialService
) {
    // Check if a proof suite is supported
    if (service.supports(ProofSuiteId.VC_LD)) {
        println("VC-LD proof suite is supported")
    }
    
    // Check proof suite capabilities
    val supportsSelectiveDisclosure = service.supportsCapability(ProofSuiteId.SD_JWT_VC) {
        selectiveDisclosure
    }
    
    if (supportsSelectiveDisclosure) {
        println("SD-JWT-VC supports selective disclosure")
    }
    
    // List all supported proof suites
    val supportedFormats = service.supportedFormats()
    println("Supported proof suites: ${supportedFormats.map { it.value }}")
}

/**
 * Example: Batch verification of multiple credentials.
 */
suspend fun exampleBatchVerification(
    service: CredentialService,
    credentials: List<VerifiableCredential>
) {
    // Verify multiple credentials in parallel
    val results: List<VerificationResult> = service.verify(
        credentials = credentials,
        trustPolicy = null,
        options = VerificationOptions()
    )
    
    // Count valid vs invalid
    val validCount = results.count { it.isValid }
    val invalidCount = results.size - validCount
    
    println("Verified ${credentials.size} credentials:")
    println("  Valid: $validCount")
    println("  Invalid: $invalidCount")
}

