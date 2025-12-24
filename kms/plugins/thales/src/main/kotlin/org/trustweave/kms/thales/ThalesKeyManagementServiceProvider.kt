package org.trustweave.kms.thales

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

class ThalesKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "thales"

    override val supportedAlgorithms: Set<Algorithm> = ThalesKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = ThalesKmsConfig.fromMap(options)
        return ThalesKeyManagementService(config)
    }
}

