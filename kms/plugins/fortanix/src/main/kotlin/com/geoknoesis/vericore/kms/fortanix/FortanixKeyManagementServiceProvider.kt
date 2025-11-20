package com.geoknoesis.vericore.kms.fortanix

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider

class FortanixKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "fortanix"
    
    override val supportedAlgorithms: Set<Algorithm> = FortanixKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = FortanixKmsConfig.fromMap(options)
        return FortanixKeyManagementService(config)
    }
}

