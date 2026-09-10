package org.trustweave.testkit

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentationExampleTest {
    @Test
    fun `fixtures isolate registries and close releases registrations`(): Unit =
        runBlocking {
            val first = TrustWeaveTestFixture.builder().withInMemoryBlockchainClient("eip155:1337").build()
            first.use {
                TrustWeaveTestFixture.builder().build().use { second ->
                    assertNotNull(first.getBlockchainClient("eip155:1337"))
                    assertNull(second.getBlockchainClient("eip155:1337"))
                    val issuer = first.createIssuerDid()
                    val other = second.createIssuerDid()
                    assertTrue(issuer.id.value.startsWith("did:key:"))
                    assertNotEquals(issuer.id, other.id)
                    assertEquals(1, issuer.verificationMethod.size)
                }
            }
            assertNull(first.getDidRegistry().get("key"))
            assertNull(first.getBlockchainRegistry().get("eip155:1337"))
        }
}
