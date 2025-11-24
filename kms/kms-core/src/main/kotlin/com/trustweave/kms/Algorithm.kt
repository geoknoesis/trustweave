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
    // Elliptic Curve algorithms
    object Ed25519 : Algorithm("Ed25519")
    object Secp256k1 : Algorithm("secp256k1")
    object P256 : Algorithm("P-256")
    object P384 : Algorithm("P-384")
    object P521 : Algorithm("P-521")
    
    // RSA algorithms (with key size)
    data class RSA(val keySize: Int) : Algorithm("RSA-$keySize") {
        init {
            require(keySize in listOf(2048, 3072, 4096)) {
                "RSA key size must be 2048, 3072, or 4096"
            }
        }
        
        companion object {
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
        return name.equals(other.name, ignoreCase = true)
    }
    
    override fun hashCode(): Int = name.lowercase().hashCode()
    
    override fun toString(): String = name
    
    companion object {
        /**
         * Parses an algorithm name into an Algorithm instance.
         * 
         * @param name Algorithm name (case-insensitive)
         * @return Algorithm instance, or null if not recognized
         */
        fun parse(name: String): Algorithm? {
            return when (name.uppercase()) {
                "ED25519" -> Ed25519
                "SECP256K1" -> Secp256k1
                "P-256", "P256" -> P256
                "P-384", "P384" -> P384
                "P-521", "P521" -> P521
                "BLS12-381", "BLS12_381" -> BLS12_381
                else -> {
                    // Try RSA
                    if (name.uppercase().startsWith("RSA")) {
                        val keySize = name.substringAfter("-", "").toIntOrNull()
                        if (keySize != null && keySize in listOf(2048, 3072, 4096)) {
                            return RSA(keySize)
                        }
                    }
                    // Custom algorithm
                    Custom(name)
                }
            }
        }
        
        /**
         * Common algorithm sets for convenience.
         */
        val COMMON_ALGORITHMS = setOf(Ed25519, Secp256k1, P256)
        val NIST_ALGORITHMS = setOf(P256, P384, P521)
        val ALL_STANDARD = setOf(
            Ed25519, 
            Secp256k1, 
            P256, 
            P384, 
            P521, 
            RSA.RSA_2048, 
            RSA.RSA_3072, 
            RSA.RSA_4096, 
            BLS12_381
        )
    }
}

