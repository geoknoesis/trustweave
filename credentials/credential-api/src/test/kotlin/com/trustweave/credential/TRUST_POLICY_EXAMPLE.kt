package com.trustweave.credential

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.trust.TrustPolicy
import com.trustweave.credential.results.VerificationResult
import com.trustweave.did.identifiers.Did
import com.trustweave.did.resolver.DidResolver

/**
 * Examples demonstrating trust policy usage in credential verification.
 */

/**
 * Example: Verify with allowlist trust policy.
 * 
 * Only credentials from trusted issuers will be accepted.
 */
suspend fun exampleAllowlistTrustPolicy(
    didResolver: DidResolver,
    credential: VerifiableCredential,
    trustedIssuers: Set<Did>
) {
    val service = credentialService(didResolver)
    
    // Create allowlist policy
    val trustPolicy = TrustPolicy.allowlist(trustedIssuers = trustedIssuers)
    
    val result = service.verify(
        credential = credential,
        trustPolicy = trustPolicy
    )
    
    when (result) {
        is VerificationResult.Valid -> {
            println("✅ Credential is valid and issuer is trusted")
        }
        is VerificationResult.Invalid.UntrustedIssuer -> {
            println("❌ Issuer ${result.issuerDid.value} is not in the allowlist")
        }
        is VerificationResult.Invalid -> {
            println("❌ Verification failed: ${result.allErrors.joinToString()}")
        }
    }
}

/**
 * Example: Verify with blocklist trust policy.
 * 
 * All issuers are accepted except those in the blocklist.
 */
suspend fun exampleBlocklistTrustPolicy(
    didResolver: DidResolver,
    credential: VerifiableCredential,
    blockedIssuers: Set<Did>
) {
    val service = credentialService(didResolver)
    
    // Create blocklist policy
    val trustPolicy = TrustPolicy.blocklist(blockedIssuers = blockedIssuers)
    
    val result = service.verify(
        credential = credential,
        trustPolicy = trustPolicy
    )
    
    when (result) {
        is VerificationResult.Valid -> {
            println("✅ Credential is valid and issuer is not blocked")
        }
        is VerificationResult.Invalid.UntrustedIssuer -> {
            println("❌ Issuer ${result.issuerDid.value} is in the blocklist")
        }
        is VerificationResult.Invalid -> {
            println("❌ Verification failed: ${result.allErrors.joinToString()}")
        }
    }
}

/**
 * Example: Verify without trust policy (default behavior).
 * 
 * Only cryptographic validity is checked, not issuer trust.
 */
suspend fun exampleNoTrustPolicy(
    didResolver: DidResolver,
    credential: VerifiableCredential
) {
    val service = credentialService(didResolver)
    
    // No trust policy - only cryptographic validity checked
    val result = service.verify(
        credential = credential,
        trustPolicy = null  // or TrustPolicy.acceptAll()
    )
    
    when (result) {
        is VerificationResult.Valid -> {
            println("✅ Credential is cryptographically valid")
            // Note: This doesn't mean the issuer is trusted!
        }
        is VerificationResult.Invalid -> {
            println("❌ Verification failed: ${result.allErrors.joinToString()}")
        }
    }
}

/**
 * Example: Verify with accept-all trust policy (explicit).
 * 
 * Same as no trust policy, but makes intent explicit.
 */
suspend fun exampleAcceptAllTrustPolicy(
    didResolver: DidResolver,
    credential: VerifiableCredential
) {
    val service = credentialService(didResolver)
    
    // Explicitly accept all issuers
    val trustPolicy = TrustPolicy.acceptAll()
    
    val result = service.verify(
        credential = credential,
        trustPolicy = trustPolicy
    )
    
    // Same behavior as no trust policy
    when (result) {
        is VerificationResult.Valid -> {
            println("✅ Credential is valid (no trust check)")
        }
        is VerificationResult.Invalid -> {
            println("❌ Verification failed: ${result.allErrors.joinToString()}")
        }
    }
}

