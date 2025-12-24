package org.trustweave.trust.dsl.credential

// CredentialService from credential-api is used for all credential operations
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.schema.SchemaRegistry

/**
 * Credential DSL Provider Interface.
 *
 * Provides a minimal abstraction for credential DSL operations.
 * This makes credentials:core self-contained and focused only on credential operations.
 *
 * All types used here are from credentials:core itself - no external dependencies.
 */
interface CredentialDslProvider {
    /**
     * Get the credential issuer.
     * Uses CredentialService from credential-api for issuance
     */
    fun getIssuer(): Any?

    /**
     * Get the credential verifier.
     * Uses CredentialService from credential-api for verification
     */
    fun getVerifier(): Any?

    /**
     * Get the revocation manager for revocation checking.
     * Returns null if not configured.
     */
    fun getRevocationManager(): CredentialRevocationManager?

    /**
     * Get the schema registry for schema operations.
     * Returns null if not configured.
     */
    fun getSchemaRegistry(): SchemaRegistry?

    /**
     * Get the default proof type.
     *
     * Returns a type-safe ProofType instead of a string to prevent errors
     * and improve API safety.
     */
    fun getDefaultProofType(): ProofType
}


