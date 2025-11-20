package com.geoknoesis.vericore.kms.cyberark

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider

class CyberArkKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "cyberark"
    
    override val supportedAlgorithms: Set<Algorithm> = CyberArkKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = CyberArkKmsConfig.fromMap(options)
        return CyberArkKeyManagementService(config)
    }
}

