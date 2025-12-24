package org.trustweave.kms.cyberark

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

class CyberArkKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "cyberark"

    override val supportedAlgorithms: Set<Algorithm> = CyberArkKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = CyberArkKmsConfig.fromMap(options)
        return CyberArkKeyManagementService(config)
    }
}

