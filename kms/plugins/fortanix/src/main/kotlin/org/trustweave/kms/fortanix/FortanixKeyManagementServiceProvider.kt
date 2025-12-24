package org.trustweave.kms.fortanix

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

class FortanixKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "fortanix"

    override val supportedAlgorithms: Set<Algorithm> = FortanixKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = FortanixKmsConfig.fromMap(options)
        return FortanixKeyManagementService(config)
    }
}

