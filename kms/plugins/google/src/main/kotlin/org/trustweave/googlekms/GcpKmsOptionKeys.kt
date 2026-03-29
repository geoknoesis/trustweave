package org.trustweave.googlekms

/**
 * Google Cloud KMS-specific option key constants.
 *
 * These constants are used when configuring Google Cloud KMS keys and operations.
 * For cross-provider generic keys (e.g., keyId, description) use
 * [org.trustweave.kms.KmsOptionKeys].
 */
object GcpKmsOptionKeys {
    /** Google Cloud KMS key ring name within the configured location. */
    const val KEY_RING = "keyRing"

    /** Labels to attach to the key (Map<String, String>). */
    const val LABELS = "labels"
}
