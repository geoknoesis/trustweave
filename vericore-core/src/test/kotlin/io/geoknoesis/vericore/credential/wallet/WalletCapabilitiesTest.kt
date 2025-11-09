package io.geoknoesis.vericore.credential.wallet

import kotlin.test.*

/**
 * Tests for WalletCapabilities data class and its supports() method.
 */
class WalletCapabilitiesTest {

    @Test
    fun `test default capabilities`() {
        val capabilities = WalletCapabilities()

        assertTrue(capabilities.credentialStorage)
        assertTrue(capabilities.credentialQuery)
        assertFalse(capabilities.collections)
        assertFalse(capabilities.tags)
        assertFalse(capabilities.metadata)
        assertFalse(capabilities.archive)
        assertFalse(capabilities.refresh)
        assertFalse(capabilities.createPresentation)
        assertFalse(capabilities.selectiveDisclosure)
        assertFalse(capabilities.didManagement)
        assertFalse(capabilities.keyManagement)
        assertFalse(capabilities.credentialIssuance)
    }

    @Test
    fun `test all capabilities enabled`() {
        val capabilities = WalletCapabilities(
            credentialStorage = true,
            credentialQuery = true,
            collections = true,
            tags = true,
            metadata = true,
            archive = true,
            refresh = true,
            createPresentation = true,
            selectiveDisclosure = true,
            didManagement = true,
            keyManagement = true,
            credentialIssuance = true
        )

        assertTrue(capabilities.credentialStorage)
        assertTrue(capabilities.collections)
        assertTrue(capabilities.tags)
        assertTrue(capabilities.metadata)
        assertTrue(capabilities.archive)
        assertTrue(capabilities.refresh)
        assertTrue(capabilities.createPresentation)
        assertTrue(capabilities.selectiveDisclosure)
        assertTrue(capabilities.didManagement)
        assertTrue(capabilities.keyManagement)
        assertTrue(capabilities.credentialIssuance)
    }

    @Test
    fun `test supports collections`() {
        val capabilities = WalletCapabilities(collections = true)

        assertTrue(capabilities.supports("collections"))
        assertTrue(capabilities.supports("Collections"))
        assertTrue(capabilities.supports("COLLECTIONS"))
    }

    @Test
    fun `test supports tags`() {
        val capabilities = WalletCapabilities(tags = true)

        assertTrue(capabilities.supports("tags"))
        assertTrue(capabilities.supports("Tags"))
        assertTrue(capabilities.supports("TAGS"))
    }

    @Test
    fun `test supports metadata`() {
        val capabilities = WalletCapabilities(metadata = true)

        assertTrue(capabilities.supports("metadata"))
        assertTrue(capabilities.supports("Metadata"))
    }

    @Test
    fun `test supports archive`() {
        val capabilities = WalletCapabilities(archive = true)

        assertTrue(capabilities.supports("archive"))
        assertTrue(capabilities.supports("Archive"))
    }

    @Test
    fun `test supports refresh`() {
        val capabilities = WalletCapabilities(refresh = true)

        assertTrue(capabilities.supports("refresh"))
        assertTrue(capabilities.supports("Refresh"))
    }

    @Test
    fun `test supports createPresentation`() {
        val capabilities = WalletCapabilities(createPresentation = true)

        assertTrue(capabilities.supports("createpresentation"))
        assertTrue(capabilities.supports("CreatePresentation"))
        assertTrue(capabilities.supports("presentation"))
        assertTrue(capabilities.supports("Presentation"))
    }

    @Test
    fun `test supports selectiveDisclosure`() {
        val capabilities = WalletCapabilities(selectiveDisclosure = true)

        assertTrue(capabilities.supports("selectivedisclosure"))
        assertTrue(capabilities.supports("SelectiveDisclosure"))
        assertTrue(capabilities.supports("selective-disclosure"))
        assertTrue(capabilities.supports("Selective-Disclosure"))
    }

    @Test
    fun `test supports didManagement`() {
        val capabilities = WalletCapabilities(didManagement = true)

        assertTrue(capabilities.supports("didmanagement"))
        assertTrue(capabilities.supports("DidManagement"))
        assertTrue(capabilities.supports("did-management"))
        assertTrue(capabilities.supports("DID-MANAGEMENT"))
    }

    @Test
    fun `test supports keyManagement`() {
        val capabilities = WalletCapabilities(keyManagement = true)

        assertTrue(capabilities.supports("keymanagement"))
        assertTrue(capabilities.supports("KeyManagement"))
        assertTrue(capabilities.supports("key-management"))
    }

    @Test
    fun `test supports credentialIssuance`() {
        val capabilities = WalletCapabilities(credentialIssuance = true)

        assertTrue(capabilities.supports("credentialissuance"))
        assertTrue(capabilities.supports("CredentialIssuance"))
        assertTrue(capabilities.supports("credential-issuance"))
    }

    @Test
    fun `test supports returns false for unsupported feature`() {
        val capabilities = WalletCapabilities()

        assertFalse(capabilities.supports("nonexistent"))
        assertFalse(capabilities.supports("unknown-feature"))
        assertFalse(capabilities.supports(""))
    }

    @Test
    fun `test supports is case insensitive`() {
        val capabilities = WalletCapabilities(
            collections = true,
            tags = true,
            didManagement = true
        )

        assertTrue(capabilities.supports("COLLECTIONS"))
        assertTrue(capabilities.supports("TAGS"))
        assertTrue(capabilities.supports("DIDMANAGEMENT"))
        assertTrue(capabilities.supports("collections"))
        assertTrue(capabilities.supports("tags"))
        assertTrue(capabilities.supports("didmanagement"))
    }

    @Test
    fun `test supports with hyphenated names`() {
        val capabilities = WalletCapabilities(
            selectiveDisclosure = true,
            didManagement = true,
            keyManagement = true,
            credentialIssuance = true
        )

        assertTrue(capabilities.supports("selective-disclosure"))
        assertTrue(capabilities.supports("did-management"))
        assertTrue(capabilities.supports("key-management"))
        assertTrue(capabilities.supports("credential-issuance"))
    }

    @Test
    fun `test supports with whitespace`() {
        val capabilities = WalletCapabilities(collections = true)

        // Should handle whitespace gracefully
        assertFalse(capabilities.supports(" collections "))
        assertFalse(capabilities.supports("collections "))
        assertFalse(capabilities.supports(" collections"))
    }

    @Test
    fun `test capabilities equality`() {
        val cap1 = WalletCapabilities(collections = true, tags = true)
        val cap2 = WalletCapabilities(collections = true, tags = true)
        val cap3 = WalletCapabilities(collections = false, tags = true)

        assertEquals(cap1, cap2)
        assertNotEquals(cap1, cap3)
    }

    @Test
    fun `test capabilities toString`() {
        val capabilities = WalletCapabilities(collections = true)

        val str = capabilities.toString()
        assertTrue(str.contains("collections=true"))
    }

    @Test
    fun `test capabilities copy`() {
        val original = WalletCapabilities(collections = true)
        val copied = original.copy(tags = true)

        assertTrue(original.collections)
        assertFalse(original.tags)
        assertTrue(copied.collections)
        assertTrue(copied.tags)
    }
}


