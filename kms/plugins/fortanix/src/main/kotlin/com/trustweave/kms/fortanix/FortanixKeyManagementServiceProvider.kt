package com.trustweave.kms.fortanix

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

class FortanixKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "fortanix"
    
    override val supportedAlgorithms: Set<Algorithm> = FortanixKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = FortanixKmsConfig.fromMap(options)
        return FortanixKeyManagementService(config)
    }
}

