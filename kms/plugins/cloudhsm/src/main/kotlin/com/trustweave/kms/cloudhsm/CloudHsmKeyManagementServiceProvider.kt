package com.trustweave.kms.cloudhsm

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

class CloudHsmKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "cloudhsm"

    override val supportedAlgorithms: Set<Algorithm> = CloudHsmKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = try {
            CloudHsmKmsConfig.fromMap(options)
        } catch (e: Exception) {
            CloudHsmKmsConfig.fromEnvironment()
                ?: throw IllegalArgumentException(
                    "AWS CloudHSM configuration requires 'clusterId' in options or AWS_CLOUDHSM_CLUSTER_ID environment variable. " +
                    "Error: ${e.message}",
                    e
                )
        }

        return CloudHsmKeyManagementService(config)
    }
}

