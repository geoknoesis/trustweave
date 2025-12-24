package org.trustweave.kms.utimaco

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

class UtimacoKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "utimaco"

    override val supportedAlgorithms: Set<Algorithm> = UtimacoKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = UtimacoKmsConfig.fromMap(options)
        return UtimacoKeyManagementService(config)
    }
}

