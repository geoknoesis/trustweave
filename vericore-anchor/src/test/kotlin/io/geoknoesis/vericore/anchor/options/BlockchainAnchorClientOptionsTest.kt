package io.geoknoesis.vericore.anchor.options

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for BlockchainAnchorClientOptions API.
 */
class BlockchainAnchorClientOptionsTest {

    @Test
    fun `test AlgorandOptions toMap`() {
        val options = AlgorandOptions(
            algodUrl = "https://testnet-api.algonode.cloud",
            algodToken = "token123",
            privateKey = "base64key",
            appId = "app-123"
        )
        
        val map = options.toMap()
        
        assertEquals("https://testnet-api.algonode.cloud", map["algodUrl"])
        assertEquals("token123", map["algodToken"])
        assertEquals("base64key", map["privateKey"])
        assertEquals("app-123", map["appId"])
    }

    @Test
    fun `test AlgorandOptions fromMap`() {
        val map = mapOf(
            "algodUrl" to "https://testnet-api.algonode.cloud",
            "algodToken" to "token123",
            "privateKey" to "base64key",
            "appId" to "app-123"
        )
        
        val options = AlgorandOptions.fromMap(map)
        
        assertEquals("https://testnet-api.algonode.cloud", options.algodUrl)
        assertEquals("token123", options.algodToken)
        assertEquals("base64key", options.privateKey)
        assertEquals("app-123", options.appId)
    }

    @Test
    fun `test AlgorandOptions with partial map`() {
        val map = mapOf("algodUrl" to "https://testnet-api.algonode.cloud")
        
        val options = AlgorandOptions.fromMap(map)
        
        assertEquals("https://testnet-api.algonode.cloud", options.algodUrl)
        assertNull(options.algodToken)
        assertNull(options.privateKey)
        assertNull(options.appId)
    }

    @Test
    fun `test PolygonOptions toMap`() {
        val options = PolygonOptions(
            rpcUrl = "https://polygon-rpc.com",
            privateKey = "0x123abc",
            contractAddress = "0xcontract"
        )
        
        val map = options.toMap()
        
        assertEquals("https://polygon-rpc.com", map["rpcUrl"])
        assertEquals("0x123abc", map["privateKey"])
        assertEquals("0xcontract", map["contractAddress"])
    }

    @Test
    fun `test PolygonOptions fromMap`() {
        val map = mapOf(
            "rpcUrl" to "https://polygon-rpc.com",
            "privateKey" to "0x123abc",
            "contractAddress" to "0xcontract"
        )
        
        val options = PolygonOptions.fromMap(map)
        
        assertEquals("https://polygon-rpc.com", options.rpcUrl)
        assertEquals("0x123abc", options.privateKey)
        assertEquals("0xcontract", options.contractAddress)
    }

    @Test
    fun `test GanacheOptions toMap`() {
        val options = GanacheOptions(
            rpcUrl = "http://localhost:8545",
            privateKey = "0x123abc",
            contractAddress = "0xcontract"
        )
        
        val map = options.toMap()
        
        assertEquals("http://localhost:8545", map["rpcUrl"])
        assertEquals("0x123abc", map["privateKey"])
        assertEquals("0xcontract", map["contractAddress"])
    }

    @Test
    fun `test GanacheOptions fromMap`() {
        val map = mapOf(
            "rpcUrl" to "http://localhost:8545",
            "privateKey" to "0x123abc",
            "contractAddress" to "0xcontract"
        )
        
        val options = GanacheOptions.fromMap(map)
        
        assertEquals("http://localhost:8545", options.rpcUrl)
        assertEquals("0x123abc", options.privateKey)
        assertEquals("0xcontract", options.contractAddress)
    }

    @Test
    fun `test GanacheOptions fromMap fails without privateKey`() {
        val map = mapOf("rpcUrl" to "http://localhost:8545")
        
        assertFailsWith<IllegalArgumentException> {
            GanacheOptions.fromMap(map)
        }
    }

    @Test
    fun `test IndyOptions toMap`() {
        val options = IndyOptions(
            poolEndpoint = "https://indy-pool.com",
            walletName = "test-wallet",
            walletKey = "wallet-key",
            did = "did:indy:test"
        )
        
        val map = options.toMap()
        
        assertEquals("https://indy-pool.com", map["poolEndpoint"])
        assertEquals("test-wallet", map["walletName"])
        assertEquals("wallet-key", map["walletKey"])
        assertEquals("did:indy:test", map["did"])
    }

    @Test
    fun `test IndyOptions fromMap`() {
        val map = mapOf(
            "poolEndpoint" to "https://indy-pool.com",
            "walletName" to "test-wallet",
            "walletKey" to "wallet-key",
            "did" to "did:indy:test"
        )
        
        val options = IndyOptions.fromMap(map)
        
        assertEquals("https://indy-pool.com", options.poolEndpoint)
        assertEquals("test-wallet", options.walletName)
        assertEquals("wallet-key", options.walletKey)
        assertEquals("did:indy:test", options.did)
    }

    @Test
    fun `test fromMap with Algorand chain ID`() {
        val map = mapOf("algodUrl" to "https://testnet-api.algonode.cloud")
        
        val options = BlockchainAnchorClientOptions.fromMap("algorand:testnet", map)
        
        assertTrue(options is AlgorandOptions)
        assertEquals("https://testnet-api.algonode.cloud", (options as AlgorandOptions).algodUrl)
    }

    @Test
    fun `test fromMap with Polygon chain ID`() {
        val map = mapOf("rpcUrl" to "https://polygon-rpc.com")
        
        val options = BlockchainAnchorClientOptions.fromMap("eip155:137", map)
        
        assertTrue(options is PolygonOptions)
        assertEquals("https://polygon-rpc.com", (options as PolygonOptions).rpcUrl)
    }

    @Test
    fun `test fromMap with Mumbai chain ID`() {
        val map = mapOf("rpcUrl" to "https://mumbai-rpc.com")
        
        val options = BlockchainAnchorClientOptions.fromMap("eip155:80001", map)
        
        assertTrue(options is PolygonOptions)
    }

    @Test
    fun `test fromMap with Ganache chain ID`() {
        val map = mapOf("privateKey" to "0x123abc")
        
        val options = BlockchainAnchorClientOptions.fromMap("eip155:1337", map)
        
        assertTrue(options is GanacheOptions)
        assertEquals("0x123abc", (options as GanacheOptions).privateKey)
    }

    @Test
    fun `test fromMap with Indy chain ID`() {
        val map = mapOf("poolEndpoint" to "https://indy-pool.com")
        
        val options = BlockchainAnchorClientOptions.fromMap("indy:testnet:bcovrin", map)
        
        assertTrue(options is IndyOptions)
        assertEquals("https://indy-pool.com", (options as IndyOptions).poolEndpoint)
    }

    @Test
    fun `test fromMap fails with unsupported chain ID`() {
        val map = mapOf<String, Any?>()
        
        assertFailsWith<IllegalArgumentException> {
            BlockchainAnchorClientOptions.fromMap("unsupported:chain", map)
        }
    }
}

