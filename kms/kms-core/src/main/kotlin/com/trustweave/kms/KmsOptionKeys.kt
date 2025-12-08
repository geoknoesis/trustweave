package com.trustweave.kms

/**
 * Constants for KMS operation option keys.
 * 
 * These constants should be used instead of string literals to prevent typos
 * and ensure consistency across all KMS plugins.
 * 
 * **Example Usage:**
 * ```kotlin
 * val result = kms.generateKey(
 *     algorithm = Algorithm.Ed25519,
 *     options = mapOf(
 *         KmsOptionKeys.KEY_ID to "my-key-id",
 *         KmsOptionKeys.DESCRIPTION to "My key description",
 *         KmsOptionKeys.TAGS to mapOf("environment" to "production")
 *     )
 * )
 * ```
 */
object KmsOptionKeys {
    /**
     * Key identifier or name for the key.
     * Used by: AWS, Google, InMemory, WaltID
     */
    const val KEY_ID = "keyId"
    
    /**
     * Key name (alternative to keyId, used by some providers).
     * Used by: Azure, Hashicorp
     */
    const val KEY_NAME = "keyName"
    
    /**
     * Key name (used by IBM, Fortanix, CyberArk, Thales).
     */
    const val NAME = "name"
    
    /**
     * Human-readable description of the key.
     * Used by: AWS, Azure, IBM, Fortanix
     */
    const val DESCRIPTION = "description"
    
    /**
     * Tags/metadata for the key (key-value pairs).
     * Used by: AWS
     */
    const val TAGS = "tags"
    
    /**
     * Labels/metadata for the key (key-value pairs).
     * Used by: Google
     */
    const val LABELS = "labels"
    
    /**
     * Alias for the key.
     * Used by: AWS
     */
    const val ALIAS = "alias"
    
    /**
     * Whether the key is exportable.
     * Used by: Hashicorp
     */
    const val EXPORTABLE = "exportable"
    
    /**
     * Whether the key is extractable.
     * Used by: IBM
     */
    const val EXTRACTABLE = "extractable"
    
    /**
     * Whether to allow plaintext backup.
     * Used by: Hashicorp
     */
    const val ALLOW_PLAINTEXT_BACKUP = "allowPlaintextBackup"
    
    /**
     * Whether to enable automatic key rotation.
     * Used by: AWS
     */
    const val ENABLE_AUTOMATIC_ROTATION = "enableAutomaticRotation"
    
    /**
     * Key ring name (Google Cloud KMS specific).
     * Used by: Google
     */
    const val KEY_RING = "keyRing"
    
    // Configuration option keys (used in config builders)
    
    /**
     * AWS region.
     * Used by: AWS config
     */
    const val REGION = "region"
    
    /**
     * AWS access key ID.
     * Used by: AWS config
     */
    const val ACCESS_KEY_ID = "accessKeyId"
    
    /**
     * AWS secret access key.
     * Used by: AWS config
     */
    const val SECRET_ACCESS_KEY = "secretAccessKey"
    
    /**
     * AWS session token.
     * Used by: AWS config
     */
    const val SESSION_TOKEN = "sessionToken"
    
    /**
     * AWS endpoint override.
     * Used by: AWS config
     */
    const val ENDPOINT_OVERRIDE = "endpointOverride"
    
    /**
     * AWS pending window in days for key deletion.
     * Used by: AWS config
     */
    const val PENDING_WINDOW_IN_DAYS = "pendingWindowInDays"
}

