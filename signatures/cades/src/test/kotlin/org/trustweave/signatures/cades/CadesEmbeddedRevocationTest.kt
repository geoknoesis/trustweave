package org.trustweave.signatures.cades

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.DERTaggedObject
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.OtherRevocationInfoFormat
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.esf.RevocationValues
import org.bouncycastle.asn1.ocsp.BasicOCSPResponse
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.CertificateList
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter
import org.bouncycastle.cert.jcajce.JcaX509v2CRLBuilder
import org.bouncycastle.cert.ocsp.OCSPResp
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.SignerInformationStore
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.cades.CadesValidationResult.Invalid
import org.trustweave.signatures.cades.CadesValidationResult.Valid
import org.trustweave.signatures.revocation.CertificateRevocationEvaluator
import org.trustweave.signatures.revocation.RevocationEvidence
import org.trustweave.signatures.revocation.RevocationPolicy
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import org.trustweave.signatures.tsa.TsaConfig
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import kotlin.time.Clock

/** CAdES B-LT: revocation values the signature carries itself are read, verified and bound to the time-stamp. */
class CadesEmbeddedRevocationTest {
    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private lateinit var tsa: TestTsa
    private lateinit var server: MockWebServer
    private val verifier = DefaultCadesVerifier()
    private val payload = "payload".toByteArray()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
        tsa = TestTsa.generate()
        server = MockWebServer().apply { start() }
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/timestamp-reply")
                        .setBody(Buffer().apply { write(tsa.stamp(request.body.readByteArray())) })
            }
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun active() =
        TrustAnchorMatch.QualifiedActive(
            tspName = "Test TSP",
            territory = "EU",
            service =
                TspService(
                    serviceName = "Test CA",
                    serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                    status = TspServiceStatus.GRANTED,
                    statusStartingTime = Clock.System.now() - kotlin.time.Duration.parse("PT8760H"),
                    serviceCertificates = listOf(ca.caCert),
                    qualifierUris = listOf(QualifierUris.QC_WITH_SSCD),
                ),
            qcWithSscd = true,
            qcForESig = true,
        )

    private val resolver =
        object : TrustAnchorResolver {
            override fun resolve(
                signerCert: X509Certificate,
                chain: List<X509Certificate>,
            ): TrustAnchorMatch = active()
        }

    private suspend fun keyId(): KeyId =
        when (val r = kms.generateKey(Algorithm.P256, mapOf("keyId" to "cades-lt-${System.nanoTime()}"))) {
            is GenerateKeyResult.Success -> r.keyHandle.id
            else -> error("KMS keygen failed: $r")
        }

    private class Signed(
        val cms: ByteArray,
        val signer: X509Certificate,
    )

    private suspend fun signed(profile: CadesProfile = CadesProfile.B_T): Signed {
        val id = keyId()
        val chain = ca.issueChainBytes(kms.publicKey(id), "CN=CAdES LT Signer")
        val signature =
            DefaultCadesSigner(kms).sign(
                CadesSigningRequest(
                    profile = profile,
                    keyId = id,
                    payload = payload,
                    signerCertificateChain = chain,
                    tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()).takeIf { profile == CadesProfile.B_T },
                    detached = true,
                ),
            )
        val signer = CertificateFactory.getInstance("X.509").generateCertificate(chain.first().inputStream()) as X509Certificate
        return Signed(signature.encoded, signer)
    }

    private fun options(
        required: CadesProfile,
        policy: RevocationPolicy = RevocationPolicy.NOT_CHECKED,
        evidence: RevocationEvidence = RevocationEvidence.NONE,
    ) = CadesVerificationOptions(
        requiredProfile = required,
        trustAnchorResolver = resolver,
        detachedPayload = payload,
        timestampTrustAnchors = listOf(tsa.cert),
        revocationPolicy = policy,
        revocationEvidence = evidence,
    )

    // ------------------------------------------------------------------ embedding helpers (unsigned data only)

    private fun parse(cms: ByteArray) = CMSSignedData(CMSProcessableByteArray(payload), cms)

    private fun withUnsignedAttribute(
        cms: ByteArray,
        attribute: Attribute,
    ): ByteArray {
        val data = parse(cms)
        val si: SignerInformation = data.signerInfos.signers.single()
        val table = si.unsignedAttributes?.toASN1EncodableVector() ?: ASN1EncodableVector()
        table.add(attribute)
        val replaced = SignerInformation.replaceUnsignedAttributes(si, AttributeTable(table))
        return CMSSignedData.replaceSigners(data, SignerInformationStore(replaced)).encoded
    }

    private fun revocationValuesAttribute(
        crls: List<ByteArray> = emptyList(),
        basicOcsp: List<ByteArray> = emptyList(),
    ): Attribute {
        val rv =
            RevocationValues(
                crls.map { CertificateList.getInstance(it) }.toTypedArray().takeIf { it.isNotEmpty() },
                basicOcsp.map { BasicOCSPResponse.getInstance(it) }.toTypedArray().takeIf { it.isNotEmpty() },
                null,
            )
        return Attribute(PKCSObjectIdentifiers.id_aa_ets_revocationValues, DERSet(rv))
    }

    /** An `OCSPResponse` as the evaluator takes it, to its `BasicOCSPResponse` as the attribute carries it. */
    private fun basic(ocspResponse: ByteArray): ByteArray =
        (OCSPResp(ocspResponse).responseObject as org.bouncycastle.cert.ocsp.BasicOCSPResp).encoded

    /** Rewrites the RFC 5652 `SignedData.crls` field. */
    private fun withSignedDataCrls(
        cms: ByteArray,
        crls: List<ByteArray> = emptyList(),
        ocspResponses: List<ByteArray> = emptyList(),
    ): ByteArray {
        val info = ContentInfo.getInstance(ASN1InputStream(cms).readObject())
        val sd = SignedData.getInstance(info.content)
        val choices = ASN1EncodableVector()
        crls.forEach { choices.add(CertificateList.getInstance(it)) }
        ocspResponses.forEach {
            choices.add(
                DERTaggedObject(
                    false,
                    1,
                    OtherRevocationInfoFormat(CMSObjectIdentifiers.id_ri_ocsp_response, ASN1InputStream(it).readObject()),
                ),
            )
        }
        val rebuilt =
            SignedData(sd.digestAlgorithms, sd.encapContentInfo, sd.certificates, DERSet(choices), sd.signerInfos)
        return ContentInfo(CMSObjectIdentifiers.signedData, rebuilt).getEncoded("DER")
    }

    private fun goodCrl() = RevocationFixtures.crl(ca.caCert, ca.caPrivateKey())

    private fun revokedCrl(signer: X509Certificate) = RevocationFixtures.crl(ca.caCert, ca.caPrivateKey(), listOf(signer.serialNumber))

    private fun ocsp(
        signer: X509Certificate,
        revoked: Boolean = false,
    ) = RevocationFixtures.ocsp(ca.caCert, ca.caPrivateKey(), signer, revoked)

    // ------------------------------------------------------------------ B-LT from the attribute

    @Test
    fun `an embedded CRL covering the chain under a trusted time-stamp yields B-LT`() =
        runBlocking<Unit> {
            val s = signed()
            val lt = withUnsignedAttribute(s.cms, revocationValuesAttribute(crls = listOf(goodCrl())))
            val result = verifier.verify(lt, options(CadesProfile.B_LT))
            assertTrue(result is Valid, "got $result")
            assertEquals(CadesProfile.B_LT, (result as Valid).profile)
            assertTrue(result.revocationChecked)
            assertTrue(result.signatureTimeStamp != null)
        }

    @Test
    fun `an embedded OCSP response yields B-LT`() =
        runBlocking<Unit> {
            val s = signed()
            val lt = withUnsignedAttribute(s.cms, revocationValuesAttribute(basicOcsp = listOf(basic(ocsp(s.signer)))))
            val result = verifier.verify(lt, options(CadesProfile.B_LT))
            assertTrue(result is Valid && result.profile == CadesProfile.B_LT, "got $result")
        }

    @Test
    fun `an embedded CRL listing the signer is refused as revoked`() =
        runBlocking<Unit> {
            val s = signed()
            val lt = withUnsignedAttribute(s.cms, revocationValuesAttribute(crls = listOf(revokedCrl(s.signer))))
            assertTrue(verifier.verify(lt, options(CadesProfile.B_LT)) is Invalid.CertificateRevoked)
            assertTrue(
                verifier.verify(lt, options(CadesProfile.B_T, RevocationPolicy.CHECK_IF_AVAILABLE)) is Invalid.CertificateRevoked,
            )
        }

    @Test
    fun `an embedded OCSP revoked status is refused`() =
        runBlocking<Unit> {
            val s = signed()
            val lt = withUnsignedAttribute(s.cms, revocationValuesAttribute(basicOcsp = listOf(basic(ocsp(s.signer, revoked = true)))))
            assertTrue(verifier.verify(lt, options(CadesProfile.B_LT)) is Invalid.CertificateRevoked)
        }

    // ------------------------------------------------------------------ B-LT from SignedData.crls

    @Test
    fun `CRLs and OCSP responses in the SignedData crls field yield B-LT`() =
        runBlocking<Unit> {
            val s = signed()
            val viaCrl = withSignedDataCrls(s.cms, crls = listOf(goodCrl()))
            val r1 = verifier.verify(viaCrl, options(CadesProfile.B_LT))
            assertTrue(r1 is Valid && r1.profile == CadesProfile.B_LT, "got $r1")
            val viaOcsp = withSignedDataCrls(s.cms, ocspResponses = listOf(ocsp(s.signer)))
            val r2 = verifier.verify(viaOcsp, options(CadesProfile.B_LT))
            assertTrue(r2 is Valid && r2.profile == CadesProfile.B_LT, "got $r2")
            val revoked = withSignedDataCrls(s.cms, ocspResponses = listOf(ocsp(s.signer, revoked = true)))
            assertTrue(verifier.verify(revoked, options(CadesProfile.B_LT)) is Invalid.CertificateRevoked)
        }

    // ------------------------------------------------------------------ what does not make B-LT

    @Test
    fun `no embedded evidence is not B-LT and requiring it fails closed`() =
        runBlocking<Unit> {
            val s = signed()
            val none = verifier.verify(s.cms, options(CadesProfile.B_LT))
            assertTrue(none is Invalid.RevocationUnavailable, "got $none")
            // Caller evidence alone gives current status (B-T), never B-LT.
            val caller = options(CadesProfile.B_LT, RevocationPolicy.REQUIRED, RevocationEvidence(crls = listOf(goodCrl())))
            val callerResult = verifier.verify(s.cms, caller)
            assertTrue(callerResult is Invalid.WrongProfile && callerResult.found == CadesProfile.B_T, "got $callerResult")
            val asBt = verifier.verify(s.cms, caller.copy(requiredProfile = CadesProfile.B_T))
            assertTrue(asBt is Valid && asBt.profile == CadesProfile.B_T && asBt.revocationChecked, "got $asBt")
        }

    @Test
    fun `embedded evidence without a trusted time-stamp is never B-LT`() =
        runBlocking<Unit> {
            val s = signed(CadesProfile.B_B)
            val lt = withUnsignedAttribute(s.cms, revocationValuesAttribute(crls = listOf(goodCrl())))
            val result = verifier.verify(lt, options(CadesProfile.B_LT))
            assertTrue(result is Invalid.WrongProfile, "got $result")
            val bb = verifier.verify(lt, options(CadesProfile.B_B, RevocationPolicy.REQUIRED))
            assertTrue(bb is Valid && bb.profile == CadesProfile.B_B && bb.revocationChecked, "got $bb")
            // A time-stamp from an unconfigured TSA does not count either.
            val stamped = withUnsignedAttribute(signed().cms, revocationValuesAttribute(crls = listOf(goodCrl())))
            val untrusted = verifier.verify(stamped, options(CadesProfile.B_B).copy(timestampTrustAnchors = emptyList()))
            assertTrue(untrusted is Valid && untrusted.profile == CadesProfile.B_B, "got $untrusted")
        }

    @Test
    fun `evidence from an unrelated CA does not cover the chain`() =
        runBlocking<Unit> {
            val s = signed()
            val other = TestCa("CN=Other CA")
            val foreign = RevocationFixtures.crl(other.caCert, other.caPrivateKey())
            val lt = withUnsignedAttribute(s.cms, revocationValuesAttribute(crls = listOf(foreign)))
            assertTrue(verifier.verify(lt, options(CadesProfile.B_LT)) is Invalid.RevocationUnavailable)
        }

    // ------------------------------------------------------------------ malformed and oversize

    @Test
    fun `a malformed revocationValues attribute is refused`() =
        runBlocking<Unit> {
            val s = signed()
            val junk = Attribute(PKCSObjectIdentifiers.id_aa_ets_revocationValues, DERSet(DERSequence(DERSequence())))
            val result = verifier.verify(withUnsignedAttribute(s.cms, junk), options(CadesProfile.B_T, RevocationPolicy.CHECK_IF_AVAILABLE))
            assertTrue(result is Invalid.Malformed, "got $result")
            val empty = Attribute(PKCSObjectIdentifiers.id_aa_ets_revocationValues, DERSet())
            val checked = options(CadesProfile.B_T, RevocationPolicy.CHECK_IF_AVAILABLE)
            assertTrue(verifier.verify(withUnsignedAttribute(s.cms, empty), checked) is Invalid.Malformed)
            val notRv = Attribute(PKCSObjectIdentifiers.id_aa_ets_revocationValues, DERSet(ASN1ObjectIdentifier("1.2.3")))
            assertTrue(verifier.verify(withUnsignedAttribute(s.cms, notRv), checked) is Invalid.Malformed)
        }

    @Test
    fun `junk embedded revocation data does not fail a signature when revocation is not checked`() =
        runBlocking<Unit> {
            val s = signed(CadesProfile.B_B)
            val junk = Attribute(PKCSObjectIdentifiers.id_aa_ets_revocationValues, DERSet(DERSequence(DERSequence())))
            val bytes = withUnsignedAttribute(s.cms, junk)
            val result = verifier.verify(bytes, options(CadesProfile.B_B))
            assertTrue(result is Valid && result.profile == CadesProfile.B_B && !result.revocationChecked, "got $result")
            // Once revocation is evaluated the same junk is refused.
            assertTrue(verifier.verify(bytes, options(CadesProfile.B_B, RevocationPolicy.REQUIRED)) is Invalid.Malformed)
        }

    @Test
    fun `malformed SignedData revocation info is refused`() =
        runBlocking<Unit> {
            val s = signed()
            val info = ContentInfo.getInstance(ASN1InputStream(s.cms).readObject())
            val sd = SignedData.getInstance(info.content)
            val garbage = DERTaggedObject(false, 1, OtherRevocationInfoFormat(CMSObjectIdentifiers.id_ri_ocsp_response, DERSequence()))
            val rebuilt = SignedData(sd.digestAlgorithms, sd.encapContentInfo, sd.certificates, DERSet(garbage), sd.signerInfos)
            val bytes = ContentInfo(CMSObjectIdentifiers.signedData, rebuilt).getEncoded("DER")
            val result = verifier.verify(bytes, options(CadesProfile.B_T, RevocationPolicy.CHECK_IF_AVAILABLE))
            assertTrue(result is Invalid.Malformed, "got $result")
        }

    @Test
    fun `surplus embedded items beyond the limit are dropped`() =
        runBlocking<Unit> {
            val s = signed()
            val other = TestCa("CN=Filler CA")
            val filler = RevocationFixtures.crl(other.caCert, other.caPrivateKey())
            // The good CRL is item MAX_ITEMS + 1, so it is never seen.
            val many = List(CertificateRevocationEvaluator.MAX_ITEMS) { filler } + goodCrl()
            val lt = withUnsignedAttribute(s.cms, revocationValuesAttribute(crls = many))
            assertTrue(verifier.verify(lt, options(CadesProfile.B_LT)) is Invalid.RevocationUnavailable)
            val within = List(CertificateRevocationEvaluator.MAX_ITEMS - 1) { filler } + goodCrl()
            val ok = withUnsignedAttribute(s.cms, revocationValuesAttribute(crls = within))
            assertTrue(verifier.verify(ok, options(CadesProfile.B_LT)) is Valid)
        }

    @Test
    fun `an oversize embedded item is dropped even though it is genuine`() =
        runBlocking<Unit> {
            val s = signed()
            val builder = JcaX509v2CRLBuilder(ca.caCert.subjectX500Principal, Date()).setNextUpdate(RevocationFixtures.ahead(24))
            builder.addExtension(
                ASN1ObjectIdentifier("1.3.6.1.4.1.99999.1"),
                false,
                DEROctetString(ByteArray(CertificateRevocationEvaluator.MAX_ITEM_BYTES + 1)),
            )
            val huge =
                JcaX509CRLConverter()
                    .getCRL(
                        builder.build(JcaContentSignerBuilder("SHA256withRSA").build(ca.caPrivateKey())),
                    ).encoded
            assertTrue(huge.size > CertificateRevocationEvaluator.MAX_ITEM_BYTES)
            val lt = withUnsignedAttribute(s.cms, revocationValuesAttribute(crls = listOf(huge)))
            assertTrue(verifier.verify(lt, options(CadesProfile.B_LT)) is Invalid.RevocationUnavailable)
            // The same CRL passed by the caller is still bounded by the evaluator.
            val viaCaller = options(CadesProfile.B_T, RevocationPolicy.REQUIRED, RevocationEvidence(crls = listOf(huge)))
            assertTrue(verifier.verify(s.cms, viaCaller) is Invalid.RevocationUnavailable)
        }

    @Test
    fun `B-LT cannot be requested from the signer`() {
        val failure = runCatching { CadesSigningRequest(CadesProfile.B_LT, KeyId("k"), payload, listOf(ByteArray(1))) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "got $failure")
    }
}
