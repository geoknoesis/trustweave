package org.trustweave.trust.dsl

import kotlinx.coroutines.runBlocking
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.quickStart
import org.trustweave.trust.types.DidCreationResult
import org.trustweave.trust.types.getOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DidBuilderFacadeTest {
    private fun <T> withFacade(block: suspend (TrustWeave) -> T): T =
        runBlocking {
            val tw = TrustWeave.quickStart()
            try {
                block(tw)
            } finally {
                tw.close()
            }
        }

    @Test
    fun `creating a DID without naming a method uses the configured default`() =
        withFacade { tw ->
            val result = tw.createDid()

            val success = assertIs<DidCreationResult.Success>(result)
            assertTrue(success.did.value.startsWith("did:key:"), success.did.value)
            assertEquals(success.did.value, success.document.id.value)
            assertTrue(success.document.verificationMethod.isNotEmpty())
        }

    @Test
    fun `each created DID is distinct and resolvable through the facade registry`() =
        withFacade { tw ->
            val (a, docA) = tw.createDid { method("key") }.getOrThrow()
            val (b, _) =
                tw
                    .createDid {
                        method("key")
                        algorithm("Ed25519")
                    }.getOrThrow()

            assertNotEquals(a, b)
            val resolved = tw.configuration.didRegistry.resolve(a.value)
            assertIs<org.trustweave.did.resolver.DidResolutionResult.Success>(resolved)
            assertEquals(docA.id, resolved.document.id)
        }

    @Test
    fun `an unregistered method reports the methods that are available`() =
        withFacade { tw ->
            val result = tw.createDid { method("nosuchmethod") }

            val failure = assertIs<DidCreationResult.Failure.MethodNotRegistered>(result)
            assertEquals("nosuchmethod", failure.method)
            assertTrue("key" in failure.availableMethods, "available: ${failure.availableMethods}")
            val thrown = assertFailsWith<IllegalStateException> { result.getOrThrow() }
            assertTrue(thrown.message!!.contains("'nosuchmethod' is not registered"), thrown.message)
        }

    @Test
    fun `method names and algorithms are validated while the request is described`() =
        withFacade { tw ->
            assertTrue(assertFailsWith<IllegalArgumentException> { tw.createDid { method("") } }.message!!.contains("cannot be blank"))
            assertTrue(
                assertFailsWith<IllegalArgumentException> { tw.createDid { method("Key_Method") } }.message!!.contains("lowercase letters"),
            )
            assertTrue(
                assertFailsWith<IllegalArgumentException> {
                    tw.createDid {
                        method("key")
                        algorithm("rot13")
                    }
                }.message!!
                    .contains("Unsupported key algorithm: rot13"),
            )
        }
}
