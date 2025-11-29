package com.trustweave.kms.thales

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

class ThalesKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "thales"

    override val supportedAlgorithms: Set<Algorithm> = ThalesKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = ThalesKmsConfig.fromMap(options)
        return ThalesKeyManagementService(config)
    }
}

