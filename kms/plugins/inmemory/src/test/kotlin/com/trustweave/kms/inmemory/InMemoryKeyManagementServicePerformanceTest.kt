package com.trustweave.kms.inmemory

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.KeyManagementServicePerformanceTest

/**
 * Performance tests for InMemory KMS plugin.
 * 
 * Measures operation latency and throughput for the in-memory implementation.
 * 
 * Note: The base class cache effectiveness test may fail for in-memory KMS
 * due to very fast operations (0ms) causing timing edge cases. This is acceptable
 * as in-memory KMS doesn't have actual caching - it's just a map lookup.
 */
class InMemoryKeyManagementServicePerformanceTest : KeyManagementServicePerformanceTest() {
    
    override fun createKms(): KeyManagementService {
        return InMemoryKeyManagementService()
    }
}

