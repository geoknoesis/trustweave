package org.trustweave.wallet

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletCapabilitiesTest {

    @Test
    fun `default capabilities have storage and query enabled`() {
        val caps = WalletCapabilities()
        assertTrue(caps.credentialStorage)
        assertTrue(caps.credentialQuery)
    }

    @Test
    fun `default capabilities have optional features disabled`() {
        val caps = WalletCapabilities()
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
    fun `supports returns true for enabled features by primary name`() {
        val caps = WalletCapabilities(
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

        assertTrue(caps.supports("collections"))
        assertTrue(caps.supports("tags"))
        assertTrue(caps.supports("metadata"))
        assertTrue(caps.supports("archive"))
        assertTrue(caps.supports("refresh"))
        assertTrue(caps.supports("createpresentation"))
        assertTrue(caps.supports("selectivedisclosure"))
        assertTrue(caps.supports("didmanagement"))
        assertTrue(caps.supports("keymanagement"))
        assertTrue(caps.supports("credentialissuance"))
    }

    @Test
    fun `supports aliases work`() {
        val caps = WalletCapabilities(
            createPresentation = true,
            selectiveDisclosure = true,
            didManagement = true,
            keyManagement = true,
            credentialIssuance = true,
            credentialStorage = true,
            credentialQuery = true
        )

        assertTrue(caps.supports("presentation"))
        assertTrue(caps.supports("selective-disclosure"))
        assertTrue(caps.supports("did-management"))
        assertTrue(caps.supports("key-management"))
        assertTrue(caps.supports("credential-issuance"))
        assertTrue(caps.supports("credentials"))
        assertTrue(caps.supports("credentialstorage"))
        assertTrue(caps.supports("query"))
        assertTrue(caps.supports("credentialquery"))
    }

    @Test
    fun `supports is case-insensitive`() {
        val caps = WalletCapabilities(collections = true)
        assertTrue(caps.supports("COLLECTIONS"))
        assertTrue(caps.supports("Collections"))
        assertTrue(caps.supports("collections"))
    }

    @Test
    fun `supports returns false for disabled features`() {
        val caps = WalletCapabilities()
        assertFalse(caps.supports("collections"))
        assertFalse(caps.supports("tags"))
    }

    @Test
    fun `supports returns false for unknown feature names`() {
        val caps = WalletCapabilities()
        assertFalse(caps.supports("unknown"))
        assertFalse(caps.supports(""))
        assertFalse(caps.supports("blockchain"))
    }
}
