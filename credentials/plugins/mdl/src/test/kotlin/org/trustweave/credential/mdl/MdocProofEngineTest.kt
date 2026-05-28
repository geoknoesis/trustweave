package org.trustweave.credential.mdl

import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.mdl.engine.MdocCbor
import org.trustweave.credential.mdl.engine.MdocProofEngine
import org.trustweave.credential.mdl.model.IssuerSignedItem
import org.trustweave.credential.mdl.model.MobileSecurityObject
import org.trustweave.credential.mdl.model.ValidityInfo
import org.trustweave.credential.mdl.model.DeviceKeyInfo
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class MdocProofEngineTest {

    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var engine: MdocProofEngine
    private var issuerKeyId: KeyId = KeyId("uninitialized")
    private val issuerDid = Did("did:example:issuer")
    private val holderDid = Did("did:example:holder")

    @BeforeEach
    fun setup() = runBlocking {
        kms = InMemoryKeyManagementService()
        engine = MdocProofEngine(kms)

        val result = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "issuer-key-1"))
        result.shouldBeInstanceOf<GenerateKeyResult.Success>()
        issuerKeyId = (result as GenerateKeyResult.Success).keyHandle.id
    }

    @Test
    fun `issue produces VerifiableCredential with MdocProof`() = runBlocking {
        val request = buildIssuanceRequest()
        val vc = engine.issue(request)

        vc.proof.shouldBeInstanceOf<CredentialProof.MdocProof>()
        val proof = vc.proof as CredentialProof.MdocProof
        proof.docType shouldBe MdlNamespace.MDL_DOC_TYPE
        withClue("deviceResponse must be non-empty") {
            proof.deviceResponse.isNotEmpty().shouldBeTrue()
        }
    }

    @Test
    fun `issued credential encodes all claims in IssuerSignedItems`() = runBlocking {
        val request = buildIssuanceRequest()
        val vc = engine.issue(request)

        val proof = vc.proof as CredentialProof.MdocProof
        val doc = MdocCbor.decodeMobileDocument(proof.deviceResponse)
        val items = doc.issuerSigned.nameSpaces[MdlNamespace.ISO_18013_5_1] ?: emptyList()

        val identifiers = items.map { it.elementIdentifier }.toSet()
        withClue("family_name claim must be present") {
            identifiers.contains("family_name").shouldBeTrue()
        }
        withClue("given_name claim must be present") {
            identifiers.contains("given_name").shouldBeTrue()
        }
    }

    @Test
    fun `MSO valueDigests match re-encoded IssuerSignedItem digests`() = runBlocking {
        val request = buildIssuanceRequest()
        val vc = engine.issue(request)

        val proof = vc.proof as CredentialProof.MdocProof
        val doc = MdocCbor.decodeMobileDocument(proof.deviceResponse)
        val msoBytes = org.trustweave.credential.mdl.engine.MdocCoseSign.extractPayload(doc.issuerSigned.issuerAuth)
        val mso = MdocCbor.decodeMso(msoBytes)

        doc.issuerSigned.nameSpaces.forEach { (ns, items) ->
            items.forEach { item ->
                val expectedDigest = mso.valueDigests[ns]?.get(item.digestId)
                withClue("Digest for item ${item.elementIdentifier} (id=${item.digestId}) must be present in MSO") {
                    expectedDigest shouldNotBe null
                }
                val actualDigest = MdocCbor.digestItem(MdocCbor.encodeIssuerSignedItem(item))
                withClue("Digest for item ${item.elementIdentifier} must match") {
                    expectedDigest!!.contentEquals(actualDigest).shouldBeTrue()
                }
            }
        }
    }

    @Test
    fun `CBOR IssuerSignedItem round-trip preserves all fields`() {
        val original = IssuerSignedItem(
            digestId = 42,
            random = MdocCbor.generateSalt(),
            elementIdentifier = "family_name",
            elementValue = "Smith"
        )
        val encoded = MdocCbor.encodeIssuerSignedItem(original)
        val decoded = MdocCbor.decodeIssuerSignedItem(encoded)

        decoded.digestId shouldBe original.digestId
        decoded.random.contentEquals(original.random).shouldBeTrue()
        decoded.elementIdentifier shouldBe original.elementIdentifier
        decoded.elementValue shouldBe original.elementValue
    }

    @Test
    fun `createPresentation wraps credential in VerifiablePresentation`() = runBlocking {
        val request = buildIssuanceRequest()
        val vc = engine.issue(request)

        val vp = engine.createPresentation(listOf(vc), PresentationRequest())

        vp.verifiableCredential.size shouldBe 1
        vp.verifiableCredential[0].proof.shouldBeInstanceOf<CredentialProof.MdocProof>()
    }

    @Test
    fun `createPresentation with selective disclosure filters claims`() = runBlocking {
        val request = buildIssuanceRequest()
        val vc = engine.issue(request)

        val vp = engine.createPresentation(
            listOf(vc),
            PresentationRequest(disclosedClaims = setOf("family_name"))
        )

        val disclosedVc = vp.verifiableCredential[0]
        val proof = disclosedVc.proof as CredentialProof.MdocProof
        val doc = MdocCbor.decodeMobileDocument(proof.deviceResponse)
        val items = doc.issuerSigned.nameSpaces.values.flatten()

        val identifiers = items.map { it.elementIdentifier }.toSet()
        identifiers.contains("family_name").shouldBeTrue()
        withClue("given_name should have been filtered out") {
            identifiers.contains("given_name") shouldBe false
        }
    }

    private fun buildIssuanceRequest(): IssuanceRequest {
        val vmId = VerificationMethodId(issuerDid, issuerKeyId)
        return IssuanceRequest(
            format = ProofSuiteId.MDOC,
            issuer = Issuer.fromDid(issuerDid),
            issuerKeyId = vmId,
            credentialSubject = CredentialSubject.fromDid(
                holderDid,
                claims = mapOf(
                    "family_name" to JsonPrimitive("Smith"),
                    "given_name" to JsonPrimitive("John"),
                    "age_over_18" to JsonPrimitive(true)
                )
            ),
            type = listOf(
                CredentialType.fromString("VerifiableCredential"),
                CredentialType.fromString(MdlNamespace.MDL_DOC_TYPE)
            ),
            proofOptions = ProofOptions(
                additionalOptions = mapOf(
                    "namespace" to MdlNamespace.ISO_18013_5_1,
                    "docType" to MdlNamespace.MDL_DOC_TYPE,
                    "algorithm" to "Ed25519"
                )
            )
        )
    }
}
