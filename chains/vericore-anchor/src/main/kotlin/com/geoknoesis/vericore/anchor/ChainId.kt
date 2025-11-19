package com.geoknoesis.vericore.anchor

/**
 * Type-safe chain identifier following CAIP-2 format.
 * 
 * Provides compile-time type safety for chain IDs, preventing typos and invalid values.
 * Supports both predefined chain IDs and custom chain IDs for extensibility.
 * 
 * **Example Usage**:
 * ```
 * // Predefined chain IDs
 * val chainId = ChainId.Algorand.Testnet
 * val client = AlgorandBlockchainAnchorClient(chainId.toString(), options)
 * 
 * // Custom chain IDs
 * val customChain = ChainId.Custom("algorand:custom", "algorand")
 * ```
 * 
 * **String Conversion**: Chain IDs can be converted to strings using `toString()` for
 * backward compatibility with existing APIs.
 */
sealed class ChainId {
    /**
     * Returns the string representation of the chain ID (CAIP-2 format).
     */
    abstract override fun toString(): String
    
    /**
     * Returns the namespace (e.g., "algorand", "eip155", "indy").
     */
    abstract fun getNamespace(): String
    
    /**
     * Algorand chain identifiers.
     */
    sealed class Algorand : ChainId() {
        object Mainnet : Algorand() {
            override fun toString() = "algorand:mainnet"
            override fun getNamespace() = "algorand"
        }
        
        object Testnet : Algorand() {
            override fun toString() = "algorand:testnet"
            override fun getNamespace() = "algorand"
        }
        
        object Betanet : Algorand() {
            override fun toString() = "algorand:betanet"
            override fun getNamespace() = "algorand"
        }
        
        companion object {
            /**
             * Creates an Algorand chain ID from a string.
             * 
             * @param chainId The chain ID string (e.g., "algorand:testnet")
             * @return The corresponding ChainId, or null if invalid
             */
            @JvmStatic
            fun fromString(chainId: String): Algorand? {
                return when (chainId) {
                    "algorand:mainnet" -> Mainnet
                    "algorand:testnet" -> Testnet
                    "algorand:betanet" -> Betanet
                    else -> null
                }
            }
        }
    }
    
    /**
     * Ethereum/Polygon chain identifiers (EIP-155).
     */
    sealed class Eip155 : ChainId() {
        object PolygonMainnet : Eip155() {
            override fun toString() = "eip155:137"
            override fun getNamespace() = "eip155"
        }
        
        object MumbaiTestnet : Eip155() {
            override fun toString() = "eip155:80001"
            override fun getNamespace() = "eip155"
        }
        
        object GanacheLocal : Eip155() {
            override fun toString() = "eip155:1337"
            override fun getNamespace() = "eip155"
        }
        
        companion object {
            /**
             * Creates an EIP-155 chain ID from a string or chain number.
             * 
             * @param chainId The chain ID string (e.g., "eip155:137") or number (e.g., 137)
             * @return The corresponding ChainId, or null if invalid
             */
            @JvmStatic
            fun fromString(chainId: String): Eip155? {
                return when (chainId) {
                    "eip155:137" -> PolygonMainnet
                    "eip155:80001" -> MumbaiTestnet
                    "eip155:1337" -> GanacheLocal
                    else -> null
                }
            }
            
            /**
             * Creates an EIP-155 chain ID from a chain number.
             */
            @JvmStatic
            fun fromChainNumber(chainNumber: Int): Eip155? {
                return when (chainNumber) {
                    137 -> PolygonMainnet
                    80001 -> MumbaiTestnet
                    1337 -> GanacheLocal
                    else -> null
                }
            }
        }
    }
    
    /**
     * Hyperledger Indy chain identifiers.
     */
    sealed class Indy : ChainId() {
        object SovrinMainnet : Indy() {
            override fun toString() = "indy:mainnet:sovrin"
            override fun getNamespace() = "indy"
        }
        
        object SovrinStaging : Indy() {
            override fun toString() = "indy:testnet:sovrin-staging"
            override fun getNamespace() = "indy"
        }
        
        object BCovrinTestnet : Indy() {
            override fun toString() = "indy:testnet:bcovrin"
            override fun getNamespace() = "indy"
        }
        
        companion object {
            /**
             * Creates an Indy chain ID from a string.
             * 
             * @param chainId The chain ID string (e.g., "indy:testnet:bcovrin")
             * @return The corresponding ChainId, or null if invalid
             */
            @JvmStatic
            fun fromString(chainId: String): Indy? {
                return when (chainId) {
                    "indy:mainnet:sovrin" -> SovrinMainnet
                    "indy:testnet:sovrin-staging" -> SovrinStaging
                    "indy:testnet:bcovrin" -> BCovrinTestnet
                    else -> null
                }
            }
        }
    }
    
    /**
     * Custom chain ID for extensibility.
     * 
     * Use this for chain IDs that don't have predefined types.
     * The chainId must follow CAIP-2 format: "namespace:reference"
     */
    data class Custom(
        private val chainId: String,
        private val namespace: String
    ) : ChainId() {
        init {
            require(chainId.matches(Regex("^[a-z0-9]+:[a-z0-9-]+(:[a-z0-9-]+)*$"))) {
                "Invalid chain ID format: $chainId. Expected CAIP-2 format: namespace:reference"
            }
        }
        
        override fun toString() = chainId
        override fun getNamespace() = namespace
        
        companion object {
            /**
             * Creates a custom chain ID from a string.
             * 
             * @param chainId The chain ID string (must follow CAIP-2 format)
             * @return The ChainId, or null if format is invalid
             */
            @JvmStatic
            fun fromString(chainId: String): Custom? {
                val parts = chainId.split(":")
                if (parts.size < 2) return null
                
                return try {
                    Custom(chainId, parts[0])
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
    }
    
    companion object {
        /**
         * Parses a chain ID string into a type-safe ChainId.
         * 
         * Attempts to match against predefined chain IDs first, then falls back to Custom.
         * 
         * @param chainId The chain ID string
         * @return The corresponding ChainId, or null if invalid
         */
        @JvmStatic
        fun parse(chainId: String): ChainId? {
            return when {
                chainId.startsWith("algorand:") -> Algorand.fromString(chainId)
                chainId.startsWith("eip155:") -> Eip155.fromString(chainId)
                chainId.startsWith("indy:") -> Indy.fromString(chainId)
                else -> Custom.fromString(chainId)
            }
        }
        
        /**
         * Validates that a chain ID string follows CAIP-2 format.
         * 
         * @param chainId The chain ID string to validate
         * @return true if valid, false otherwise
         */
        @JvmStatic
        fun isValid(chainId: String): Boolean {
            return parse(chainId) != null
        }
    }
}

