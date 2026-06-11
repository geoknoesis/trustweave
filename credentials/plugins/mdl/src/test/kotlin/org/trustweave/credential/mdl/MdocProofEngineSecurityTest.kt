package org.trustweave.credential.mdl

import com.upokecenter.cbor.CBORObject
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.mdl.engine.MdocCbor
import org.trustweave.credential.mdl.engine.MdocCoseSign
import org.trustweave.credential.mdl.engine.MdocProofEngine
import org.trustweave.credential.mdl.model.DeviceAuth
import org.trustweave.credential.mdl.model.DeviceSigned
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date

/**
 * Security-hardening tests for [MdocProofEngine]:
 * envelope reconciliation, selective-disclosure envelope filtering, explicit issuer trust
 * configuration (KMS opt-in / x5chain + IACA anchors), and device-auth fail-closed behavior.
 */
class MdocProofEngineSecurityTest {

    private lateinit var kms: InMemoryKeyManagementService
    private lateinit var engineOptIn: MdocProofEngine
    private lateinit var engineDefault: MdocProofEngine
    private val issuerDid = Did("did:example:issuer")
    private val holderDid = Did("did:example:holder")

    // The KMS opt-in path resolves the issuer key under KeyId(credential.issuer.id),
    // so the test key is registered under the issuer DID itself.
    private val issuerKeyId = KeyId(issuerDid.value)

