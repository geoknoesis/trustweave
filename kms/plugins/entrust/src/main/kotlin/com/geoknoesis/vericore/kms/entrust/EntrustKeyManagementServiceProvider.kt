package com.geoknoesis.vericore.kms.entrust

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider

class EntrustKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "entrust"
    
    override val supportedAlgorithms: Set<Algorithm> = EntrustKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = EntrustKmsConfig.fromMap(options)
        return EntrustKeyManagementService(config)
    }
}

