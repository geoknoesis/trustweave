package org.trustweave.credential

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.did.issueForDid
import org.trustweave.credential.trust.TrustPolicy
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Example: Complete credential issuance and verification flow using the new simplified API.
 * 
 * This demonstrates the recommended usage pattern:
 * 
 * ```kotlin
 * // 1. Create service (all proof suites built-in)
 * val service = credentialService(didResolver)
 * 
 * // 2. Issue a credential
 * val result = service.issue(IssuanceRequest(...))
 * 
 * // 3. Verify the credential
 * val verification = service.verify(credential)
 * ```
 */
suspend fun exampleCredentialFlow(
    didResolver: DidResolver,
    issuerDid: Did,
    subjectDid: Did
) {
    // Step 1: Create service with all built-in proof suites
    // All proof suites (VC-LD, VC-JWT, SD-JWT-VC) are always available
    val service = credentialService(didResolver)
    
    // Step 2: Issue a credential using the simplified API
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
                CredentialType.Custom("EducationCredential")
            ),
            issuedAt = Clock.System.now(),
            validUntil = Clock.System.now().plus(kotlin.time.Duration.parse("PT${86400 * 365}S")) // 1 year
        )
    )
    
    // Step 3: Handle issuance result (exhaustive when expression)
    val credential: VerifiableCredential = when (issuanceResult) {
        is IssuanceResult.Success -> {
            println("✅ Issued credential: ${issuanceResult.credential.id}")
            issuanceResult.credential
        }
        is IssuanceResult.Failure.UnsupportedFormat -> {
            error("Format not supported: ${issuanceResult.format}. Supported: ${issuanceResult.supportedFormats}")
        }
        is IssuanceResult.Failure.AdapterNotReady -> {
            error("Adapter not ready: ${issuanceResult.reason}")
        }
        is IssuanceResult.Failure.InvalidRequest -> {
            error("Invalid request: ${issuanceResult.field} - ${issuanceResult.reason}")
        }
        is IssuanceResult.Failure.AdapterError -> {
            error("Adapter error: ${issuanceResult.reason}")
        }
        is IssuanceResult.Failure.MultipleFailures -> {
            error("Multiple failures: ${issuanceResult.allErrors.joinToString()}")
        }
    }
    
    // Step 4: Verify the credential with trust policy
    val trustPolicy = TrustPolicy.allowlist(
        trustedIssuers = setOf(issuerDid)
    )
    
    val verificationResult: VerificationResult = service.verify(
        credential = credential,
        trustPolicy = trustPolicy,
        options = VerificationOptions(
            checkRevocation = true,
            checkExpiration = true,
            resolveIssuerDid = true
        )
    )
    
    // Step 5: Handle verification result (exhaustive when expression)
    when (verificationResult) {
        is VerificationResult.Valid -> {
            println("✅ Credential is valid!")
            println("   Issuer: ${verificationResult.issuerDid}")
            println("   Subject: ${verificationResult.subjectDid}")
            println("   Issued: ${verificationResult.issuedAt}")
            println("   Expires: ${verificationResult.expiresAt}")
        }
        is VerificationResult.Invalid.InvalidProof -> {
            println("❌ Invalid proof: ${verificationResult.reason}")
        }
        is VerificationResult.Invalid.Expired -> {
            println("❌ Credential expired at ${verificationResult.expiredAt}")
        }
        is VerificationResult.Invalid.Revoked -> {
            println("❌ Credential revoked: ${verificationResult.revocationReason}")
        }
        is VerificationResult.Invalid.InvalidIssuer -> {
            println("❌ Invalid issuer: ${verificationResult.reason}")
        }
        is VerificationResult.Invalid.UntrustedIssuer -> {
            println("❌ Untrusted issuer: ${verificationResult.issuerDid.value}")
        }
        is VerificationResult.Invalid.UnsupportedFormat -> {
            println("❌ Unsupported format: ${verificationResult.format}")
        }
        is VerificationResult.Invalid.SchemaValidationFailed -> {
            println("❌ Schema validation failed: ${verificationResult.validationErrors.joinToString()}")
        }
        is VerificationResult.Invalid.MultipleFailures -> {
            println("❌ Multiple failures: ${verificationResult.allErrors.joinToString()}")
        }
        is VerificationResult.Invalid.NotYetValid -> {
            println("❌ Credential not yet valid until ${verificationResult.validFrom}")
        }
    }
}

