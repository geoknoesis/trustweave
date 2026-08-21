package org.trustweave.credential.proof.internal.engines

import kotlinx.coroutines.runBlocking
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A proof names the key that signed it. That name must resolve inside the issuer's own DID
 * document and nowhere else — otherwise the key actually used and the key the proof claims to have
 * used can differ, and anything downstream that reads `proof.verificationMethod` is reading a
 * value the verifier never checked.
 */
class ProofVerificationMethodBindingTest {
    private val kms = InMemoryKeyManagementService()
    private val didMethod = DidKeyMockMethod(kms)

    private val issuerDocument = runBlocking { didMethod.createDid() }
    private val otherDocument = runBlocking { didMethod.createDid() }

    private val resolver =
        object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
        }

    private val issuerIri = Iri(issuerDocument.id.value)

    @Test
    fun `the issuer's own verification method resolves`() =
        runBlocking {
            val vmId =
                issuerDocument.verificationMethod
                    .first()
                    .id.value

            val resolved = ProofEngineUtils.resolveVerificationMethod(issuerIri, vmId, resolver)

            assertEquals(vmId, resolved?.id?.value, "The issuer's own key must resolve")
        }

    @Test
    fun `a verification method belonging to another DID does not resolve`() =
        runBlocking {
            val foreignVmId =
                otherDocument.verificationMethod
                    .first()
                    .id.value

            val resolved =
                ProofEngineUtils.resolveVerificationMethod(issuerIri, foreignVmId, resolver)

            assertNull(resolved, "A key from a different DID document must never satisfy an issuer's proof")
        }

    @Test
    fun `a foreign DID reusing the issuer's fragment does not resolve`() =
        runBlocking {
            // Same fragment as a real key of the issuer, but under someone else's DID. Matching on
            // the fragment alone would hand back the issuer's key for a proof that named another.
            val issuerFragment =
                issuerDocument.verificationMethod
                    .first()
                    .id.value
                    .substringAfter("#")
            val spoofed = "${otherDocument.id.value}#$issuerFragment"

            val resolved = ProofEngineUtils.resolveVerificationMethod(issuerIri, spoofed, resolver)

            assertNull(resolved, "A fragment match must not cross DID boundaries")
        }
}
