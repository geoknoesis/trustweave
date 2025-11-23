package com.trustweave

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.CredentialVerificationResult
import com.trustweave.did.DidResolutionResult
import com.trustweave.services.VerificationConfig

/**
 * Extension function to verify a credential using TrustWeave.
 * 
 * **Example:**
 * ```kotlin
 * val result = credential.verify(trustweave)
 * ```
 */
suspend fun VerifiableCredential.verify(
    trustweave: TrustWeave,
    config: VerificationConfig = VerificationConfig()
): CredentialVerificationResult {
    return trustweave.credentials.verify(this, config)
}

/**
 * Extension function to resolve a DID using TrustWeave.
 * 
 * **Example:**
 * ```kotlin
 * val result = "did:key:...".resolveDid(trustweave)
 * ```
 */
suspend fun String.resolveDid(trustweave: TrustWeave): DidResolutionResult {
    return trustweave.dids.resolve(this)
}

