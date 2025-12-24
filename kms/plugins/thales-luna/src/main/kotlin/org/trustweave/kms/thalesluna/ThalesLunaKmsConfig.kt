package org.trustweave.kms.thalesluna

/**
 * Configuration for Thales Luna Network HSM client.
 */
data class ThalesLunaKmsConfig(
    val hsmAddress: String,
    val partitionId: String,
    val partitionPassword: String,
    val clientCertificatePath: String? = null,
    val clientKeyPath: String? = null
) {
    init {
        require(hsmAddress.isNotBlank()) { "Thales Luna HSM address must be specified" }
        require(partitionId.isNotBlank()) { "Thales Luna partition ID must be specified" }
        require(partitionPassword.isNotBlank()) { "Thales Luna partition password must be specified" }
    }

    companion object {
        fun builder(): Builder = Builder()

        fun fromMap(options: Map<String, Any?>): ThalesLunaKmsConfig {
            val hsmAddress = options["hsmAddress"] as? String
                ?: throw IllegalArgumentException("Thales Luna HSM address must be specified")
            val partitionId = options["partitionId"] as? String
                ?: throw IllegalArgumentException("Thales Luna partition ID must be specified")
            val partitionPassword = options["partitionPassword"] as? String
                ?: throw IllegalArgumentException("Thales Luna partition password must be specified")

            return Builder()
                .hsmAddress(hsmAddress)
                .partitionId(partitionId)
                .partitionPassword(partitionPassword)
                .clientCertificatePath(options["clientCertificatePath"] as? String)
                .clientKeyPath(options["clientKeyPath"] as? String)
                .build()
        }
    }

    class Builder {
        private var hsmAddress: String? = null
        private var partitionId: String? = null
        private var partitionPassword: String? = null
        private var clientCertificatePath: String? = null
        private var clientKeyPath: String? = null

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

        fun clientCertificatePath(path: String?): Builder {
            this.clientCertificatePath = path
            return this
        }

        fun clientKeyPath(path: String?): Builder {
            this.clientKeyPath = path
            return this
        }

        fun build(): ThalesLunaKmsConfig {
            val hsmAddress = this.hsmAddress ?: throw IllegalArgumentException("HSM address is required")
            val partitionId = this.partitionId ?: throw IllegalArgumentException("Partition ID is required")
            val partitionPassword = this.partitionPassword ?: throw IllegalArgumentException("Partition password is required")
            return ThalesLunaKmsConfig(hsmAddress, partitionId, partitionPassword, clientCertificatePath, clientKeyPath)
        }
    }
}

