package com.trustweave.kms.cyberark

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

class CyberArkKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "cyberark"

    override val supportedAlgorithms: Set<Algorithm> = CyberArkKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = CyberArkKmsConfig.fromMap(options)
        return CyberArkKeyManagementService(config)
    }
}

