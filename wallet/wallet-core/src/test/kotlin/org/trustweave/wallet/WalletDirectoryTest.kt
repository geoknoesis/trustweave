package org.trustweave.wallet

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WalletDirectoryTest {
    private val plain = MapWallet("plain-1")
    private val rich = RichWallet("rich-1", walletDid = "did:key:wallet", holderDid = "did:key:holder")

    @Test
    fun `registered wallet is retrievable by id`() {
        val directory = WalletDirectory()
        directory.register(plain)

        assertSame(plain, directory.get("plain-1"))
        assertNull(directory.get("missing"))
    }

    @Test
    fun `a wallet with DID management is indexed by both its wallet DID and holder DID`() {
        val directory = WalletDirectory()
        directory.register(rich)

        assertSame(rich, directory.getByDid("did:key:wallet"))
        assertSame(rich, directory.getByDid("did:key:holder"))
        assertNull(directory.getByDid("did:key:other"))
    }

    @Test
    fun `a wallet without DID management has no DID index entry`() {
        val directory = WalletDirectory()
        directory.register(plain)

        assertNull(directory.getByDid("plain-1"))
    }

    @Test
    fun `registering the same id again replaces the earlier wallet`() {
        val directory = WalletDirectory()
        directory.register(MapWallet("same"))
        val replacement = MapWallet("same")
        directory.register(replacement)

        assertSame(replacement, directory.get("same"))
        assertEquals(1, directory.getAll().size)
    }

    @Test
    fun `replacement removes old DID mappings and indexes the new identity`() {
        val directory = WalletDirectory()
        directory.register(rich)
        val replacement = RichWallet("rich-1", "did:key:new-wallet", "did:key:new-holder")
        directory.register(replacement)

        assertNull(directory.getByDid(rich.walletDid))
        assertNull(directory.getByDid(rich.holderDid))
        assertSame(replacement, directory.get("rich-1"))
        assertSame(replacement, directory.getByDid(replacement.walletDid))
        assertSame(replacement, directory.getByDid(replacement.holderDid))
    }

    @Test
    fun `replacing a DID wallet with a plain wallet removes both old DID mappings`() {
        val directory = WalletDirectory()
        directory.register(rich)
        directory.register(MapWallet("rich-1"))

        assertNull(directory.getByDid(rich.walletDid))
        assertNull(directory.getByDid(rich.holderDid))
    }

    @Test
    fun `unregister preserves shared DID mappings owned by a later wallet`() {
        val directory = WalletDirectory()
        val later = RichWallet("later", rich.holderDid, rich.walletDid)
        directory.register(rich)
        directory.register(later)

        assertTrue(directory.unregister(rich.walletId))
        assertSame(later, directory.getByDid(rich.walletDid))
        assertSame(later, directory.getByDid(rich.holderDid))
    }

    @Test
    fun `replacement preserves shared DID mappings owned by another wallet`() {
        val directory = WalletDirectory()
        val later = RichWallet("later", rich.walletDid, rich.holderDid)
        directory.register(rich)
        directory.register(later)
        directory.register(MapWallet(rich.walletId))

        assertSame(later, directory.getByDid(rich.walletDid))
        assertSame(later, directory.getByDid(rich.holderDid))
    }

    @Test
    fun `unregister removes the wallet and its DID entries and reports whether it existed`() {
        val directory = WalletDirectory()
        directory.register(rich)

        assertTrue(directory.unregister("rich-1"))
        assertNull(directory.get("rich-1"))
        assertNull(directory.getByDid("did:key:wallet"))
        assertNull(directory.getByDid("did:key:holder"))
        assertFalse(directory.unregister("rich-1"))
    }

    @Test
    fun `unregistering a plain wallet leaves DID entries of other wallets alone`() {
        val directory = WalletDirectory()
        directory.register(rich)
        directory.register(plain)

        assertTrue(directory.unregister("plain-1"))
        assertSame(rich, directory.getByDid("did:key:wallet"))
    }

    @Test
    fun `clear empties both indexes`() {
        val directory = WalletDirectory()
        directory.register(rich)
        directory.register(plain)

        directory.clear()

        assertTrue(directory.getAll().isEmpty())
        assertNull(directory.getByDid("did:key:holder"))
    }

    @Test
    fun `findByCapability by type returns only wallets implementing it`() {
        val directory = WalletDirectory()
        directory.register(rich)
        directory.register(plain)

        val tagging = directory.findByCapability<CredentialTagging>()

        assertEquals(1, tagging.size)
        assertSame(rich, tagging.single())
        assertTrue(directory.findByCapability<CredentialPresentation>().isEmpty())
    }

    @Test
    fun `findByCapability by feature name uses the wallet capability flags`() {
        val directory = WalletDirectory()
        directory.register(rich)
        directory.register(plain)

        assertEquals(listOf(rich), directory.findByCapability("tags"))
        assertEquals(listOf(rich), directory.findByCapability("archive"))
        assertEquals(2, directory.findByCapability("credentials").size)
        assertTrue(directory.findByCapability("no-such-feature").isEmpty())
    }

    @Test
    fun `capability flags and statistics reflect the implemented interfaces`() =
        runTest {
            val caps = rich.capabilities
            assertTrue(caps.collections && caps.tags && caps.metadata && caps.archive && caps.refresh && caps.didManagement)
            assertFalse(caps.createPresentation || caps.keyManagement || caps.credentialIssuance || caps.selectiveDisclosure)

            val plainCaps = plain.capabilities
            assertFalse(plainCaps.collections || plainCaps.tags || plainCaps.archive || plainCaps.didManagement)

            rich.store(sampleCredential("vc:1"))
            rich.store(sampleCredential("vc:2"))
            rich.tagCredential("vc:1", setOf("a", "b"))
            rich.tagCredential("vc:2", setOf("b"))
            rich.createCollection("work")
            rich.archive("vc:2")

            val stats = rich.getStatistics()
            assertEquals(2, stats.totalCredentials)
            assertEquals(2, stats.tagsCount)
            assertEquals(1, stats.collectionsCount)
            assertEquals(1, stats.archivedCount)
        }

    @Test
    fun `capability scope helpers run the block only when the capability exists`() {
        assertEquals("ok", rich.withTagging { "ok" })
        assertEquals("ok", rich.withCollections { "ok" })
        assertEquals("ok", rich.withOrganization { "ok" })
        assertEquals("ok", rich.withLifecycle { "ok" })
        assertEquals("ok", rich.withDidManagement { "ok" })

        assertNull(plain.withTagging { "ok" })
        assertNull(plain.withCollections { "ok" })
        assertNull(plain.withOrganization { "ok" })
        assertNull(plain.withLifecycle { "ok" })
        assertNull(plain.withDidManagement { "ok" })
        assertNull(plain.withPresentation { "ok" })
        assertNull(plain.withKeyManagement { "ok" })
        assertNull(plain.withIssuance { "ok" })
    }

    @Test
    fun `default close is a no-op and overriding wallets are closed through use`() {
        val wallet = MapWallet("closing")
        wallet.use { assertFalse(it.closed) }
        assertTrue(wallet.closed)
    }
}
