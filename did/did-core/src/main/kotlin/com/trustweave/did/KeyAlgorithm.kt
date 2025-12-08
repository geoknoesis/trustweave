package com.trustweave.did

/**
 * Supported cryptographic algorithms for DID key generation.
 *
 * Extracted from [DidCreationOptions] to improve discoverability and reduce nesting.
 *
 * **Example Usage:**
 * ```kotlin
 * val options = DidCreationOptions(
 *     algorithm = KeyAlgorithm.ED25519,
 *     purposes = listOf(KeyPurpose.AUTHENTICATION)
 * )
 * ```
 */
enum class KeyAlgorithm(val algorithmName: String) {
    /** Ed25519 signature algorithm (recommended) */
    ED25519("Ed25519"),

    /** secp256k1 (Bitcoin/Ethereum curve) */
    SECP256K1("secp256k1"),

    /** P-256 (NIST curve) */
    P256("P-256"),

    /** P-384 (NIST curve) */
    P384("P-384"),

    /** P-521 (NIST curve) */
    P521("P-521");

    companion object {
        /**
         * Gets algorithm by name (case-insensitive).
         *
         * @param name Algorithm name
         * @return KeyAlgorithm or null if not found
         */
        fun fromName(name: String): KeyAlgorithm? {
            return values().firstOrNull {
                it.algorithmName.equals(name, ignoreCase = true)
            }
        }
    }
}

