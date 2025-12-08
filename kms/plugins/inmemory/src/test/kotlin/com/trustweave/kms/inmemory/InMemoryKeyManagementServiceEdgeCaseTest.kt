package com.trustweave.kms.inmemory

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.testkit.kms.PluginEdgeCaseTestTemplate

/**
 * Edge case tests for InMemory KMS plugin.
 * 
 * Tests plugin-specific edge cases and error scenarios.
 */
class InMemoryKeyManagementServiceEdgeCaseTest : PluginEdgeCaseTestTemplate() {
    
    override fun createKms(): KeyManagementService {
        return InMemoryKeyManagementService()
    }

    override fun getSupportedAlgorithms(): List<Algorithm> {
        return InMemoryKeyManagementService.SUPPORTED_ALGORITHMS.toList()
    }
}

