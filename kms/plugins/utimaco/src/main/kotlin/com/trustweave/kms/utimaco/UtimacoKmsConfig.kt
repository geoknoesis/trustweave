package com.trustweave.kms.utimaco

/**
 * Configuration for Utimaco HSM client.
 */
data class UtimacoKmsConfig(
    val hsmAddress: String,
    val partitionId: String,
    val partitionPassword: String
) {
    init {
        require(hsmAddress.isNotBlank()) { "Utimaco HSM address must be specified" }
        require(partitionId.isNotBlank()) { "Utimaco partition ID must be specified" }
        require(partitionPassword.isNotBlank()) { "Utimaco partition password must be specified" }
    }
    
    companion object {
        fun builder(): Builder = Builder()
        
        fun fromMap(options: Map<String, Any?>): UtimacoKmsConfig {
            val hsmAddress = options["hsmAddress"] as? String
                ?: throw IllegalArgumentException("Utimaco HSM address must be specified")
            val partitionId = options["partitionId"] as? String
                ?: throw IllegalArgumentException("Utimaco partition ID must be specified")
            val partitionPassword = options["partitionPassword"] as? String
                ?: throw IllegalArgumentException("Utimaco partition password must be specified")
            
            return Builder()
                .hsmAddress(hsmAddress)
                .partitionId(partitionId)
                .partitionPassword(partitionPassword)
                .build()
        }
    }
    
    class Builder {
        private var hsmAddress: String? = null
        private var partitionId: String? = null
        private var partitionPassword: String? = null
        
        fun hsmAddress(hsmAddress: String): Builder {
            this.hsmAddress = hsmAddress
            return this
        }
        
        fun partitionId(partitionId: String): Builder {
            this.partitionId = partitionId
            return this
        }
        
        fun partitionPassword(partitionPassword: String): Builder {
            this.partitionPassword = partitionPassword
            return this
        }
        
        fun build(): UtimacoKmsConfig {
            val hsmAddress = this.hsmAddress ?: throw IllegalArgumentException("HSM address is required")
            val partitionId = this.partitionId ?: throw IllegalArgumentException("Partition ID is required")
            val partitionPassword = this.partitionPassword ?: throw IllegalArgumentException("Partition password is required")
            return UtimacoKmsConfig(hsmAddress, partitionId, partitionPassword)
        }
    }
}

