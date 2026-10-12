package org.trustweave.credential.did

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock

class CredentialServiceDidExtensionsBehaviorTest {
    private val subject = Did("did:key:subject")
    private val issuer = Did("did:key:issuer")

    /** Records what the extension delegates to; every other operation is out of scope. */
    private class RecordingService : CredentialService {
        var issued: IssuanceRequest? = null
        var verifyCalls = 0
        var lastOptions: VerificationOptions? = null
        var lastTrustPolicy: Any? = Any()
        var verifyResult: VerificationResult? = null

        override suspend fun issue(request: IssuanceRequest): IssuanceResult {
            issued = request
            return IssuanceResult.Success(sampleCredential(request.issuer.id.value))
        }

        override suspend fun verify(
            credential: VerifiableCredential,
            trustPolicy: org.trustweave.credential.trust.TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult {
            verifyCalls++
            lastOptions = options
            lastTrustPolicy = trustPolicy
            return verifyResult ?: error("verifyResult not configured")
        }

        override suspend fun createPresentation(
            credentials: List<VerifiableCredential>,
            request: org.trustweave.credential.requests.PresentationRequest,
        ) = throw UnsupportedOperationException()

        override suspend fun verifyPresentation(
            presentation: org.trustweave.credential.model.vc.VerifiablePresentation,
            trustPolicy: org.trustweave.credential.trust.TrustEvaluator?,
            options: VerificationOptions,
        ) = throw UnsupportedOperationException()

        override suspend fun status(
            credential: VerifiableCredential,
            clockSkewTolerance: kotlin.time.Duration,
        ) = throw UnsupportedOperationException()

        override fun supports(format: ProofSuiteId) = true

        override fun supportedFormats() = ProofSuiteId.values().toList()

        override fun supportsCapability(
            format: ProofSuiteId,
            capability: org.trustweave.credential.spi.proof.ProofEngineCapabilities.() -> Boolean,
        ) = false
    }

    private fun resolverReturning(result: DidResolutionResult): DidResolver = DidResolver { result }

    private fun document(did: Did) = DidDocument(id = did)

    private val ok = { did: Did -> DidResolutionResult.Success(document(did)) }

    private fun call(
        service: CredentialService,
        resolver: DidResolver,
        keyId: VerificationMethodId? = null,
    ): IssuanceResult =
        runBlocking {
            service.issueForDid(
                didResolver = resolver,
                subjectDid = subject,
                issuerDid = issuer,
                issuerKeyId = keyId,
                type = listOf(CredentialType.VerifiableCredential),
                claims = mapOf("name" to JsonPrimitive("Alice")),
                format = ProofSuiteId.VC_LD,
            )
        }

    // ---- issueForDid ----

    @Test
    fun `issueForDid delegates a request built from the dids and claims when the subject resolves`() {
        val service = RecordingService()
        val keyId = VerificationMethodId(issuer, KeyId("key-1"))

        val result = call(service, resolverReturning(ok(subject)), keyId)

        assertIs<IssuanceResult.Success>(result)
        val request = service.issued!!
        assertEquals(ProofSuiteId.VC_LD, request.format)
        assertEquals(issuer.value, request.issuer.id.value)
        assertEquals(subject.value, request.credentialSubject.id?.value)
        assertEquals(JsonPrimitive("Alice"), request.credentialSubject.claims["name"])
        assertEquals(keyId, request.issuerKeyId)
        assertEquals(listOf(CredentialType.VerifiableCredential), request.type)
    }

    @Test
    fun `issueForDid resolves the subject did, not the issuer did`() {
        var resolved: Did? = null
        val resolver =
            DidResolver { did ->
                resolved = did
                ok(did)
            }
        call(RecordingService(), resolver)
        assertEquals(subject, resolved)
    }

