package org.trustweave.credential

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertTrue

/**
 * `bbs-2023` is a recognised identifier with nothing behind it.
 *
 * The plugin that used to serve it was an HMAC emulation that signed with the public key, so it was
 * removed rather than left behind a flag. There is no maintained BBS+ library for the JVM, and a
 * placeholder is worse than nothing for a signature scheme.
 *
 * What matters now is what happens to a credential that claims the cryptosuite. Note that it does
 * *not* arrive as an unsupported format: `getFormatId()` maps every `DataIntegrityProof` to
 * `VC_LD`, so it reaches the VC-LD engine, which does not read `cryptosuite` at all. It fails
 * there, on the signature — which is the outcome that matters, but by a different route than
 * "format not supported", and worth pinning so a future reader is not misled.
 */
class Bbs2023UnsupportedTest {
    @Test
    fun `a credential claiming the bbs-2023 cryptosuite does not verify`() =
        runBlocking {
            val kms = InMemoryKeyManagementService()
            val didMethod = DidKeyMockMethod(kms)
            val issuerDocument = didMethod.createDid()
            val holderDocument = didMethod.createDid()

            val resolver =
                object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                }
            val service =
                CredentialServices.createCredentialService(
                    kms = kms,
                    didResolver = resolver,
                    formats = listOf(ProofSuiteId.VC_LD),
                )

            val credential =
                VerifiableCredential(
                    type = listOf(CredentialType.fromString("VerifiableCredential")),
                    issuer = Issuer.IriIssuer(Iri(issuerDocument.id.value)),
                    issuanceDate = Clock.System.now(),
                    credentialSubject = CredentialSubject(id = Iri(holderDocument.id.value)),
                    proof =
                        CredentialProof.LinkedDataProof(
                            type = "DataIntegrityProof",
                            created = Clock.System.now(),
                            verificationMethod =
                                issuerDocument.verificationMethod
                                    .first()
                                    .id.value,
                            proofPurpose = "assertionMethod",
                            proofValue = "z${"3".repeat(80)}",
                            additionalProperties = mapOf("cryptosuite" to JsonPrimitive("bbs-2023")),
                        ),
                )

            val result = service.verify(credential)

            assertTrue(
                result !is VerificationResult.Valid,
                "Nothing implements bbs-2023; such a proof must never verify. Got: $result",
            )
        }

    @Test
    fun `no proof engine claims the bbs-2023 suite`() =
        runBlocking {
            val kms = InMemoryKeyManagementService()
            val resolver =
                object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = DidResolutionResult.Failure.NotFound(did, "not needed")
                }

            // Asking for the suite explicitly must not produce an engine — the ServiceLoader has
            // nothing registered for it now that the emulation is gone.
            val service =
                CredentialServices.createCredentialService(
                    kms = kms,
                    didResolver = resolver,
                    formats = listOf(ProofSuiteId.BBS_2023),
                )

            assertTrue(
                service.supportedFormats().none { it == ProofSuiteId.BBS_2023 },
                "bbs-2023 must not appear as a supported format: ${service.supportedFormats()}",
            )
        }
}
