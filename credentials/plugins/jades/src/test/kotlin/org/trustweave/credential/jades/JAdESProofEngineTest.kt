package org.trustweave.credential.jades

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.proof.ProofPurpose
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.identifiers.Did
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.trustlists.DefaultTrustAnchorResolver
import org.trustweave.signatures.trustlists.MemberStateTsl
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustList
import org.trustweave.signatures.trustlists.TrustedTSP
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType

class JAdESProofEngineTest {

    private lateinit var kms: TestKms
    private lateinit var ca: TestCa

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
    }

    @Test
    fun `provider basic metadata`() {
        val engine = JAdESProofEngine(kms = kms, trustAnchorResolver = resolverFor(ca))
        assertEquals(ProofSuiteId.JADES, engine.format)
        assertEquals("ETSI TS 119 182-1 JAdES", engine.formatName)
        assertEquals("B-B, B-T", engine.formatVersion)
    }

    @Test
    fun `issues and verifies a JAdES B-B credential (Ed25519)`() = runBlocking {
        val resolver = resolverFor(ca)
        val engine = JAdESProofEngine(kms = kms, trustAnchorResolver = resolver)

        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Ed25519 JAdES Signer")

        val issueRequest = buildIssuanceRequest(
            keyId = keyId,
            chain = chain,
            profile = JAdESProofEngine.PROFILE_B_B,
        )
        val credential = engine.issue(issueRequest)

        assertNotNull(credential.proof, "engine must attach a proof")
        val jadesProof = credential.proof as CredentialProof.JAdES
        assertEquals(JAdESProofEngine.PROFILE_B_B, jadesProof.profile)
        assertTrue(jadesProof.jws.isNotBlank())

        val verification = engine.verify(credential, VerificationOptions())
        assertTrue(
            verification is VerificationResult.Valid,
            "expected Valid, got $verification",
        )
        verification as VerificationResult.Valid
        assertEquals("did:key:issuer", verification.issuerIri.value)
        assertEquals("qualifiedActive", verification.formatMetadata["trustStatus"]?.toString()?.trim('"'))
    }

    @Test
    fun `verifying with a different CA returns UntrustedSigner via InvalidProof`() = runBlocking {
        val resolver = resolverFor(ca)
        val engine = JAdESProofEngine(kms = kms, trustAnchorResolver = resolver)

        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Ed25519 Signer")
        val credential = engine.issue(
            buildIssuanceRequest(keyId, chain, JAdESProofEngine.PROFILE_B_B),
        )

        // Verify with a resolver that knows about a DIFFERENT CA
        val otherCa = TestCa("CN=Unrelated CA")
        val otherResolverEngine = JAdESProofEngine(kms = kms, trustAnchorResolver = resolverFor(otherCa))
        val verification = otherResolverEngine.verify(credential, VerificationOptions())

        assertTrue(verification is VerificationResult.Invalid.InvalidProof, "got $verification")
        val invalid = verification as VerificationResult.Invalid.InvalidProof
        assertTrue(
            invalid.reason.contains("UntrustedSigner"),
            "expected reason to mention UntrustedSigner; got '${invalid.reason}'",
        )
    }

    @Test
    fun `verifying a credential whose proof is not JAdES fails cleanly`() = runBlocking {
        val resolver = resolverFor(ca)
        val engine = JAdESProofEngine(kms = kms, trustAnchorResolver = resolver)

        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Ed25519 Signer")
        val credential = engine.issue(buildIssuanceRequest(keyId, chain, JAdESProofEngine.PROFILE_B_B))

        // Replace the proof with a wrong variant
        val twistedCredential = credential.copy(
            proof = CredentialProof.JwtProof(jwt = "eyJ...not.actually.a.jws"),
        )
        val verification = engine.verify(twistedCredential, VerificationOptions())
        assertTrue(verification is VerificationResult.Invalid.InvalidProof, "got $verification")
    }

    @Test
    fun `issue rejects missing signerCertificateChain`() = runBlocking {
        val engine = JAdESProofEngine(kms = kms, trustAnchorResolver = resolverFor(ca))
        val keyId = generateKey(Algorithm.Ed25519)
        val req = IssuanceRequest(
            format = ProofSuiteId.JADES,
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject")),
            type = listOf(CredentialType.VerifiableCredential),
            proofOptions = ProofOptions(
                purpose = ProofPurpose.AssertionMethod,
                additionalOptions = mapOf("keyId" to keyId.value),
            ),
        )
        val thrown = runCatching { engine.issue(req) }.exceptionOrNull()
        assertTrue(thrown is JAdESEngineException, "expected JAdESEngineException, got $thrown")
        thrown as JAdESEngineException
        assertTrue(thrown.message!!.contains("signerCertificateChain"))
    }

    @Test
    fun `verify falls back to additionalOptions trustAnchorResolver when not constructor-injected`() = runBlocking {
        // Engine constructed WITHOUT a resolver
        val engine = JAdESProofEngine(kms = kms)
        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Ed25519 Signer")
        val credential = engine.issue(buildIssuanceRequest(keyId, chain, JAdESProofEngine.PROFILE_B_B))

        val verification = engine.verify(
            credential,
            VerificationOptions(
                additionalOptions = mapOf("trustAnchorResolver" to resolverFor(ca)),
            ),
        )
        assertTrue(verification is VerificationResult.Valid, "got $verification")
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun generateKey(algorithm: Algorithm): KeyId {
        val keyId = "test-${algorithm.name}"
        val result = kms.generateKey(algorithm, mapOf("keyId" to keyId))
        return when (result) {
            is GenerateKeyResult.Success -> result.keyHandle.id
            else -> error("KMS keygen failed: $result")
        }
    }

    private fun buildIssuanceRequest(
        keyId: KeyId,
        chain: List<ByteArray>,
        profile: String,
    ): IssuanceRequest = IssuanceRequest(
        format = ProofSuiteId.JADES,
        issuer = Issuer.fromDid(Did("did:key:issuer")),
        credentialSubject = CredentialSubject.fromDid(Did("did:key:subject")),
        type = listOf(CredentialType.VerifiableCredential),
        proofOptions = ProofOptions(
            purpose = ProofPurpose.AssertionMethod,
            additionalOptions = mapOf(
                "keyId" to keyId.value,
                "signerCertificateChain" to chain,
                "profile" to profile,
            ),
        ),
    )

    private fun resolverFor(testCa: TestCa): DefaultTrustAnchorResolver {
        val service = TspService(
            serviceName = "Test CA",
            serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
            status = TspServiceStatus.GRANTED,
            statusStartingTime = Clock.System.now(),
            serviceCertificates = listOf(testCa.caCert),
            qualifierUris = listOf(QualifierUris.QC_WITH_SSCD, QualifierUris.QC_FOR_ESIG),
        )
        val trustList = TrustList(
            schemeOperator = "Test",
            sequenceNumber = 1,
            issuedAt = Clock.System.now(),
            nextUpdateAt = null,
            memberStateLists = listOf(
                MemberStateTsl(
                    territory = "EU",
                    schemeOperator = "Test",
                    sequenceNumber = 1,
                    issuedAt = Clock.System.now(),
                    trustedTsps = listOf(
                        TrustedTSP(name = "Test TSP", tradeName = null, services = listOf(service)),
                    ),
                ),
            ),
        )
        return DefaultTrustAnchorResolver(trustList)
    }
}
