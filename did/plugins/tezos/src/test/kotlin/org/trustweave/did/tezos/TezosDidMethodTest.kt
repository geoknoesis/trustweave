package org.trustweave.did.tezos

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TezosDidMethodTest {
    private val method = TezosDidMethod(InMemoryKeyManagementService())

    @Test
    fun `unsupported creation never returns a fabricated DID document`() =
        runBlocking<Unit> {
            assertFailsWith<TrustWeaveException.Unknown> { method.createDid(DidCreationOptions()) }
        }

    @Test
    fun `unsupported resolution reports an implementation failure rather than a successful document`() =
        runBlocking<Unit> {
            val result = method.resolveDid(Did("did:tz:example"))
            val failure = assertIs<DidResolutionResult.Failure.ResolutionError>(result)
            assertTrue(failure.reason.contains("not implemented"))
        }
}
