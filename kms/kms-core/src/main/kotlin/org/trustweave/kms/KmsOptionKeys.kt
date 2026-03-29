package org.trustweave.kms

/**
 * Cross-provider constants for KMS operation option keys.
 *
 * These constants apply to multiple KMS providers and should be used instead of string
 * literals to prevent typos. Provider-specific constants live in each plugin module:
 * - AWS: `AwsKmsOptionKeys`
 * - Google Cloud: `GcpKmsOptionKeys`
 * - HashiCorp Vault: `HashiCorpKmsOptionKeys`
 *
 * **Example Usage:**
 * ```kotlin
 * val result = kms.generateKey(
 *     algorithm = Algorithm.Ed25519,
 *     options = mapOf(
 *         KmsOptionKeys.KEY_ID to "my-key-id",
 *         KmsOptionKeys.DESCRIPTION to "My key description"
 *     )
 * )
 * ```
 */
object KmsOptionKeys {
    /** Key identifier or name for the key. Used by: AWS, Google, InMemory, WaltID. */
    const val KEY_ID = "keyId"

    /** Key name (alternative to keyId). Used by: Azure, HashiCorp. */
    const val KEY_NAME = "keyName"

    /** Key name (short form). Used by: IBM, Fortanix, CyberArk, Thales. */
    const val NAME = "name"

    /** Human-readable description of the key. Used by: AWS, Azure, IBM, Fortanix, Thales. */
    const val DESCRIPTION = "description"

    /** Whether the key material can be exported. Used by: HashiCorp, Thales. */
    const val EXPORTABLE = "exportable"

    /** Whether the key material can be extracted. Used by: IBM. */
    const val EXTRACTABLE = "extractable"
}
