package org.trustweave.core.plugin

/**
 * Plugin type enumeration.
 *
 * Represents the plugin interface categories that TrustWeave framework supports.
 * These are framework-level plugin types, not domain-specific implementations.
 *
 * Each enum value corresponds to a plugin interface in the TrustWeave framework:
 * - `BLOCKCHAIN` → `BlockchainAnchorClient` interface (in `anchors:core` module)
 * - `CREDENTIAL_SERVICE` → `CredentialService` interface (in `credentials:core` module)
 * - `DID_METHOD` → `DidMethod` interface (in `did:core` module)
 * - `KMS` → `KeyManagementService` interface (in `kms:core` module)
 * - etc.
 *
 * **Important Distinction:**
 * - **PluginType enum** = Framework plugin architecture (what plugin interfaces exist)
 * - **Domain-specific capabilities** = Implementation details (which specific blockchains, DID methods, proof types)
 *
 * For example:
 * - `PluginType.CREDENTIAL_SERVICE` means "this plugin implements CredentialService interface"
 * - `supportedProofTypes: ["Ed25519Signature2020"]` is a domain-specific capability handled by the CredentialService interface
 *
 * This enum is part of the framework's plugin architecture definition, not domain-specific business logic.
 */
enum class PluginType {
    /** Blockchain adapter plugin (implements BlockchainAnchorClient) */
    BLOCKCHAIN,

    /** Credential storage/wallet plugin */
    CREDENTIAL_STORE,

    /** Credential service plugin (implements CredentialService) */
    CREDENTIAL_SERVICE,

    /** Trust service plugin */
    TRUST_SERVICE,

    /** Schema validator plugin */
    SCHEMA_VALIDATOR,

    /** Proof generator plugin */
    PROOF_GENERATOR,

    /** Revocation service plugin */
    REVOCATION_SERVICE,

    /** DID method plugin (implements DidMethod) */
    DID_METHOD,

    /** Key management service plugin (implements KeyManagementService) */
    KMS,

    /** Presentation protocol plugin */
    PRESENTATION_PROTOCOL,

    /** Query engine plugin */
    QUERY_ENGINE
}
