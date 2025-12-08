package com.trustweave.kms

/**
 * Represents a cryptographic algorithm supported by a KMS.
 *
 * This is a sealed class to ensure type safety and allow for future extensions
 * (e.g., algorithm-specific parameters, key sizes, etc.).
 *
 * **Example Usage:**
 * ```kotlin
 * val algorithm = Algorithm.Ed25519
 * val key = kms.generateKey(algorithm)
 *
 * // Or parse from string
 * val algorithm = Algorithm.parse("Ed25519")
 * ```
 */
sealed class Algorithm(val name: String) {
    
    /**
     * Security level of this algorithm.
     */
    enum class SecurityLevel {
        LEGACY,    // Algorithms with known weaknesses or deprecated
        STANDARD,  // Current standard algorithms
        HIGH       // High-security algorithms
    }
    
    /**
     * Returns the security level of this algorithm.
     */
    val securityLevel: SecurityLevel
        get() = when (this) {
            is RSA -> when (rsaKeySize) {
                2048 -> SecurityLevel.LEGACY
                3072 -> SecurityLevel.STANDARD
                4096 -> SecurityLevel.HIGH
                else -> SecurityLevel.STANDARD
            }
            Ed25519, Secp256k1, P256, P384, P521, BLS12_381 -> SecurityLevel.STANDARD
            is Custom -> SecurityLevel.STANDARD // Unknown security level for custom algorithms
        }
    
    /**
     * Returns the curve name for elliptic curve algorithms, if applicable.
     */
    val curveName: String?
        get() = when (this) {
            Ed25519 -> "Ed25519"
            Secp256k1 -> "secp256k1"
            P256 -> "P-256"
            P384 -> "P-384"
            P521 -> "P-521"
            BLS12_381 -> "BLS12-381"
            else -> null
        }
    
    /**
     * Checks if this algorithm is compatible with another algorithm.
     * 
     * Algorithms are compatible if:
     * - They are the same algorithm
     * - They are both RSA variants (any RSA key size can sign with any other)
     * - They are both ECC algorithms with the same curve
     */
    fun isCompatibleWith(other: Algorithm): Boolean {
        // Same algorithm is always compatible
        if (this == other) return true
        
        // For RSA, any RSA variant can sign with any other RSA variant (same key type)
        if (this is RSA && other is RSA) return true
        
        // For ECC, algorithms must match exactly (same curve)
        if (this.curveName != null && other.curveName != null) {
            return this.curveName == other.curveName
        }
        
        // Otherwise, not compatible
        return false
    }
    // Elliptic Curve algorithms
    object Ed25519 : Algorithm("Ed25519")
    object Secp256k1 : Algorithm("secp256k1")
    object P256 : Algorithm("P-256")
    object P384 : Algorithm("P-384")
    object P521 : Algorithm("P-521")

    // RSA algorithms (with key size)
    data class RSA(val rsaKeySize: Int) : Algorithm("RSA-$rsaKeySize") {
        init {
            require(rsaKeySize in listOf(2048, 3072, 4096)) {
                "RSA key size must be 2048, 3072, or 4096"
            }
        }
        
        /**
         * Returns the key size in bits for RSA algorithms.
         */
        val keySize: Int = rsaKeySize

        companion object {
            /**
             * RSA-2048 is considered legacy due to security concerns.
             * Use RSA-3072 or RSA-4096 for new deployments.
             * 
             * @deprecated RSA-2048 is considered legacy. Use RSA-3072 or RSA-4096 instead.
             */
            @Deprecated(
                message = "RSA-2048 is considered legacy. Use RSA-3072 or RSA-4096 for better security.",
                level = DeprecationLevel.WARNING,
                replaceWith = ReplaceWith("RSA.RSA_3072")
            )
            val RSA_2048 = RSA(2048)
            
            val RSA_3072 = RSA(3072)
            val RSA_4096 = RSA(4096)
        }
    }

    // BLS algorithms
    object BLS12_381 : Algorithm("BLS12-381")

    // Custom algorithm (for extensibility)
    data class Custom(val customName: String) : Algorithm(customName)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Algorithm) return false
        
        // Critical: Custom algorithms should never equal standard algorithms
        // This prevents algorithm confusion attacks
        if (this is Custom && other !is Custom) return false
        if (this !is Custom && other is Custom) return false
        
        // For same type, compare names (case-insensitive)
        return name.equals(other.name, ignoreCase = true)
    }

    override fun hashCode(): Int {
        // Include type in hash code to ensure Custom algorithms have different hash codes
        val typeHash = when (this) {
            is Custom -> "Custom".hashCode()
            else -> javaClass.name.hashCode()
        }
        return typeHash * 31 + name.lowercase().hashCode()
    }

    override fun toString(): String = name

    companion object {
        /**
         * Parses an algorithm name into an Algorithm instance.
         *
         * @param name Algorithm name (case-insensitive)
         * @return Algorithm instance, or null if not recognized
         */
        /**
         * Standard algorithm names that are recognized.
         * Used to prevent custom algorithms from conflicting with standard ones.
         */
        private val STANDARD_ALGORITHM_NAMES = setOf(
            "ED25519", "SECP256K1", "P-256", "P256", "P-384", "P384", 
            "P-521", "P521", "BLS12-381", "BLS12_381", "RSA-2048", 
            "RSA-3072", "RSA-4096", "RSA"
        )

        /**
         * Parses an algorithm name into an Algorithm instance.
         *
         * @param name Algorithm name (case-insensitive)
         * @return Algorithm instance, or null if not recognized
         */
        fun parse(name: String): Algorithm? {
            val upperName = name.uppercase()
            return when (upperName) {
                "ED25519" -> Ed25519
                "SECP256K1" -> Secp256k1
                "P-256", "P256" -> P256
                "P-384", "P384" -> P384
                "P-521", "P521" -> P521
                "BLS12-381", "BLS12_381" -> BLS12_381
                else -> {
                    // Try RSA
                    if (upperName.startsWith("RSA")) {
                        val keySize = name.substringAfter("-", "").toIntOrNull()
                        if (keySize != null && keySize in listOf(2048, 3072, 4096)) {
                            return RSA(keySize)
                        }
                        // "RSA" without key size is not valid - return null
                        return null
                    }
                    // Validate custom algorithm name
                    if (upperName in STANDARD_ALGORITHM_NAMES) {
                        // Prevent custom algorithms from using standard names (case-insensitive)
                        return null
                    }
                    // Custom algorithm - validate name is not empty and doesn't contain invalid characters
                    if (name.isBlank() || name.contains(Regex("[^a-zA-Z0-9_-]"))) {
                        return null
                    }
                    Custom(name)
                }
            }
        }

    }
}

