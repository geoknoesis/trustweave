package io.geoknoesis.vericore.testkit

import io.geoknoesis.vericore.anchor.BlockchainRegistry
import io.geoknoesis.vericore.did.DidRegistry
import io.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for VeriCoreTestFixture.
 */
class VeriCoreTestFixtureTest {

    @BeforeEach
    @AfterEach
    fun cleanup() {
        DidRegistry.clear()
        BlockchainRegistry.clear()
    }

    @Test
    fun `test builder creates fixture with defaults`() = runBlocking {
        val fixture = VeriCoreTestFixture.builder().build()
        
        assertNotNull(fixture.getKms())
        assertNotNull(fixture.getDidMethod())
        assertEquals("key", fixture.getDidMethod().method)
    }

    @Test
    fun `test builder with custom KMS`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val fixture = VeriCoreTestFixture.builder()
            .withKms(kms)
            .build()
        
        assertEquals(kms, fixture.getKms())
    }

    @Test
    fun `test builder with custom DID method`() = runBlocking {
        val fixture = VeriCoreTestFixture.builder()
            .withDidMethod("key")
            .build()
        
        assertEquals("key", fixture.getDidMethod().method)
    }

    @Test
    fun `test builder with blockchain client`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("algorand:testnet")
        val fixture = VeriCoreTestFixture.builder()
            .withBlockchainClient("algorand:testnet", client)
            .build()
        
        assertEquals(client, fixture.getBlockchainClient("algorand:testnet"))
    }

    @Test
    fun `test builder with in-memory blockchain client`() = runBlocking {
        val fixture = VeriCoreTestFixture.builder()
            .withInMemoryBlockchainClient("algorand:testnet", "app-123")
            .build()
        
        assertNotNull(fixture.getBlockchainClient("algorand:testnet"))
    }

    @Test
    fun `test getAllBlockchainClients`() = runBlocking {
        val fixture = VeriCoreTestFixture.builder()
            .withInMemoryBlockchainClient("algorand:testnet")
            .withInMemoryBlockchainClient("eip155:137")
            .build()
        
        val clients = fixture.getAllBlockchainClients()
        
        assertEquals(2, clients.size)
        assertTrue(clients.containsKey("algorand:testnet"))
        assertTrue(clients.containsKey("eip155:137"))
    }

    @Test
    fun `test createIssuerDid`() = runBlocking {
        val fixture = VeriCoreTestFixture.builder().build()
        
        val didDoc = fixture.createIssuerDid()
        
        assertNotNull(didDoc)
        assertTrue(didDoc.id.startsWith("did:key:"))
    }

    @Test
    fun `test createIssuerDid with custom algorithm`() = runBlocking {
        val fixture = VeriCoreTestFixture.builder().build()
        
        val didDoc = fixture.createIssuerDid("secp256k1")
        
        assertNotNull(didDoc)
    }

    @Test
    fun `test close cleans up registries`() = runBlocking {
        val fixture = VeriCoreTestFixture.builder()
            .withInMemoryBlockchainClient("algorand:testnet")
            .build()
        
        assertNotNull(BlockchainRegistry.get("algorand:testnet"))
        assertNotNull(DidRegistry.get("key"))
        
        fixture.close()
        
        assertNull(BlockchainRegistry.get("algorand:testnet"))
        assertNull(DidRegistry.get("key"))
    }

    @Test
    fun `test minimal fixture`() = runBlocking {
        val fixture = VeriCoreTestFixture.minimal()
        
        assertNotNull(fixture.getKms())
        assertNotNull(fixture.getDidMethod())
        assertNotNull(fixture.getBlockchainClient("algorand:testnet"))
    }

    @Test
    fun `test builder withDidMethod throws for unsupported method`() {
        assertFailsWith<IllegalArgumentException> {
            VeriCoreTestFixture.builder()
                .withDidMethod("unsupported")
                .build()
        }
    }

    @Test
    fun `test getBlockchainClient returns null for unregistered chain`() = runBlocking {
        val fixture = VeriCoreTestFixture.builder().build()
        
        assertNull(fixture.getBlockchainClient("nonexistent:chain"))
    }
}


