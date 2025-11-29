package com.trustweave.kms.cloudhsm

/**
 * Configuration for AWS CloudHSM client.
 */
data class CloudHsmKmsConfig(
    val clusterId: String,
    val region: String = "us-east-1",
    val hsmIpAddress: String? = null,
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null
) {
    init {
        require(clusterId.isNotBlank()) { "AWS CloudHSM cluster ID must be specified" }
        require(region.isNotBlank()) { "AWS region must be specified" }
    }

    companion object {
        fun builder(): Builder = Builder()

        fun fromEnvironment(): CloudHsmKmsConfig? {
            val clusterId = System.getenv("AWS_CLOUDHSM_CLUSTER_ID") ?: return null
            val region = System.getenv("AWS_REGION") ?: System.getenv("AWS_DEFAULT_REGION") ?: "us-east-1"

            return Builder()
                .clusterId(clusterId)
                .region(region)
                .accessKeyId(System.getenv("AWS_ACCESS_KEY_ID"))
                .secretAccessKey(System.getenv("AWS_SECRET_ACCESS_KEY"))
                .build()
        }

        fun fromMap(options: Map<String, Any?>): CloudHsmKmsConfig {
            val clusterId = options["clusterId"] as? String
                ?: throw IllegalArgumentException("AWS CloudHSM cluster ID must be specified")

            return Builder()
                .clusterId(clusterId)
                .region(options["region"] as? String ?: "us-east-1")
                .hsmIpAddress(options["hsmIpAddress"] as? String)
                .accessKeyId(options["accessKeyId"] as? String)
                .secretAccessKey(options["secretAccessKey"] as? String)
                .build()
        }
    }

    class Builder {
        private var clusterId: String? = null
        private var region: String = "us-east-1"
        private var hsmIpAddress: String? = null
        private var accessKeyId: String? = null
        private var secretAccessKey: String? = null

        fun clusterId(clusterId: String): Builder {
            this.clusterId = clusterId
            return this
        }

        fun region(region: String): Builder {
            this.region = region
            return this
        }

        fun hsmIpAddress(address: String?): Builder {
            this.hsmIpAddress = address
            return this
        }

        fun accessKeyId(keyId: String?): Builder {
            this.accessKeyId = keyId
            return this
        }

        fun secretAccessKey(secret: String?): Builder {
            this.secretAccessKey = secret
            return this
        }

        fun build(): CloudHsmKmsConfig {
            val clusterId = this.clusterId ?: throw IllegalArgumentException("Cluster ID is required")
            return CloudHsmKmsConfig(clusterId, region, hsmIpAddress, accessKeyId, secretAccessKey)
        }
    }
}

