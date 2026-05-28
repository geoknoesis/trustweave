package org.trustweave.kms

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
            is RSA -> when (keySize) {
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
            is RSA -> null // RSA is not an elliptic-curve algorithm
            is Custom -> null // Custom algorithms have no defined curve
        }
    
    /**
     * Checks if this algorithm is compatible with another algorithm.
     * 
     * Algorithms are compatible if:
     * - They are the same algorithm
     * - They are both RSA variants with the same key size (RSA-2048 is not compatible with RSA-4096)
     * - They are both ECC algorithms with the same curve
     */
    fun isCompatibleWith(other: Algorithm): Boolean {
        // Same algorithm is always compatible
        if (this == other) return true
        
        // For RSA, key sizes must match — RSA-2048 and RSA-4096 are different key types
        if (this is RSA && other is RSA) return this.rsaKeySize == other.rsaKeySize
        
        // Ed25519 is OKP, not EC — never compatible with EC curves
        if (this is Ed25519 || other is Ed25519) return this is Ed25519 && other is Ed25519
        if (this is BLS12_381 || other is BLS12_381) return this is BLS12_381 && other is BLS12_381
        // For custom algorithms, compare by name
        if (this is Custom) return other is Custom && this.name == other.name
        // For EC algorithms, compare curve names
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
        val keySize: Int get() = rsaKeySize

        companion object {
            /**
             * RSA-2048. Supported for interoperability with legacy systems.
             * Prefer RSA_3072 or RSA_4096 for new deployments.
             */
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
        // Require identical runtime class: prevents RSA(2048) == Custom("RSA-2048") and
        // similar cross-subclass confusions even when names happen to match.
        if (this::class != other::class) return false

        // Critical: Custom algorithms should never equal standard algorithms
        // This prevents algorithm confusion attacks
        if (this is Custom && other !is Custom) return false
        if (this !is Custom && other is Custom) return false

        // For same type, compare names (case-sensitive — standard algorithm names have fixed casing)
        return name == other.name
    }

    override fun hashCode(): Int {
        // Include type in hash code to ensure Custom algorithms have different hash codes.
        // RSA variants share the same class name so we fold rsaKeySize into the hash to
        // ensure RSA(2048), RSA(3072), and RSA(4096) occupy distinct hash buckets.
        return when (this) {
            is Custom -> "Custom".hashCode() * 31 + name.hashCode()
            is RSA -> this::class.java.name.hashCode() * 31 + rsaKeySize
            else -> this::class.java.name.hashCode() * 31 + name.hashCode()
        }
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

