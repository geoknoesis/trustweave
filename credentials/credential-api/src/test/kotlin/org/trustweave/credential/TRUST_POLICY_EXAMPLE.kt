package org.trustweave.credential

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver

/**
 * Examples demonstrating trust evaluator usage in credential verification.
 */

/**
 * Example: Verify with allowlist trust evaluator.
 *
 * Only credentials from trusted issuers will be accepted.
 */
suspend fun exampleAllowlistTrustEvaluator(
    didResolver: DidResolver,
    credential: VerifiableCredential,
    trustedIssuers: Set<Did>
) {
    val service = credentialService(didResolver)

    val evaluator = TrustEvaluator.allowlist(trustedIssuers = trustedIssuers)

    val result = service.verify(
        credential = credential,
        trustPolicy = evaluator
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
 * Example: Verify with blocklist trust evaluator.
 *
 * All issuers are accepted except those in the blocklist.
 */
suspend fun exampleBlocklistTrustEvaluator(
    didResolver: DidResolver,
    credential: VerifiableCredential,
    blockedIssuers: Set<Did>
) {
    val service = credentialService(didResolver)

    val evaluator = TrustEvaluator.blocklist(blockedIssuers = blockedIssuers)

    val result = service.verify(
        credential = credential,
        trustPolicy = evaluator
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
 * Example: Verify without trust evaluator (default behavior).
 *
 * Only cryptographic validity is checked, not issuer trust.
 */
suspend fun exampleNoTrustEvaluator(
    didResolver: DidResolver,
    credential: VerifiableCredential
) {
    val service = credentialService(didResolver)

    val result = service.verify(
        credential = credential,
        trustPolicy = null  // or TrustEvaluator.acceptAll()
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
 * Example: Verify with accept-all trust evaluator (explicit).
 *
 * Same as no trust policy, but makes intent explicit.
 */
suspend fun exampleAcceptAllTrustEvaluator(
    didResolver: DidResolver,
    credential: VerifiableCredential
) {
    val service = credentialService(didResolver)

    val evaluator = TrustEvaluator.acceptAll()

    val result = service.verify(
        credential = credential,
        trustPolicy = evaluator
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
