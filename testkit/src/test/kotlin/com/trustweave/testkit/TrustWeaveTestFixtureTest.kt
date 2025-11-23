package com.trustweave.testkit

import com.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for TrustWeaveTestFixture.
 */
class TrustWeaveTestFixtureTest {

    @Test
    fun `test builder creates fixture with defaults`() = runBlocking {
        val fixture = TrustWeaveTestFixture.builder().build()
        
        assertNotNull(fixture.getKms())
        assertNotNull(fixture.getDidMethod())
        assertEquals("key", fixture.getDidMethod().method)
    }

    @Test
    fun `test builder with custom KMS`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val fixture = TrustWeaveTestFixture.builder()
            .withKms(kms)
            .build()
        
        assertEquals(kms, fixture.getKms())
    }

    @Test
    fun `test builder with custom DID method`() = runBlocking {
        val fixture = TrustWeaveTestFixture.builder()
            .withDidMethod("key")
            .build()
        
        assertEquals("key", fixture.getDidMethod().method)
    }

    @Test
    fun `test builder with blockchain client`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("algorand:testnet")
        val fixture = TrustWeaveTestFixture.builder()
            .withBlockchainClient("algorand:testnet", client)
            .build()
        
        assertEquals(client, fixture.getBlockchainClient("algorand:testnet"))
    }

    @Test
    fun `test builder with in-memory blockchain client`() = runBlocking {
        val fixture = TrustWeaveTestFixture.builder()
            .withInMemoryBlockchainClient("algorand:testnet", "app-123")
            .build()
        
        assertNotNull(fixture.getBlockchainClient("algorand:testnet"))
    }

    @Test
    fun `test getAllBlockchainClients`() = runBlocking {
        val fixture = TrustWeaveTestFixture.builder()
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
        val fixture = TrustWeaveTestFixture.builder().build()
        
        val didDoc = fixture.createIssuerDid()
        
        assertNotNull(didDoc)
        assertTrue(didDoc.id.startsWith("did:key:"))
    }

    @Test
    fun `test createIssuerDid with custom algorithm`() = runBlocking {
        val fixture = TrustWeaveTestFixture.builder().build()
        
        val didDoc = fixture.createIssuerDid("secp256k1")
        
        assertNotNull(didDoc)
    }

    @Test
    fun `test close cleans up registries`() = runBlocking {
        val fixture = TrustWeaveTestFixture.builder()
            .withInMemoryBlockchainClient("algorand:testnet")
            .build()
        
        assertNotNull(fixture.getBlockchainRegistry().get("algorand:testnet"))
        assertNotNull(fixture.getDidRegistry().get("key"))
        
        fixture.close()
        
        assertNull(fixture.getBlockchainRegistry().get("algorand:testnet"))
        assertNull(fixture.getDidRegistry().get("key"))
    }

    @Test
    fun `test minimal fixture`() = runBlocking {
        val fixture = TrustWeaveTestFixture.minimal()
        
        assertNotNull(fixture.getKms())
        assertNotNull(fixture.getDidMethod())
        assertNotNull(fixture.getBlockchainClient("algorand:testnet"))
    }

    @Test
    fun `test builder withDidMethod throws for unsupported method`() {
        assertFailsWith<IllegalArgumentException> {
            TrustWeaveTestFixture.builder()
                .withDidMethod("unsupported")
                .build()
        }
    }

    @Test
    fun `test getBlockchainClient returns null for unregistered chain`() = runBlocking {
        val fixture = TrustWeaveTestFixture.builder().build()
        
        assertNull(fixture.getBlockchainClient("nonexistent:chain"))
    }
}