    @Test
    fun `issueForDid does not issue when subject is not found`() {
        val service = RecordingService()
        val result = call(service, resolverReturning(DidResolutionResult.Failure.NotFound(subject)))

        val failure = assertIs<IssuanceResult.Failure.InvalidRequest>(result)
        assertEquals("subjectDid", failure.field)
        assertTrue(failure.reason.contains("not found") && failure.reason.contains(subject.value))
        assertNull(service.issued)
    }

    @Test
    fun `issueForDid reports invalid format with the resolver reason`() {
        val result =
            call(
                RecordingService(),
                resolverReturning(DidResolutionResult.Failure.InvalidFormat("did:bad", "bad syntax")),
            )
        val failure = assertIs<IssuanceResult.Failure.InvalidRequest>(result)
        assertTrue(failure.reason.contains("invalid") && failure.reason.contains("bad syntax"))
    }

    @Test
    fun `issueForDid maps a resolution error to an adapter error preserving the cause`() {
        val boom = RuntimeException("network down")
        val result =
            call(
                RecordingService(),
                resolverReturning(DidResolutionResult.Failure.ResolutionError(subject, "network down", boom)),
            )
        val failure = assertIs<IssuanceResult.Failure.AdapterError>(result)
        assertEquals(ProofSuiteId.VC_LD, failure.format)
        assertSame(boom, failure.cause)
        assertTrue(failure.reason.contains("network down"))
    }

    @Test
    fun `issueForDid reports unregistered method options error and deactivated subject`() {
        val service = RecordingService()
        val method =
            assertIs<IssuanceResult.Failure.InvalidRequest>(
                call(service, resolverReturning(DidResolutionResult.Failure.MethodNotRegistered("zzz"))),
            )
        assertTrue(method.reason.contains("not registered") && method.reason.contains("zzz"))

        val options =
            assertIs<IssuanceResult.Failure.InvalidRequest>(
                call(service, resolverReturning(DidResolutionResult.Failure.OptionsError(subject, "bad option"))),
            )
        assertTrue(options.reason.contains("bad option"))

        val deactivated =
            assertIs<IssuanceResult.Failure.InvalidRequest>(
                call(service, resolverReturning(DidResolutionResult.Deactivated(subject))),
            )
        assertTrue(deactivated.reason.contains("deactivated"))
        assertNull(service.issued)
    }

    // ---- resolveSubjectDid ----

    private fun credentialWith(
        subjectId: String?,
        issuerId: String = "did:key:issuer",
    ) = sampleCredential(issuerId, subjectId)

    private fun resolveSubject(
        credential: VerifiableCredential,
        resolver: DidResolver,
    ): Did? = runBlocking { RecordingService().resolveSubjectDid(resolver, credential) }

    @Test
    fun `resolveSubjectDid returns the did when it resolves`() {
        val result = resolveSubject(credentialWith("did:key:holder"), DidResolver { ok(it) })
        assertEquals(Did("did:key:holder"), result)
    }

    @Test
    fun `resolveSubjectDid returns null for deactivated or failed resolution`() {
        val credential = credentialWith("did:key:holder")
        val holder = Did("did:key:holder")
        assertNull(resolveSubject(credential, resolverReturning(DidResolutionResult.Deactivated(holder))))
        assertNull(resolveSubject(credential, resolverReturning(DidResolutionResult.Failure.NotFound(holder))))
    }

    @Test
    fun `resolveSubjectDid returns null without resolving for non did or missing subject ids`() {
        var calls = 0
        val resolver =
            DidResolver {
                calls++
                ok(it)
            }
        assertNull(resolveSubject(credentialWith("https://example.com/holder"), resolver))
        assertNull(resolveSubject(credentialWith(null), resolver))
        assertEquals(0, calls)
    }

    @Test
    fun `resolveSubjectDid returns null for a malformed did string`() {
        var calls = 0
        val resolver =
            DidResolver {
                calls++
                ok(it)
            }
        // "did:key" has a method but no method-specific identifier, so Did(...) rejects it.
        assertNull(resolveSubject(credentialWith("did:key"), resolver))
        assertEquals(0, calls)
    }

