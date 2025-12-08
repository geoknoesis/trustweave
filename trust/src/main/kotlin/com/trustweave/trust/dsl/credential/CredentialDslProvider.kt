package com.trustweave.trust.dsl.credential

// TODO: CredentialIssuer and CredentialVerifier are from credential-core - needs migration
// import com.trustweave.credential.issuer.CredentialIssuer
// import com.trustweave.credential.verifier.CredentialVerifier
import com.trustweave.credential.revocation.CredentialRevocationManager
import com.trustweave.credential.model.ProofType
import com.trustweave.credential.schema.SchemaRegistry

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
     * TODO: Replace CredentialIssuer with CredentialService from credential-api
     */
    fun getIssuer(): Any?

    /**
     * Get the credential verifier.
     * TODO: Replace CredentialVerifier with CredentialService from credential-api
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


