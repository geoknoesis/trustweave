package com.trustweave.did.spi

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidDocument
import com.trustweave.did.DidMethod
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.didCreationOptions
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for DidMethodProvider SPI.
 */
class DidMethodProviderTest {

    @Test
    fun `test DidMethodProvider create method`() {
        val provider = createMockProvider()
        
        val method = provider.create("test")
        
        assertNotNull(method)
        assertEquals("test", method?.method)
    }

    @Test
    fun `test DidMethodProvider create returns null for unsupported method`() {
        val provider = createMockProvider()
        
        val method = provider.create("unsupported")
        
        assertNull(method)
    }

    @Test
    fun `test DidMethodProvider name property`() {
        val provider = createMockProvider()
        
        assertEquals("mock", provider.name)
    }

    @Test
    fun `test DidMethodProvider supportedMethods property`() {
        val provider = createMockProvider()
        
        assertEquals(listOf("test", "mock"), provider.supportedMethods)
    }

    private fun createMockProvider(): DidMethodProvider {
        return object : DidMethodProvider {
            override val name = "mock"
            override val supportedMethods = listOf("test", "mock")
            
            override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
                if (!supportedMethods.contains(methodName)) {
                    return null
                }
                
                return object : DidMethod {
                    override val method = methodName
                    
                    override suspend fun createDid(options: DidCreationOptions) = DidDocument(
                        id = "did:$methodName:123"
                    )
                    
                    override suspend fun resolveDid(did: String) = DidResolutionResult(
                        document = DidDocument(id = did)
                    )
                    
                    override suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument) = DidDocument(id = did)
                    
                    override suspend fun deactivateDid(did: String) = true
                }
            }
        }
    }
}

