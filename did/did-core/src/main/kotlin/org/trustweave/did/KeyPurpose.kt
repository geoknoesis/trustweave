package org.trustweave.did

/**
 * Key purposes as defined in DID Core specification.
 *
 * Extracted from [DidCreationOptions] to improve discoverability and reduce nesting.
 *
 * **Example Usage:**
 * ```kotlin
 * val options = DidCreationOptions(
 *     algorithm = KeyAlgorithm.ED25519,
 *     purposes = listOf(KeyPurpose.AUTHENTICATION, KeyPurpose.ASSERTION)
 * )
 * ```
 */
enum class KeyPurpose(val purposeName: String) {
    /** For authentication (proving control of DID) */
    AUTHENTICATION("authentication"),

    /**
     * For making assertions (issuing credentials).
     *
     * Note: The enum name is `ASSERTION` but the DID spec purpose name is "assertionMethod".
     * This is intentional to keep the enum name concise while matching the spec.
     */
    ASSERTION("assertionMethod"),

    /** For key agreement (encryption) */
    KEY_AGREEMENT("keyAgreement"),

    /** For invoking capabilities */
    CAPABILITY_INVOCATION("capabilityInvocation"),

    /** For delegating capabilities */
    CAPABILITY_DELEGATION("capabilityDelegation");

    companion object {
        /**
         * Gets purpose by name (case-insensitive).
         *
         * @param name Purpose name
         * @return KeyPurpose or null if not found
         */
        fun fromName(name: String): KeyPurpose? {
            return values().firstOrNull {
                it.purposeName.equals(name, ignoreCase = true)
            }
        }
    }
}

