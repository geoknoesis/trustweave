package org.trustweave.hashicorpkms

/**
 * HashiCorp Vault KMS-specific option key constants.
 *
 * These constants are used when configuring HashiCorp Vault Transit secrets engine keys.
 * For cross-provider generic keys (e.g., keyName, exportable, description) use
 * [org.trustweave.kms.KmsOptionKeys].
 */
object HashiCorpKmsOptionKeys {
    /** Allow the key material to be backed up in plaintext. */
    const val ALLOW_PLAINTEXT_BACKUP = "allowPlaintextBackup"
}
