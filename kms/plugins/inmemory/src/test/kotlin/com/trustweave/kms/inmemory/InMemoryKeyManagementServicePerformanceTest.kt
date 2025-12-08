package com.trustweave.kms.inmemory

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.KeyManagementServicePerformanceTest

/**
 * Performance tests for InMemory KMS plugin.
 * 
 * Measures operation latency and throughput for the in-memory implementation.
 */
class InMemoryKeyManagementServicePerformanceTest : KeyManagementServicePerformanceTest() {
    
    override fun createKms(): KeyManagementService {
        return InMemoryKeyManagementService()
    }
}