    // ---- verifyIssuerDid ----

    private fun verifyIssuer(
        credential: VerifiableCredential,
        resolver: DidResolver,
    ): Boolean = runBlocking { RecordingService().verifyIssuerDid(resolver, credential) }

    @Test
    fun `verifyIssuerDid is true only for a resolvable did issuer`() {
        assertTrue(verifyIssuer(credentialWith(null), DidResolver { ok(it) }))
        assertFalse(
            verifyIssuer(credentialWith(null), resolverReturning(DidResolutionResult.Failure.NotFound(issuer))),
        )
        assertFalse(verifyIssuer(credentialWith(null), resolverReturning(DidResolutionResult.Deactivated(issuer))))
    }

    @Test
    fun `verifyIssuerDid is false for a non did issuer without resolving`() {
        var calls = 0
        val resolver =
            DidResolver {
                calls++
                ok(it)
            }
        assertFalse(verifyIssuer(credentialWith(null, issuerId = "https://issuer.example.com"), resolver))
        assertEquals(0, calls)
    }

    @Test
    fun `verifyIssuerDid is false when the resolver throws but rethrows cancellation`() {
        assertFalse(verifyIssuer(credentialWith(null), DidResolver { throw IllegalStateException("boom") }))
        assertFailsWith<CancellationException> {
            verifyIssuer(credentialWith(null), DidResolver { throw CancellationException("cancelled") })
        }
    }

    // ---- verifyWithDidResolution ----

    @Test
    fun `verifyWithDidResolution rejects an unresolvable issuer without running verification`() {
        val service = RecordingService()
        val credential = credentialWith(null)
        val result =
            runBlocking {
                service.verifyWithDidResolution(
                    resolverReturning(DidResolutionResult.Failure.NotFound(issuer)),
                    credential,
                )
            }
        val invalid = assertIs<VerificationResult.Invalid.InvalidIssuer>(result)
        assertEquals(issuer.value, invalid.issuerIri.value)
        assertSame(credential, invalid.credential)
        assertTrue(invalid.errors.single().contains(issuer.value))
        assertEquals(0, service.verifyCalls)
    }

    @Test
    fun `verifyWithDidResolution delegates to verify with options and no trust policy`() {
        val credential = credentialWith(null)
        val service = RecordingService()
        val expected =
            VerificationResult.Valid(
                credential = credential,
                issuerIri = Iri(issuer.value),
                subjectIri = null,
                issuedAt = Clock.System.now(),
                expiresAt = null,
            )
        service.verifyResult = expected
        val options = VerificationOptions(checkRevocation = false)

        val result = runBlocking { service.verifyWithDidResolution(DidResolver { ok(it) }, credential, options) }

        assertSame(expected, result)
        assertSame(options, service.lastOptions)
        assertNull(service.lastTrustPolicy)
    }

    @Test
    fun `verifyWithDidResolution skips issuer resolution for non did issuers`() {
        val credential = credentialWith(null, issuerId = "https://issuer.example.com")
        val service = RecordingService()
        service.verifyResult =
            VerificationResult.Invalid.InvalidIssuer(credential, Iri("https://issuer.example.com"), "x")
        var resolves = 0
        val resolver =
            DidResolver {
                resolves++
                ok(it)
            }

        runBlocking { service.verifyWithDidResolution(resolver, credential) }

        assertEquals(0, resolves)
        assertEquals(1, service.verifyCalls)
    }
}

private fun sampleCredential(
    issuerId: String,
    subjectId: String? = "did:key:subject",
): VerifiableCredential =
    VerifiableCredential(
        type = listOf(CredentialType.VerifiableCredential),
        issuer = Issuer.IriIssuer(Iri(issuerId)),
        issuanceDate = Clock.System.now(),
        credentialSubject = CredentialSubject(id = subjectId?.let { Iri(it) }, claims = emptyMap()),
    )
