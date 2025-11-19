package com.geoknoesis.vericore.credential.wallet

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for WalletCapabilities.
 */
class WalletCapabilitiesBranchCoverageTest {

    @Test
    fun `test WalletCapabilities supports collections`() {
        val caps = WalletCapabilities(collections = true)
        assertTrue(caps.supports("collections"))
        assertTrue(caps.supports("COLLECTIONS"))
        assertTrue(caps.supports("Collections"))
    }

    @Test
    fun `test WalletCapabilities supports tags`() {
        val caps = WalletCapabilities(tags = true)
        assertTrue(caps.supports("tags"))
        assertTrue(caps.supports("TAGS"))
    }

    @Test
    fun `test WalletCapabilities supports metadata`() {
        val caps = WalletCapabilities(metadata = true)
        assertTrue(caps.supports("metadata"))
    }

    @Test
    fun `test WalletCapabilities supports archive`() {
        val caps = WalletCapabilities(archive = true)
        assertTrue(caps.supports("archive"))
    }

    @Test
    fun `test WalletCapabilities supports refresh`() {
        val caps = WalletCapabilities(refresh = true)
        assertTrue(caps.supports("refresh"))
    }

    @Test
    fun `test WalletCapabilities supports createPresentation`() {
        val caps = WalletCapabilities(createPresentation = true)
        assertTrue(caps.supports("createpresentation"))
        assertTrue(caps.supports("presentation"))
    }

    @Test
    fun `test WalletCapabilities supports selectiveDisclosure`() {
        val caps = WalletCapabilities(selectiveDisclosure = true)
        assertTrue(caps.supports("selectivedisclosure"))
        assertTrue(caps.supports("selective-disclosure"))
    }

    @Test
    fun `test WalletCapabilities supports didManagement`() {
        val caps = WalletCapabilities(didManagement = true)
        assertTrue(caps.supports("didmanagement"))
        assertTrue(caps.supports("did-management"))
    }

    @Test
    fun `test WalletCapabilities supports keyManagement`() {
        val caps = WalletCapabilities(keyManagement = true)
        assertTrue(caps.supports("keymanagement"))
        assertTrue(caps.supports("key-management"))
    }

    @Test
    fun `test WalletCapabilities supports credentialIssuance`() {
        val caps = WalletCapabilities(credentialIssuance = true)
        assertTrue(caps.supports("credentialissuance"))
        assertTrue(caps.supports("credential-issuance"))
    }

    @Test
    fun `test WalletCapabilities supports returns false for unknown feature`() {
        val caps = WalletCapabilities()
        assertFalse(caps.supports("unknown"))
        assertFalse(caps.supports(""))
    }

    @Test
    fun `test WalletCapabilities supports returns false when feature disabled`() {
        val caps = WalletCapabilities(collections = false)
        assertFalse(caps.supports("collections"))
    }

    @Test
    fun `test WalletCapabilities constructor with all defaults`() {
        val caps = WalletCapabilities()
        assertTrue(caps.credentialStorage)
        assertTrue(caps.credentialQuery)
        assertFalse(caps.collections)
        assertFalse(caps.tags)
        assertFalse(caps.metadata)
        assertFalse(caps.archive)
        assertFalse(caps.refresh)
        assertFalse(caps.createPresentation)
        assertFalse(caps.selectiveDisclosure)
        assertFalse(caps.didManagement)
        assertFalse(caps.keyManagement)
        assertFalse(caps.credentialIssuance)
    }

    @Test
    fun `test WalletCapabilities constructor with all enabled`() {
        val caps = WalletCapabilities(
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
        assertTrue(caps.credentialStorage)
        assertTrue(caps.collections)
        assertTrue(caps.didManagement)
    }

    @Test
    fun `test WalletCapabilities equality`() {
        val caps1 = WalletCapabilities(collections = true, tags = true)
        val caps2 = WalletCapabilities(collections = true, tags = true)
        val caps3 = WalletCapabilities(collections = false, tags = true)
        
        assertEquals(caps1, caps2)
        assertNotEquals(caps1, caps3)
    }

    @Test
    fun `test WalletCapabilities copy`() {
        val caps1 = WalletCapabilities(collections = true)
        val caps2 = caps1.copy(tags = true)
        
        assertTrue(caps1.collections)
        assertFalse(caps1.tags)
        assertTrue(caps2.collections)
        assertTrue(caps2.tags)
    }
}

