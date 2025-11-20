package com.geoknoesis.vericore.kms.utimaco

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider

class UtimacoKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "utimaco"
    
    override val supportedAlgorithms: Set<Algorithm> = UtimacoKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = UtimacoKmsConfig.fromMap(options)
        return UtimacoKeyManagementService(config)
    }
}