    @BeforeEach
    fun setup() = runBlocking<Unit> {
        kms = InMemoryKeyManagementService()
        val result = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to issuerKeyId.value))
        result.shouldBeInstanceOf<GenerateKeyResult.Success>()

        engineOptIn = MdocProofEngine(
            kms,
            config = ProofEngineConfig(
                properties = mapOf(MdocProofEngine.OPTION_ALLOW_KMS_ISSUER_KEY_LOOKUP to true)
            )
        )
        engineDefault = MdocProofEngine(kms)
    }

    // ------------------------------------------------------------------------------
    // Issuer trust configuration
    // ------------------------------------------------------------------------------

    @Test
    fun `issued credential verifies via explicit KMS opt-in (happy path)`() = runBlocking<Unit> {
        val vc = engineOptIn.issue(buildIssuanceRequest())
        val result = engineOptIn.verify(vc, VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Valid>()
    }

    @Test
    fun `verification without opt-in and without x5chain fails closed`() = runBlocking<Unit> {
        val vc = engineOptIn.issue(buildIssuanceRequest())
        val result = engineDefault.verify(vc, VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        result.reason
            .shouldContain(MdocProofEngine.OPTION_ALLOW_KMS_ISSUER_KEY_LOOKUP)
    }

    @Test
    fun `x5chain without configured IACA anchors fails closed even with KMS opt-in`() = runBlocking<Unit> {
        val keyPair = generateP256KeyPair()
        val cert = generateSelfSignedCert(keyPair)
        val vc = engineOptIn.issue(buildIssuanceRequest())
        val resigned = resignWithX5Chain(vc, keyPair, cert)

        val result = engineOptIn.verify(resigned, VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        result.reason.shouldContain("IACA trust anchors")
    }

    @Test
    fun `x5chain verifies against configured IACA trust anchor`() = runBlocking<Unit> {
        val keyPair = generateP256KeyPair()
        val cert = generateSelfSignedCert(keyPair)
        val vc = engineOptIn.issue(buildIssuanceRequest())
        val resigned = resignWithX5Chain(vc, keyPair, cert)

        val engineWithAnchor = MdocProofEngine(
            kms,
            config = ProofEngineConfig(
                properties = mapOf(MdocProofEngine.OPTION_IACA_TRUST_ANCHORS to listOf(cert))
            )
        )
        val result = engineWithAnchor.verify(resigned, VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Valid>()
    }

    @Test
    fun `x5chain not chaining to the configured anchor fails`() = runBlocking<Unit> {
        val keyPair = generateP256KeyPair()
        val cert = generateSelfSignedCert(keyPair)
        val unrelatedAnchor = generateSelfSignedCert(generateP256KeyPair(), "CN=Other IACA")
        val vc = engineOptIn.issue(buildIssuanceRequest())
        val resigned = resignWithX5Chain(vc, keyPair, cert)

        val engineWithWrongAnchor = MdocProofEngine(
            kms,
            config = ProofEngineConfig(
                properties = mapOf(MdocProofEngine.OPTION_IACA_TRUST_ANCHORS to listOf(unrelatedAnchor))
            )
        )
        val result = engineWithWrongAnchor.verify(resigned, VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
    }

    @Test
    fun `x5chain signature not matching the leaf certificate fails`() = runBlocking<Unit> {
        val keyPair = generateP256KeyPair()
        val wrongCert = generateSelfSignedCert(generateP256KeyPair(), "CN=Wrong Key Cert")
        val vc = engineOptIn.issue(buildIssuanceRequest())
        // Signed with keyPair but carrying a certificate for a DIFFERENT key.
        val resigned = resignWithX5Chain(vc, keyPair, wrongCert)

        val engineWithAnchor = MdocProofEngine(
            kms,
            config = ProofEngineConfig(
                properties = mapOf(MdocProofEngine.OPTION_IACA_TRUST_ANCHORS to listOf(wrongCert))
            )
        )
        val result = engineWithAnchor.verify(resigned, VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
    }

    // ------------------------------------------------------------------------------
    // Envelope reconciliation
    // ------------------------------------------------------------------------------

    @Test
    fun `swapped envelope claim value fails verification`() = runBlocking<Unit> {
        val vc = engineOptIn.issue(buildIssuanceRequest())
        val tampered = vc.withEnvelopeClaims(
            vc.credentialSubject.claims + ("family_name" to JsonPrimitive("Mallory"))
        )

        val result = engineOptIn.verify(tampered, VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        result.reason.shouldContain("family_name")
    }

    @Test
    fun `unbacked envelope claim fails verification`() = runBlocking<Unit> {
        val vc = engineOptIn.issue(buildIssuanceRequest())
        val tampered = vc.withEnvelopeClaims(
            vc.credentialSubject.claims + ("admin" to JsonPrimitive(true))
        )

        val result = engineOptIn.verify(tampered, VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
        result.reason.shouldContain("not backed")
    }

    @Test
    fun `swapped envelope boolean claim fails verification`() = runBlocking<Unit> {
        val vc = engineOptIn.issue(buildIssuanceRequest())
        val tampered = vc.withEnvelopeClaims(
            vc.credentialSubject.claims + ("age_over_18" to JsonPrimitive(false))
        )

        val result = engineOptIn.verify(tampered, VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
    }

    @Test
    fun `untampered envelope with mixed claim types verifies`() = runBlocking<Unit> {
        val vc = engineOptIn.issue(
            buildIssuanceRequest(
                claims = mapOf(
                    "family_name" to JsonPrimitive("Smith"),
                    "age" to JsonPrimitive(42L),
                    "height_m" to JsonPrimitive(1.85),
                    "age_over_18" to JsonPrimitive(true)
                )
            )
        )
        val result = engineOptIn.verify(vc, VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Valid>()
    }

    // ------------------------------------------------------------------------------
    // Selective disclosure: envelope filtering
    // ------------------------------------------------------------------------------

    @Test
    fun `createPresentation filters the envelope credentialSubject to disclosed claims`() = runBlocking<Unit> {
        val vc = engineOptIn.issue(buildIssuanceRequest())

        val vp = engineOptIn.createPresentation(
            listOf(vc),
            PresentationRequest(disclosedClaims = setOf("family_name"))
        )

        val presented = vp.verifiableCredential[0]
        presented.credentialSubject.claims.shouldContainExactly(
            mapOf<String, JsonElement>("family_name" to JsonPrimitive("Smith"))
        )
        // Holder id is retained on the envelope.
        presented.credentialSubject.id?.value shouldBe holderDid.value
    }

    @Test
    fun `presented credential with filtered envelope verifies`() = runBlocking<Unit> {
        val vc = engineOptIn.issue(buildIssuanceRequest())
        val vp = engineOptIn.createPresentation(
            listOf(vc),
            PresentationRequest(disclosedClaims = setOf("family_name"))
        )

        val result = engineOptIn.verify(vp.verifiableCredential[0], VerificationOptions())
        result.shouldBeInstanceOf<VerificationResult.Valid>()
    }

    // ------------------------------------------------------------------------------
    // Device authentication fail-closed behavior
    // ------------------------------------------------------------------------------

    @Test
    fun `device-signed document without session transcript fails when presentation verification requested`() =
        runBlocking<Unit> {
            val vc = engineOptIn.issue(buildIssuanceRequest())
            val withDevice = vc.withDummyDeviceSigned()

            // Default options request presentation proof verification (verifyPresentationProof = true).
            val result = engineOptIn.verify(withDevice, VerificationOptions())
            result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
            result.reason
                .shouldContain(MdocProofEngine.OPTION_SESSION_TRANSCRIPT)
        }

    @Test
    fun `device-signed document without transcript is skipped for explicit issuer-only verification`() =
        runBlocking<Unit> {
            val vc = engineOptIn.issue(buildIssuanceRequest())
            val withDevice = vc.withDummyDeviceSigned()

            val issuerOnly = VerificationOptions(
                verifyPresentationProof = false,
                enforceHolderBinding = false
            )
            val result = engineOptIn.verify(withDevice, issuerOnly)
            result.shouldBeInstanceOf<VerificationResult.Valid>()
        }

    @Test
    fun `session transcript provided but no device-signed data fails when presentation verification requested`() =
        runBlocking<Unit> {
            val vc = engineOptIn.issue(buildIssuanceRequest())

            val options = VerificationOptions(
                additionalOptions = mapOf(
                    MdocProofEngine.OPTION_SESSION_TRANSCRIPT to CBORObject.NewArray().EncodeToBytes()
                )
            )
            val result = engineOptIn.verify(vc, options)
            result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
            result.reason.shouldContain("deviceSigned")
        }

    @Test
    fun `session transcript with garbage device signature fails`() = runBlocking<Unit> {
        val vc = engineOptIn.issue(buildIssuanceRequest())
        val withDevice = vc.withDummyDeviceSigned()

        val options = VerificationOptions(
            additionalOptions = mapOf(
                MdocProofEngine.OPTION_SESSION_TRANSCRIPT to CBORObject.NewArray().EncodeToBytes()
            )
        )
        val result = engineOptIn.verify(withDevice, options)
        result.shouldBeInstanceOf<VerificationResult.Invalid.InvalidProof>()
    }

    // ------------------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------------------

    private fun buildIssuanceRequest(
        claims: Map<String, JsonElement> = mapOf(
            "family_name" to JsonPrimitive("Smith"),
            "given_name" to JsonPrimitive("John"),
            "age_over_18" to JsonPrimitive(true)
        )
    ): IssuanceRequest {
        return IssuanceRequest(
            format = ProofSuiteId.MDOC,
            issuer = Issuer.fromDid(issuerDid),
            issuerKeyId = VerificationMethodId(issuerDid, issuerKeyId),
            credentialSubject = CredentialSubject.fromDid(holderDid, claims = claims),
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

    private fun VerifiableCredential.withEnvelopeClaims(
        claims: Map<String, JsonElement>
    ): VerifiableCredential =
        copy(credentialSubject = credentialSubject.copy(claims = claims))

    /** Attach an (unverifiable) deviceSigned structure, simulating a presentation artifact. */
    private fun VerifiableCredential.withDummyDeviceSigned(): VerifiableCredential {
        val mdocProof = proof as CredentialProof.MdocProof
        val doc = MdocCbor.decodeMobileDocument(mdocProof.deviceResponse)
        val dummyCoseSign1 = CBORObject.NewArray().apply {
            Add(CBORObject.FromObject(ByteArray(0))) // protected
            Add(CBORObject.NewOrderedMap()) // unprotected
            Add(CBORObject.FromObject(ByteArray(0))) // payload
            Add(CBORObject.FromObject(ByteArray(64))) // signature
        }.EncodeToBytes()
        val withDevice = doc.copy(
            deviceSigned = DeviceSigned(
                nameSpaces = CBORObject.NewOrderedMap().EncodeToBytes(),
                deviceAuth = DeviceAuth(deviceSignature = dummyCoseSign1)
            )
        )
        return copy(
            proof = CredentialProof.MdocProof(
                deviceResponse = MdocCbor.encodeMobileDocument(withDevice),
                docType = mdocProof.docType
            )
        )
    }

    /**
     * Re-sign the credential's MSO with [keyPair] (ES256) and embed [cert] as the COSE
     * `x5chain` (header label 33) in the unprotected header â€” simulating an ISO 18013-5
     * DocSigner-certificate-based credential.
     */
    private fun resignWithX5Chain(
        vc: VerifiableCredential,
        keyPair: KeyPair,
        cert: X509Certificate
    ): VerifiableCredential {
        val mdocProof = vc.proof as CredentialProof.MdocProof
        val doc = MdocCbor.decodeMobileDocument(mdocProof.deviceResponse)
        val msoBytes = MdocCoseSign.extractPayload(doc.issuerSigned.issuerAuth)

        // Protected header: { alg: ES256 (-7) }
        val protectedMap = CBORObject.NewOrderedMap()
        protectedMap[CBORObject.FromObject(1)] = CBORObject.FromObject(-7)
        val protectedBytes = protectedMap.EncodeToBytes()

        // Sig_structure = ["Signature1", protected, external-aad, payload]
        val sigStructure = CBORObject.NewArray().apply {
            Add(CBORObject.FromObject("Signature1"))
            Add(CBORObject.FromObject(protectedBytes))
            Add(CBORObject.FromObject(ByteArray(0)))
            Add(CBORObject.FromObject(msoBytes))
        }
        val signer = Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyPair.private)
            update(sigStructure.EncodeToBytes())
        }
        val rawSignature = derToRawSignature(signer.sign())

        val unprotectedMap = CBORObject.NewOrderedMap()
        unprotectedMap[CBORObject.FromObject(33)] = CBORObject.FromObject(cert.encoded)

        val coseSign1 = CBORObject.NewArray().apply {
            Add(CBORObject.FromObject(protectedBytes))
            Add(unprotectedMap)
            Add(CBORObject.FromObject(msoBytes))
            Add(CBORObject.FromObject(rawSignature))
        }

        val newDoc = doc.copy(
            issuerSigned = doc.issuerSigned.copy(issuerAuth = coseSign1.EncodeToBytes())
        )
        return vc.copy(
            proof = CredentialProof.MdocProof(
                deviceResponse = MdocCbor.encodeMobileDocument(newDoc),
                docType = mdocProof.docType
            )
        )
    }

    private fun generateP256KeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()

    private fun generateSelfSignedCert(
        keyPair: KeyPair,
        subject: String = "CN=Test IACA"
    ): X509Certificate {
        val name = X500Name(subject)
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(now),
            Date(now - 3_600_000L),
            Date(now + 86_400_000L),
            name,
            keyPair.public
        )
        val contentSigner = JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(contentSigner))
    }

    /** Convert a DER ECDSA signature to the raw `r || s` form used by COSE (P-256: 32+32). */
    private fun derToRawSignature(der: ByteArray, componentSize: Int = 32): ByteArray {
        val seq = ASN1Sequence.getInstance(der)
        fun pad(value: BigInteger): ByteArray {
            val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
            require(bytes.size <= componentSize) { "Signature component too large" }
            return ByteArray(componentSize - bytes.size) + bytes
        }
        val r = (seq.getObjectAt(0) as ASN1Integer).positiveValue
        val s = (seq.getObjectAt(1) as ASN1Integer).positiveValue
        return pad(r) + pad(s)
    }
}
