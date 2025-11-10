package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.wallet.Wallet
import io.geoknoesis.vericore.testkit.credential.InMemoryWallet
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for WalletBuilder DSL.
 */
class WalletDslTest {

    private lateinit var trustLayer: TrustLayerConfig

    @BeforeEach
    fun setUp() = runBlocking {
        trustLayer = trustLayer {
            keys {
                provider("inMemory")
            }
            
            did {
                method("key") {
                    // Empty block
                }
            }
        }
    }

    @Test
    fun `test wallet creation with minimal config`() = runBlocking {
        val wallet = trustLayer.wallet {
            holder("did:key:holder")
        }
        
        assertNotNull(wallet)
        // InMemoryWallet exposes holderDid as a property
        assertTrue(wallet is InMemoryWallet)
        assertEquals("did:key:holder", (wallet as InMemoryWallet).holderDid)
    }

    @Test
    fun `test wallet creation with custom ID`() = runBlocking {
        val wallet = trustLayer.wallet {
            id("my-custom-wallet")
            holder("did:key:holder")
        }
        
        assertNotNull(wallet)
        assertEquals("my-custom-wallet", wallet.walletId)
        // Verify holder DID is set correctly
        assertTrue(wallet is InMemoryWallet)
        assertEquals("did:key:holder", (wallet as InMemoryWallet).holderDid)
    }

    @Test
    fun `test wallet creation with organization enabled`() = runBlocking {
        val wallet = trustLayer.wallet {
            holder("did:key:holder")
            enableOrganization()
        }
        
        assertNotNull(wallet)
        // InMemoryWallet always supports organization capabilities
        assertTrue(wallet is io.geoknoesis.vericore.credential.wallet.CredentialOrganization)
    }

    @Test
    fun `test wallet creation with presentation enabled`() = runBlocking {
        val wallet = trustLayer.wallet {
            holder("did:key:holder")
            enablePresentation()
        }
        
        assertNotNull(wallet)
        // InMemoryWallet always supports presentation capabilities
        assertTrue(wallet is io.geoknoesis.vericore.credential.wallet.CredentialPresentation)
    }

    @Test
    fun `test wallet creation requires holder DID`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.wallet {
                id("wallet-id")
                // Missing holder DID
            }
        }
    }
}

