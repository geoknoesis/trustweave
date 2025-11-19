package com.geoknoesis.vericore.credential.dsl

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.testkit.credential.InMemoryWallet
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for WalletBuilder DSL.
 * Tests all conditional branches, error paths, and edge cases.
 */
class WalletBuilderBranchCoverageTest {

    private lateinit var trustLayer: TrustLayerConfig

    @BeforeEach
    fun setUp() = runBlocking {
        trustLayer = trustLayer {
            keys {
                provider("inMemory")
            }
            did {
                method("key") {}
            }
        }
    }

    // ========== Holder DID Required Branches ==========

    @Test
    fun `test branch holder DID required error`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.wallet {
                id("wallet-id")
                // Missing holder DID
            }
        }
    }

    @Test
    fun `test branch holder DID provided`() = runBlocking {
        val wallet = trustLayer.wallet {
            holder("did:key:holder")
        }
        
        assertNotNull(wallet)
        assertTrue(wallet is InMemoryWallet)
        assertEquals("did:key:holder", (wallet as InMemoryWallet).holderDid)
    }

    // ========== Wallet ID Branches ==========

    @Test
    fun `test branch wallet ID auto-generated when not provided`() = runBlocking {
        val wallet = trustLayer.wallet {
            holder("did:key:holder")
            // No ID provided - should auto-generate
        }
        
        assertNotNull(wallet)
        assertTrue(wallet.walletId.isNotEmpty())
    }

    @Test
    fun `test branch wallet ID from custom value`() = runBlocking {
        val wallet = trustLayer.wallet {
            id("my-custom-wallet")
            holder("did:key:holder")
        }
        
        assertNotNull(wallet)
        assertEquals("my-custom-wallet", wallet.walletId)
    }

    // ========== Organization Capabilities Branches ==========

    @Test
    fun `test branch organization disabled by default`() = runBlocking {
        val wallet = trustLayer.wallet {
            holder("did:key:holder")
            // enableOrganization() not called
        }
        
        assertNotNull(wallet)
        // InMemoryWallet always supports organization, but flag is for future use
        assertTrue(wallet is com.geoknoesis.vericore.credential.wallet.CredentialOrganization)
    }

    @Test
    fun `test branch organization enabled`() = runBlocking {
        val wallet = trustLayer.wallet {
            holder("did:key:holder")
            enableOrganization()
        }
        
        assertNotNull(wallet)
        assertTrue(wallet is com.geoknoesis.vericore.credential.wallet.CredentialOrganization)
    }

    // ========== Presentation Capabilities Branches ==========

    @Test
    fun `test branch presentation disabled by default`() = runBlocking {
        val wallet = trustLayer.wallet {
            holder("did:key:holder")
            // enablePresentation() not called
        }
        
        assertNotNull(wallet)
        // InMemoryWallet always supports presentation, but flag is for future use
        assertTrue(wallet is com.geoknoesis.vericore.credential.wallet.CredentialPresentation)
    }

    @Test
    fun `test branch presentation enabled`() = runBlocking {
        val wallet = trustLayer.wallet {
            holder("did:key:holder")
            enablePresentation()
        }
        
        assertNotNull(wallet)
        assertTrue(wallet is com.geoknoesis.vericore.credential.wallet.CredentialPresentation)
    }

    // ========== Combined Options Branches ==========

    @Test
    fun `test branch all wallet options enabled`() = runBlocking {
        val wallet = trustLayer.wallet {
            id("my-wallet")
            holder("did:key:holder")
            enableOrganization()
            enablePresentation()
        }
        
        assertNotNull(wallet)
        assertEquals("my-wallet", wallet.walletId)
        assertTrue(wallet is com.geoknoesis.vericore.credential.wallet.CredentialOrganization)
        assertTrue(wallet is com.geoknoesis.vericore.credential.wallet.CredentialPresentation)
    }

    @Test
    fun `test branch minimal wallet configuration`() = runBlocking {
        val wallet = trustLayer.wallet {
            holder("did:key:holder")
            // Only required field
        }
        
        assertNotNull(wallet)
        assertTrue(wallet.walletId.isNotEmpty())
    }
}



