package com.trustweave.kms.utimaco

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

class UtimacoKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "utimaco"
    
    override val supportedAlgorithms: Set<Algorithm> = UtimacoKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = UtimacoKmsConfig.fromMap(options)
        return UtimacoKeyManagementService(config)
    }
}

