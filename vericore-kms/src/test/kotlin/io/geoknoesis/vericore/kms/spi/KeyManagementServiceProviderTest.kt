package io.geoknoesis.vericore.kms.spi

import io.geoknoesis.vericore.kms.KeyHandle
import io.geoknoesis.vericore.kms.KeyManagementService
import io.geoknoesis.vericore.kms.KeyNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for KeyManagementServiceProvider SPI.
 */
class KeyManagementServiceProviderTest {

    @Test
    fun `test KeyManagementServiceProvider create method`() {
        val provider = createMockProvider()
        
        val kms = provider.create()
        
        assertNotNull(kms)
    }

    @Test
    fun `test KeyManagementServiceProvider create with options`() {
        val provider = createMockProvider()
        
        val kms = provider.create(mapOf("algorithm" to "Ed25519"))
        
        assertNotNull(kms)
    }

    @Test
    fun `test KeyManagementServiceProvider name property`() {
        val provider = createMockProvider()
        
        assertEquals("mock", provider.name)
    }

    @Test
    fun `test KeyManagementServiceProvider creates functional KMS`() = runBlocking {
        val provider = createMockProvider()
        val kms = provider.create()
        
        val handle = kms.generateKey("Ed25519")
        
        assertNotNull(handle)
        assertEquals("Ed25519", handle.algorithm)
    }

    private fun createMockProvider(): KeyManagementServiceProvider {
        return object : KeyManagementServiceProvider {
            override val name = "mock"
            
            override fun create(options: Map<String, Any?>): KeyManagementService {
                return object : KeyManagementService {
                    private val keys = mutableMapOf<String, KeyHandle>()
                    
                    override suspend fun generateKey(algorithm: String, options: Map<String, Any?>): KeyHandle {
                        val handle = KeyHandle(
                            id = "key-${keys.size + 1}",
                            algorithm = algorithm
                        )
                        keys[handle.id] = handle
                        return handle
                    }
                    
                    override suspend fun getPublicKey(keyId: String): KeyHandle {
                        return keys[keyId] ?: throw KeyNotFoundException("Key not found: $keyId")
                    }
                    
                    override suspend fun sign(keyId: String, data: ByteArray, algorithm: String?): ByteArray {
                        keys[keyId] ?: throw KeyNotFoundException("Key not found: $keyId")
                        return ByteArray(64)
                    }
                    
                    override suspend fun deleteKey(keyId: String): Boolean {
                        return keys.remove(keyId) != null
                    }
                }
            }
        }
    }
}

