package org.trustweave.kms.thalesluna

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

class ThalesLunaKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "thales-luna"

    override val supportedAlgorithms: Set<Algorithm> = ThalesLunaKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = ThalesLunaKmsConfig.fromMap(options)
        return ThalesLunaKeyManagementService(config)
    }
}

