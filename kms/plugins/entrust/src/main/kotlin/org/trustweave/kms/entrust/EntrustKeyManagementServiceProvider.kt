package org.trustweave.kms.entrust

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.spi.KeyManagementServiceProvider

class EntrustKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "entrust"

    override val supportedAlgorithms: Set<Algorithm> = EntrustKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = EntrustKmsConfig.fromMap(options)
        return EntrustKeyManagementService(config)
    }
}

