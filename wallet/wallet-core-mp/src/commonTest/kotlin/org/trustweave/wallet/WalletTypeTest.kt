package org.trustweave.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WalletTypeTest {
    @Test
    fun `built-in wallet types expose stable ids`() {
        assertEquals("inMemory", WalletType.InMemory.id)
        assertEquals("file", WalletType.File.id)
        assertEquals("database", WalletType.Database.id)
        assertEquals("cloud", WalletType.Cloud.id)
        assertEquals("cloud", WalletType.Cloud.toString())
    }

    @Test
    fun `fromString maps every built-in id back to its singleton`() {
        assertSame(WalletType.InMemory, WalletTypes.fromString("inMemory"))
        assertSame(WalletType.File, WalletTypes.fromString("file"))
        assertSame(WalletType.Database, WalletTypes.fromString("database"))
        assertSame(WalletType.Cloud, WalletTypes.fromString("cloud"))
    }

    @Test
    fun `an unknown id becomes a custom type that keeps its id`() {
        val custom = WalletTypes.fromString("hardware")
        assertIs<WalletType.Custom>(custom)
        assertEquals("hardware", custom.customId)
        assertEquals("hardware", custom.id)
        assertEquals("hardware", custom.toString())
        assertEquals(WalletType.Custom("hardware"), custom)
        assertNotEquals<WalletType>(WalletType.Custom("file"), WalletType.File)
    }

    @Test
    fun `fromString is case sensitive`() {
        assertIs<WalletType.Custom>(WalletTypes.fromString("INMEMORY"))
        assertIs<WalletType.Custom>(WalletTypes.fromString(""))
    }

    @Test
    fun `the WalletTypes aliases are the same objects`() {
        assertSame(WalletType.InMemory, WalletTypes.InMemory)
        assertSame(WalletType.File, WalletTypes.File)
        assertSame(WalletType.Database, WalletTypes.Database)
        assertSame(WalletType.Cloud, WalletTypes.Cloud)
    }

    @Test
    fun `capabilities default to storage and query only`() {
        val defaults = WalletCapabilities()
        assertTrue(defaults.supports("credentials"))
        assertTrue(defaults.supports("QUERY"))
        assertFalse(defaults.supports("collections"))
        assertFalse(defaults.supports("unknown-feature"))
    }

    @Test
    fun `every alias of a capability follows its flag`() {
        val all =
            WalletCapabilities(
                collections = true,
                tags = true,
                metadata = true,
                archive = true,
                refresh = true,
                createPresentation = true,
                selectiveDisclosure = true,
                didManagement = true,
                keyManagement = true,
                credentialIssuance = true,
            )
        listOf(
            "collections",
            "tags",
            "metadata",
            "archive",
            "refresh",
            "createPresentation",
            "presentation",
            "selectiveDisclosure",
            "selective-disclosure",
            "didManagement",
            "did-management",
            "keyManagement",
            "key-management",
            "credentialIssuance",
            "credential-issuance",
            "credentialStorage",
            "credentialQuery",
        ).forEach { assertTrue(all.supports(it), it) }
        val none = WalletCapabilities(credentialStorage = false, credentialQuery = false)
        listOf("credentials", "credentialStorage", "query", "credentialQuery").forEach { assertFalse(none.supports(it), it) }
    }
}