/**
 * Example: Using the DID extension for simplified issuance.
 * 
 * The issueForDid() extension handles DID resolution automatically
 * and returns proper Result types instead of throwing exceptions.
 */
suspend fun exampleDidBasedIssuance(
    didResolver: DidResolver,
    issuerDid: Did,
    subjectDid: Did
) {
    val service = credentialService(didResolver)
    
    // Issue credential for a DID subject
    // This automatically resolves the subject DID and handles errors gracefully
    val result = service.issueForDid(
        didResolver = didResolver,
        subjectDid = subjectDid,
        issuerDid = issuerDid,
        type = listOf(
            CredentialType.VerifiableCredential,
            CredentialType.Custom("PersonCredential")
        ),
        claims = mapOf(
            "name" to JsonPrimitive("Alice"),
            "email" to JsonPrimitive("alice@example.com")
        ),
        format = ProofSuiteId.VC_LD
    )
    
    when (result) {
        is IssuanceResult.Success -> {
            println("✅ Issued credential for DID: ${subjectDid.value}")
            println("   Credential ID: ${result.credential.id}")
        }
        is IssuanceResult.Failure -> {
            // All failure cases are handled - no exceptions thrown
            println("❌ Failed to issue credential: ${result.allErrors.joinToString()}")
        }
    }
}

/**
 * Example: Batch verification of multiple credentials with trust policy.
 */
suspend fun exampleBatchVerification(
    didResolver: DidResolver,
    credentials: List<VerifiableCredential>,
    trustedIssuers: Set<Did>
) {
    val service = credentialService(didResolver)
    
    // Create trust policy
    val trustPolicy = TrustPolicy.allowlist(trustedIssuers = trustedIssuers)
    
    // Verify multiple credentials in parallel
    val results = service.verify(
        credentials = credentials,
        trustPolicy = trustPolicy,
        options = VerificationOptions(
            checkRevocation = true,
            checkExpiration = true
        )
    )
    
    // Process results
    val validCount = results.count { it is VerificationResult.Valid }
    val invalidCount = results.count { it is VerificationResult.Invalid }
    
    println("Verified ${credentials.size} credentials:")
    println("  ✅ Valid: $validCount")
    println("  ❌ Invalid: $invalidCount")
    
    // Get all valid credentials
    val validCredentials = results
        .filterIsInstance<VerificationResult.Valid>()
        .map { it.credential }
}

/**
 * Example: Check credential status (revocation, expiration).
 */
suspend fun exampleCheckStatus(
    didResolver: DidResolver,
    credential: VerifiableCredential
) {
    val service = credentialService(didResolver)
    
    val status = service.status(credential)
    
    when {
        status.valid -> {
            println("✅ Credential is valid")
        }
        status.revoked -> {
            println("❌ Credential has been revoked")
        }
        status.expired -> {
            println("❌ Credential has expired")
        }
        status.notYetValid -> {
            println("⏳ Credential is not yet valid")
        }
    }
}

/**
 * Example: Check supported formats.
 * 
 * All proof formats are built-in and always available.
 */
fun exampleCheckSupportedFormats(didResolver: DidResolver) {
    val service = credentialService(didResolver)
    
    // Get all supported formats
    val formats = service.supportedFormats()
    println("Supported formats: ${formats.joinToString { it.value }}")
    
    // Check if a specific format is supported
    val vcLdSupported = service.supports(ProofSuiteId.VC_LD)
    val sdJwtVcSupported = service.supports(ProofSuiteId.SD_JWT_VC)
    
    println("VC-LD supported: $vcLdSupported")
    println("SD-JWT-VC supported: $sdJwtVcSupported")
}

