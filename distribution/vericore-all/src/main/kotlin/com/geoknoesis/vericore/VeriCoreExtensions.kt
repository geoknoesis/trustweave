package com.geoknoesis.vericore

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.CredentialVerificationResult
import com.geoknoesis.vericore.did.DidResolutionResult
import com.geoknoesis.vericore.services.VerificationConfig

/**
 * Extension function to verify a credential using VeriCore.
 * 
 * **Example:**
 * ```kotlin
 * val result = credential.verify(vericore)
 * ```
 */
suspend fun VerifiableCredential.verify(
    vericore: VeriCore,
    config: VerificationConfig = VerificationConfig()
): CredentialVerificationResult {
    return vericore.credentials.verify(this, config)
}

/**
 * Extension function to resolve a DID using VeriCore.
 * 
 * **Example:**
 * ```kotlin
 * val result = "did:key:...".resolveDid(vericore)
 * ```
 */
suspend fun String.resolveDid(vericore: VeriCore): DidResolutionResult {
    return vericore.dids.resolve(this)
}

