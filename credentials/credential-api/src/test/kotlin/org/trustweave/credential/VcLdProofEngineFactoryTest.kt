package org.trustweave.credential

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.Test
import kotlin.test.assertEquals

class VcLdProofEngineFactoryTest {
    @Test
    fun `exposes a public VC-LD proof engine`() {
        val resolver = DidResolver { _: Did -> DidResolutionResult.Failure.NotFound(Did("did:example:test")) }
        val signer: suspend (ByteArray, String) -> ByteArray = { data: ByteArray, _: String -> data }
        val engine = CredentialServices.vcLdProofEngine(resolver, signer)
        assertEquals<ProofSuiteId>(ProofSuiteId.VC_LD, engine.format)
    }
}
