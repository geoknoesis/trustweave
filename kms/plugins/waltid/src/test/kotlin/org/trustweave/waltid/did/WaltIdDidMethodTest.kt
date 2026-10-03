@file:Suppress("DEPRECATION")

package org.trustweave.waltid.did

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.didCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.util.ServiceLoader
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The walt.id DID methods were placeholders that produced invalid identifiers. They must neither
 * be discoverable via SPI (where they shadowed the real did:key / did:web plugins) nor create
 * DIDs.
 */
class WaltIdDidMethodTest {
    @Test
    fun `the placeholder provider is not registered via SPI`() {
        val names = ServiceLoader.load(DidMethodProvider::class.java).map { it.name }
        assertFalse("waltid" in names, "placeholder provider must not be discoverable, found $names")
    }

    @Test
    fun `the placeholder did-key method refuses to create DIDs`() =
        runBlocking<Unit> {
            val error = assertFailsWith<UnsupportedOperationException> { WaltIdKeyMethod(InMemoryKeyManagementService()).createDid() }
            assertTrue(error.message!!.contains("did:plugins:key"), error.message)
        }

    @Test
    fun `the placeholder did-web method refuses to create DIDs`() =
        runBlocking<Unit> {
            val error =
                assertFailsWith<UnsupportedOperationException> {
                    WaltIdWebMethod(InMemoryKeyManagementService()).createDid(didCreationOptions { property("domain", "example.com") })
                }
            assertTrue(error.message!!.contains("did:plugins:web"), error.message)
        }

    @Test
    fun `the placeholder methods resolve nothing`() =
        runBlocking<Unit> {
            assertIs<DidResolutionResult.Failure.NotFound>(
                WaltIdKeyMethod(InMemoryKeyManagementService()).resolveDid(Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")),
            )
        }

    @Test
    fun `the deprecated provider still returns null for unsupported methods`() {
        val provider = WaltIdDidMethodProvider()
        assertEquals(null, provider.create("unsupported", didCreationOptions { property("kms", InMemoryKeyManagementService()) }))
    }
}
