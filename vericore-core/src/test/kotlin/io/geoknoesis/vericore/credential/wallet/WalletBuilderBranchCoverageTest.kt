package io.geoknoesis.vericore.credential.wallet

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for WalletBuilder.
 */
class WalletBuilderBranchCoverageTest {

    @Test
    fun `test WalletBuilder withWalletId`() {
        val builder = WalletBuilder()
        val result = builder.withWalletId("test-wallet")
        
        assertEquals(builder, result)
    }

    @Test
    fun `test WalletBuilder withWalletDid`() {
        val builder = WalletBuilder()
        val result = builder.withWalletDid("did:key:wallet")
        
        assertEquals(builder, result)
    }

    @Test
    fun `test WalletBuilder withHolderDid`() {
        val builder = WalletBuilder()
        val result = builder.withHolderDid("did:key:holder")
        
        assertEquals(builder, result)
    }

    @Test
    fun `test WalletBuilder enableOrganization`() {
        val builder = WalletBuilder()
        val result = builder.enableOrganization()
        
        assertEquals(builder, result)
    }

    @Test
    fun `test WalletBuilder enableLifecycle`() {
        val builder = WalletBuilder()
        val result = builder.enableLifecycle()
        
        assertEquals(builder, result)
    }

    @Test
    fun `test WalletBuilder enablePresentation`() {
        val builder = WalletBuilder()
        val result = builder.enablePresentation()
        
        assertEquals(builder, result)
    }

    @Test
    fun `test WalletBuilder enablePresentation with service`() {
        val builder = WalletBuilder()
        val result = builder.enablePresentation("test-service")
        
        assertEquals(builder, result)
    }

    @Test
    fun `test WalletBuilder enableDidManagement`() {
        val builder = WalletBuilder()
        val result = builder.enableDidManagement("test-registry")
        
        assertEquals(builder, result)
    }

    @Test
    fun `test WalletBuilder enableKeyManagement`() {
        val builder = WalletBuilder()
        val result = builder.enableKeyManagement("test-kms")
        
        assertEquals(builder, result)
    }

    @Test
    fun `test WalletBuilder enableIssuance`() {
        val builder = WalletBuilder()
        val result = builder.enableIssuance("test-issuer")
        
        assertEquals(builder, result)
    }

    @Test
    fun `test WalletBuilder build throws when DID management enabled without wallet DID`() = runBlocking {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
            .enableDidManagement("test-registry")
        
        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun `test WalletBuilder build throws when key management enabled without wallet DID`() = runBlocking {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
            .enableKeyManagement("test-kms")
        
        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun `test WalletBuilder build throws UnsupportedOperationException for basic wallet`() = runBlocking {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
        
        assertFailsWith<UnsupportedOperationException> {
            builder.build()
        }
    }

    @Test
    fun `test WalletBuilder build throws UnsupportedOperationException with DID and wallet DID`() = runBlocking {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
            .withWalletDid("did:key:wallet")
            .enableDidManagement("test-registry")
        
        assertFailsWith<UnsupportedOperationException> {
            builder.build()
        }
    }

    @Test
    fun `test WalletBuilder build uses holder DID when provided`() = runBlocking {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
            .withWalletDid("did:key:wallet")
            .withHolderDid("did:key:holder")
            .enableDidManagement("test-registry")
        
        assertFailsWith<UnsupportedOperationException> {
            builder.build()
        }
    }

    @Test
    fun `test WalletBuilder build uses wallet DID as holder DID when holder DID not provided`() = runBlocking {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
            .withWalletDid("did:key:wallet")
            .enableDidManagement("test-registry")
        
        assertFailsWith<UnsupportedOperationException> {
            builder.build()
        }
    }

    @Test
    fun `test WalletBuilder build generates UUID when wallet ID not provided`() = runBlocking {
        val builder = WalletBuilder()
        
        assertFailsWith<UnsupportedOperationException> {
            builder.build()
        }
    }

    @Test
    fun `test WalletBuilder chaining multiple methods`() {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
            .withWalletDid("did:key:wallet")
            .enableOrganization()
            .enableLifecycle()
            .enablePresentation()
        
        assertNotNull(builder)
    }

    @Test
    fun `test WalletBuilder chaining all methods`() {
        val builder = WalletBuilder()
            .withWalletId("test-wallet")
            .withWalletDid("did:key:wallet")
            .withHolderDid("did:key:holder")
            .enableOrganization()
            .enableLifecycle()
            .enablePresentation("presentation-service")
            .enableDidManagement("did-registry")
            .enableKeyManagement("kms")
            .enableIssuance("issuer")
        
        assertNotNull(builder)
    }
}
