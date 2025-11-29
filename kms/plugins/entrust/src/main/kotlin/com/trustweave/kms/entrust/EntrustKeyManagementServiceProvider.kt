package com.trustweave.kms.entrust

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.spi.KeyManagementServiceProvider

class EntrustKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "entrust"

    override val supportedAlgorithms: Set<Algorithm> = EntrustKeyManagementService.SUPPORTED_ALGORITHMS

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = EntrustKmsConfig.fromMap(options)
        return EntrustKeyManagementService(config)
    }
}

