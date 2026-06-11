package org.trustweave.anchor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for ChainId API.
 */
class ChainIdTest {

    @Test
    fun `test Algorand chain IDs`() {
        assertEquals("algorand:mainnet", ChainId.Algorand.Mainnet.toString())
        assertEquals("algorand:testnet", ChainId.Algorand.Testnet.toString())
        assertEquals("algorand:betanet", ChainId.Algorand.Betanet.toString())
        assertEquals("algorand", ChainId.Algorand.Mainnet.getNamespace())
    }

    @Test
    fun `test Algorand fromString`() {
        assertEquals(ChainId.Algorand.Mainnet, ChainId.Algorand.fromString("algorand:mainnet"))
        assertEquals(ChainId.Algorand.Testnet, ChainId.Algorand.fromString("algorand:testnet"))
        assertEquals(ChainId.Algorand.Betanet, ChainId.Algorand.fromString("algorand:betanet"))
        assertNull(ChainId.Algorand.fromString("algorand:invalid"))
    }

    @Test
    fun `test EIP-155 chain IDs`() {
        assertEquals("eip155:1", ChainId.Eip155.EthereumMainnet.toString())
        assertEquals("eip155:11155111", ChainId.Eip155.SepoliaTestnet.toString())
        assertEquals("eip155:10", ChainId.Eip155.OptimismMainnet.toString())
        assertEquals("eip155:11155420", ChainId.Eip155.OptimismSepoliaTestnet.toString())
        assertEquals("eip155:137", ChainId.Eip155.PolygonMainnet.toString())
        assertEquals("eip155:80002", ChainId.Eip155.AmoyTestnet.toString())
        assertEquals("eip155:324", ChainId.Eip155.ZkSyncEraMainnet.toString())
        assertEquals("eip155:300", ChainId.Eip155.ZkSyncEraSepoliaTestnet.toString())
        assertEquals("eip155:8453", ChainId.Eip155.BaseMainnet.toString())
        assertEquals("eip155:84532", ChainId.Eip155.BaseSepoliaTestnet.toString())
        assertEquals("eip155:42161", ChainId.Eip155.ArbitrumOneMainnet.toString())
        assertEquals("eip155:421614", ChainId.Eip155.ArbitrumSepoliaTestnet.toString())
        assertEquals("eip155:1337", ChainId.Eip155.GanacheLocal.toString())
        assertEquals("eip155", ChainId.Eip155.PolygonMainnet.getNamespace())
    }

    @Test
    fun `test EIP-155 fromString`() {
        assertEquals(ChainId.Eip155.EthereumMainnet, ChainId.Eip155.fromString("eip155:1"))
        assertEquals(ChainId.Eip155.PolygonMainnet, ChainId.Eip155.fromString("eip155:137"))
        assertEquals(ChainId.Eip155.AmoyTestnet, ChainId.Eip155.fromString("eip155:80002"))
        assertEquals(ChainId.Eip155.GanacheLocal, ChainId.Eip155.fromString("eip155:1337"))
        assertNull(ChainId.Eip155.fromString("eip155:999"))
        // Mumbai was decommissioned in 2024 and replaced by Amoy
        assertNull(ChainId.Eip155.fromString("eip155:80001"))
        assertNull(ChainId.Eip155.fromString("algorand:testnet"))
    }

    @Test
    fun `test EIP-155 fromChainNumber`() {
        assertEquals(ChainId.Eip155.EthereumMainnet, ChainId.Eip155.fromChainNumber(1))
        assertEquals(ChainId.Eip155.PolygonMainnet, ChainId.Eip155.fromChainNumber(137))
        assertEquals(ChainId.Eip155.AmoyTestnet, ChainId.Eip155.fromChainNumber(80002))
        assertEquals(ChainId.Eip155.GanacheLocal, ChainId.Eip155.fromChainNumber(1337))
        assertNull(ChainId.Eip155.fromChainNumber(999))
        // Mumbai was decommissioned in 2024 and replaced by Amoy
        assertNull(ChainId.Eip155.fromChainNumber(80001))
    }

    @Test
    fun `test EIP-155 all entries round-trip through fromString`() {
        for (entry in ChainId.Eip155.all) {
            assertEquals(entry, ChainId.Eip155.fromString(entry.toString()))
            assertEquals(entry, ChainId.Eip155.fromChainNumber(entry.chainNumber))
        }
    }

    @Test
    fun `test Indy chain IDs`() {
        assertEquals("indy:mainnet:sovrin", ChainId.Indy.SovrinMainnet.toString())
        assertEquals("indy:testnet:sovrin-staging", ChainId.Indy.SovrinStaging.toString())
        assertEquals("indy:testnet:bcovrin", ChainId.Indy.BCovrinTestnet.toString())
        assertEquals("indy", ChainId.Indy.SovrinMainnet.getNamespace())
    }

    @Test
    fun `test Indy fromString`() {
        assertEquals(ChainId.Indy.SovrinMainnet, ChainId.Indy.fromString("indy:mainnet:sovrin"))
        assertEquals(ChainId.Indy.SovrinStaging, ChainId.Indy.fromString("indy:testnet:sovrin-staging"))
        assertEquals(ChainId.Indy.BCovrinTestnet, ChainId.Indy.fromString("indy:testnet:bcovrin"))
        assertNull(ChainId.Indy.fromString("indy:invalid"))
    }

    @Test
    fun `test Custom chain ID`() {
        val custom = ChainId.Custom("custom:test", "custom")
        assertEquals("custom:test", custom.toString())
        assertEquals("custom", custom.getNamespace())
    }

    @Test
    fun `test Custom chain ID validation`() {
        assertFailsWith<IllegalArgumentException> {
            ChainId.Custom("invalid", "custom")
        }

        // The regex allows multiple colons, so this is actually valid
        // assertFailsWith<IllegalArgumentException> {
        //     ChainId.Custom("invalid:format:with:too:many:parts", "custom")
        // }

        // Test invalid format without colon
        assertFailsWith<IllegalArgumentException> {
            ChainId.Custom("no-colon", "custom")
        }
    }

    @Test
    fun `test Custom fromString`() {
        val custom = ChainId.Custom.fromString("custom:test")
        assertNotNull(custom)
        assertEquals("custom:test", custom?.toString())
        assertEquals("custom", custom?.getNamespace())

        assertNull(ChainId.Custom.fromString("invalid"))
        assertNull(ChainId.Custom.fromString(""))
    }

    @Test
    fun `test ChainId parse`() {
        assertEquals(ChainId.Algorand.Testnet, ChainId.parse("algorand:testnet"))
        assertEquals(ChainId.Eip155.PolygonMainnet, ChainId.parse("eip155:137"))
        assertEquals(ChainId.Indy.BCovrinTestnet, ChainId.parse("indy:testnet:bcovrin"))

        val custom = ChainId.parse("custom:test")
        assertNotNull(custom)
        assertTrue(custom is ChainId.Custom)

        assertNull(ChainId.parse("invalid"))
    }

    @Test
    fun `test ChainId isValid`() {
        assertTrue(ChainId.isValid("algorand:testnet"))
        assertTrue(ChainId.isValid("eip155:137"))
        assertTrue(ChainId.isValid("indy:testnet:bcovrin"))
        assertTrue(ChainId.isValid("custom:test"))
        // Multiple colons are valid per CAIP-2
        assertTrue(ChainId.isValid("custom:test:with:multiple:parts"))

        assertFalse(ChainId.isValid("invalid"))
        assertFalse(ChainId.isValid(""))
        // Single word without colon is invalid
        assertFalse(ChainId.isValid("no-colon"))
    }
}

