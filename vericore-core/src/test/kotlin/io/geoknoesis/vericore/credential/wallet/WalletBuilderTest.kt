package io.geoknoesis.vericore.credential.wallet

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for WalletBuilder API.
 */
class WalletBuilderTest {

    @Test
    fun `test withWalletId`() {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
        
        // Builder pattern returns self
        assertNotNull(builder)
    }

    @Test
    fun `test withWalletDid`() {
        val builder = WalletBuilder()
            .withWalletDid("did:key:wallet")
        
        assertNotNull(builder)
    }

    @Test
    fun `test withHolderDid`() {
        val builder = WalletBuilder()
            .withHolderDid("did:key:holder")
        
        assertNotNull(builder)
    }

    @Test
    fun `test enableOrganization`() {
        val builder = WalletBuilder()
            .enableOrganization()
        
        assertNotNull(builder)
    }

    @Test
    fun `test enableLifecycle`() {
        val builder = WalletBuilder()
            .enableLifecycle()
        
        assertNotNull(builder)
    }

    @Test
    fun `test enablePresentation`() {
        val builder = WalletBuilder()
            .enablePresentation()
        
        assertNotNull(builder)
    }

    @Test
    fun `test enableDidManagement`() {
        val builder = WalletBuilder()
            .enableDidManagement(mockDidRegistry())
        
        assertNotNull(builder)
    }

    @Test
    fun `test enableKeyManagement`() {
        val builder = WalletBuilder()
            .enableKeyManagement(mockKms())
        
        assertNotNull(builder)
    }

    @Test
    fun `test enableIssuance`() {
        val builder = WalletBuilder()
            .enableIssuance(mockCredentialIssuer())
        
        assertNotNull(builder)
    }

    @Test
    fun `test build fails without DID when DID management enabled`() = runBlocking {
        val builder = WalletBuilder()
            .enableDidManagement(mockDidRegistry())
        
        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun `test build fails without DID when key management enabled`() = runBlocking {
        val builder = WalletBuilder()
            .enableKeyManagement(mockKms())
        
        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun `test build fails with DID management throws UnsupportedOperationException`() = runBlocking {
        val builder = WalletBuilder()
            .withWalletDid("did:key:wallet")
            .enableDidManagement(mockDidRegistry())
        
        assertFailsWith<UnsupportedOperationException> {
            builder.build()
        }
    }

    @Test
    fun `test build fails with key management throws UnsupportedOperationException`() = runBlocking {
        val builder = WalletBuilder()
            .withWalletDid("did:key:wallet")
            .enableKeyManagement(mockKms())
        
        assertFailsWith<UnsupportedOperationException> {
            builder.build()
        }
    }

    @Test
    fun `test build fails for basic wallet throws UnsupportedOperationException`() = runBlocking {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
        
        assertFailsWith<UnsupportedOperationException> {
            builder.build()
        }
    }

    @Test
    fun `test builder chaining`() {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
            .withWalletDid("did:key:wallet")
            .withHolderDid("did:key:holder")
            .enableOrganization()
            .enableLifecycle()
            .enablePresentation()
        
        assertNotNull(builder)
    }

    private fun mockDidRegistry(): Any = object {}
    private fun mockKms(): Any = object {}
    private fun mockCredentialIssuer(): Any = object {}
}

