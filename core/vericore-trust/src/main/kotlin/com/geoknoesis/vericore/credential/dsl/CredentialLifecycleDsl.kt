package com.geoknoesis.vericore.credential.dsl

import com.geoknoesis.vericore.credential.CredentialVerificationResult
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.wallet.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Credential Lifecycle DSL.
 * 
 * Provides chainable operations for credential lifecycle management.
 * Enables fluent chaining of operations like store, organize, verify.
 * 
 * **Example Usage**:
 * ```kotlin
 * val credential = trustLayer.issue {
 *     credential { ... }
 *     by(issuerDid = "did:key:issuer", keyId = "key-1")
 * }
 * .storeIn(wallet)
 * .organize {
 *     collection("Education") {
 *         add(credentialId)
 *         tag(credentialId, "education", "degree")
 *     }
 * }
 * .verify()
 * ```
 */

/**
 * Result of storing a credential in a wallet.
 */
data class StoredCredential(
    val credential: VerifiableCredential,
    val credentialId: String,
    val wallet: Wallet
)

/**
 * Extension function to store a credential in a wallet.
 * 
 * @param wallet Wallet to store the credential in
 * @return StoredCredential with credential ID for chaining
 */
suspend fun VerifiableCredential.storeIn(wallet: Wallet): StoredCredential = withContext(Dispatchers.IO) {
    val credentialId = wallet.store(this@storeIn)
    StoredCredential(this@storeIn, credentialId, wallet)
}

/**
 * Extension function to organize a stored credential.
 * 
 * @param block Organization builder block
 * @return Organization result
 */
suspend fun StoredCredential.organize(block: WalletOrganizationBuilder.() -> Unit): OrganizationResult {
    return wallet.organize {
        // Add this credential to the organization operations
        block()
    }
}

/**
 * Extension function to verify a credential using trust layer.
 * 
 * @param trustLayer Trust layer context for verification
 * @param block Optional verification builder block
 * @return Verification result
 */
suspend fun VerifiableCredential.verify(
    trustLayer: TrustLayerContext,
    block: VerificationBuilder.() -> Unit = {}
): CredentialVerificationResult {
    val cred = this@verify
    return trustLayer.verify {
        credential(cred)
        block()
    }
}

/**
 * Extension function to verify a stored credential.
 * 
 * @param trustLayer Trust layer context for verification
 * @param block Optional verification builder block
 * @return Verification result
 */
suspend fun StoredCredential.verify(
    trustLayer: TrustLayerContext,
    block: VerificationBuilder.() -> Unit = {}
): CredentialVerificationResult {
    return credential.verify(trustLayer, block)
}

/**
 * Extension function to verify a credential using trust layer config.
 * 
 * @param trustLayer Trust layer config
 * @param block Optional verification builder block
 * @return Verification result
 */
suspend fun VerifiableCredential.verify(
    trustLayer: TrustLayerConfig,
    block: VerificationBuilder.() -> Unit = {}
): CredentialVerificationResult {
    return verify(trustLayer.dsl(), block)
}

/**
 * Extension function to verify a stored credential using trust layer config.
 * 
 * @param trustLayer Trust layer config
 * @param block Optional verification builder block
 * @return Verification result
 */
suspend fun StoredCredential.verify(
    trustLayer: TrustLayerConfig,
    block: VerificationBuilder.() -> Unit = {}
): CredentialVerificationResult {
    return credential.verify(trustLayer, block)
}

/**
 * Extension function to check revocation status of a credential.
 * 
 * @param trustLayer Trust layer context
 * @return Revocation status
 */
suspend fun VerifiableCredential.checkRevocation(trustLayer: TrustLayerContext): com.geoknoesis.vericore.credential.revocation.RevocationStatus {
    return trustLayer.revocation { }.check(this@checkRevocation)
}

/**
 * Extension function to check revocation status of a stored credential.
 * 
 * @param trustLayer Trust layer context
 * @return Revocation status
 */
suspend fun StoredCredential.checkRevocation(trustLayer: TrustLayerContext): com.geoknoesis.vericore.credential.revocation.RevocationStatus {
    return credential.checkRevocation(trustLayer)
}

/**
 * Extension function to validate a credential against a schema.
 * 
 * @param trustLayer Trust layer context
 * @param schemaId Schema ID to validate against
 * @return Schema validation result
 */
suspend fun VerifiableCredential.validateSchema(
    trustLayer: TrustLayerContext,
    schemaId: String
): com.geoknoesis.vericore.credential.schema.SchemaValidationResult {
    return trustLayer.schema(schemaId).validate(this@validateSchema)
}

/**
 * Extension function to validate a stored credential against a schema.
 * 
 * @param trustLayer Trust layer context
 * @param schemaId Schema ID to validate against
 * @return Schema validation result
 */
suspend fun StoredCredential.validateSchema(
    trustLayer: TrustLayerContext,
    schemaId: String
): com.geoknoesis.vericore.credential.schema.SchemaValidationResult {
    return credential.validateSchema(trustLayer, schemaId)
}

