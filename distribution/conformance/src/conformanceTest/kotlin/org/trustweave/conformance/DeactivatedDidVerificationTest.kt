package org.trustweave.conformance

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.CredentialServices
import org.trustweave.credential.did.issueForDid
import org.trustweave.credential.did.verifyIssuerDid
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.RegistryBasedResolver
import org.trustweave.keydid.KeyDidMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * Regression guard for the security rule this migration hinges on: a **deactivated** DID
 * (DID Resolution 1.0 §4.4 — [DidResolutionResult.Deactivated]) must be a **verification
 * failure**, never treated as "document missing" or silently skipped.
 *
 * Ten call sites across `credential-api`, `bbs`, `oidc4vp`, `siop` and `trust` were migrated by
 * hand to honour this rule and verified by review, but none of them had a regression test outside
 * `did-core` itself. This suite exercises three of those call sites through this module's public
 * dependency on `:credentials:credential-api`:
 *
 *  - [org.trustweave.credential.did.issueForDid] — must reject a deactivated *subject* DID.
 *  - [org.trustweave.credential.did.verifyIssuerDid] — must return `false` for a deactivated
 *    *issuer* DID.
 *  - The proof engine's issuer-key resolution (`ProofEngineUtils.resolveVerificationMethod`,
 *    internal to `credential-api` and therefore not directly callable from this module) is
 *    exercised transitively through the public [org.trustweave.credential.CredentialService.verify]
 *    entry point: a credential signed by a real issuer key must fail verification — not succeed,
 *    not silently skip the signature check — once that issuer's DID is reported deactivated.
 *
 * Fixtures use the real `did:key` method plugin and the in-memory KMS/testkit doubles, matching
 * [DidCore11ConformanceTest]; the "deactivated" resolver is a plain SAM lambda over the
 * [DidResolver] functional interface, not a mock.
 */
@Tag("conformance")
@Tag("did-resolution-1.0")
class DeactivatedDidVerificationTest {

    private val kms = InMemoryKeyManagementService()
    private val method = KeyDidMethod(kms)
    private val liveResolver = RegistryBasedResolver(DidMethodRegistry().apply { register(method) })

    private fun newDid(): Did = runBlocking {
        method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519)).id
    }

    /** Reports [deactivatedDid] as deactivated (§4.4); every other DID delegates to [liveResolver]. */
    private fun deactivatedResolverFor(deactivatedDid: Did): DidResolver = DidResolver { requested ->
        if (requested == deactivatedDid) {
            DidResolutionResult.Deactivated(requested)
        } else {
            liveResolver.resolve(requested)
        }
    }

    @Test
    fun `DV-01 issueForDid rejects a deactivated subject DID with a failure naming deactivation`() = runBlocking {
        val subjectDid = newDid()
        val issuerDid = newDid()
        val service = CredentialServices.createCredentialService(kms, liveResolver)

        val result = service.issueForDid(
            didResolver = deactivatedResolverFor(subjectDid),
            subjectDid = subjectDid,
            issuerDid = issuerDid,
            type = listOf(CredentialType.VerifiableCredential),
            claims = emptyMap(),
            format = ProofSuiteId.VC_LD
        )

        assertTrue(result is IssuanceResult.Failure, "issueForDid must reject a deactivated subject DID: $result")
        val errors = result.allErrors.joinToString()
        assertTrue(
            errors.contains("deactivat", ignoreCase = true),
            "failure must name deactivation, not just 'not found': $errors"
        )
    }

    @Test
    fun `DV-02 verifyIssuerDid returns false for a deactivated issuer DID`() = runBlocking {
        val issuerDid = newDid()
        val service = CredentialServices.createCredentialService(kms, liveResolver)
        val credential = VerifiableCredential(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(issuerDid),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri(issuerDid.value), claims = emptyMap())
        )

        val valid = service.verifyIssuerDid(deactivatedResolverFor(issuerDid), credential)

        assertFalse(valid, "verifyIssuerDid must return false for a deactivated issuer DID")
    }

    @Test
    fun `DV-03 a deactivated issuer DID fails credential verification rather than being skipped`() = runBlocking {
        // Issue a genuinely signed VC-LD credential against the real, live resolver so the
        // signature and verification-method authorization are valid on their own merits.
        val issuerDocument = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
        val issuerDid = issuerDocument.id
        val issuerKeyId = issuerDocument.verificationMethod.first().id

        val issuingService = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = liveResolver,
            formats = listOf(ProofSuiteId.VC_LD)
        )
        val issuanceResult = issuingService.issue(
            IssuanceRequest(
                format = ProofSuiteId.VC_LD,
                issuer = Issuer.IriIssuer(issuerDid),
                issuerKeyId = issuerKeyId,
                credentialSubject = CredentialSubject(id = Iri(issuerDid.value), claims = emptyMap()),
                type = listOf(CredentialType.VerifiableCredential),
                validUntil = Clock.System.now().plus(3650.days)
            )
        )
        assertTrue(
            issuanceResult is IssuanceResult.Success,
            "fixture credential must issue cleanly against the live resolver: $issuanceResult"
        )
        val credential = issuanceResult.credential

        // Verify with a resolver that reports the issuer DID as deactivated. The proof engine
        // resolves the issuer's verification method through ProofEngineUtils.resolveVerificationMethod
        // (internal to credential-api); per §4.4 a Deactivated result yields no document, so that
        // resolution must come back empty and verification must fail closed — not succeed, and not
        // silently skip the signature check.
        val verifyingService = CredentialServices.createCredentialService(
            kms = kms,
            didResolver = deactivatedResolverFor(issuerDid),
            formats = listOf(ProofSuiteId.VC_LD)
        )
        val result = verifyingService.verify(credential)

        assertTrue(
            result is VerificationResult.Invalid,
            "verification must fail for a deactivated issuer DID, not succeed: $result"
        )
        assertTrue(
            result is VerificationResult.Invalid.InvalidIssuer,
            "expected the failure to be attributed to issuer-key resolution, got: $result"
        )
    }
}
