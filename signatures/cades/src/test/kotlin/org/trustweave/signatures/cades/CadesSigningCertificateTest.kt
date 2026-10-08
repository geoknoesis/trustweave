package org.trustweave.signatures.cades

import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerIdentifier
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.cades.CadesValidationResult.Invalid
import org.trustweave.signatures.cades.CadesValidationResult.Valid
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import kotlin.time.Clock

/** EN 319 122-1 5.2.2: the signing-certificate attribute binds the signature to one certificate. */
class CadesSigningCertificateTest {
    private val kms = TestKms()
    private val ca = TestCa()
    private val verifier = DefaultCadesVerifier()
    private val payload = "payload".toByteArray()

    private val resolver =
        object : TrustAnchorResolver {
            override fun resolve(
                signerCert: X509Certificate,
                chain: List<X509Certificate>,
            ): TrustAnchorMatch =
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
        }

    private fun options() =
        CadesVerificationOptions(
            requiredProfile = CadesProfile.B_B,
            trustAnchorResolver = resolver,
        )

    @Test
    fun `a signature with the signing certificate is valid`() =
        runBlocking<Unit> {
            val keyId = generateKey()
            val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Signer")
            val signature =
                DefaultCadesSigner(kms).sign(CadesSigningRequest(CadesProfile.B_B, keyId, payload, chain, detached = false))
            assertTrue(verifier.verify(signature.encoded, options()) is Valid)
        }

    @Test
    fun `swapping in a second certificate for the same key is rejected`() =
        runBlocking<Unit> {
            val keyId = generateKey()
            val publicKey = kms.publicKey(keyId)
            val first = ca.issue(publicKey, "CN=Signer")
            val second = ca.issue(publicKey, "CN=Someone Else")
            val signature =
                DefaultCadesSigner(kms).sign(
                    CadesSigningRequest(CadesProfile.B_B, keyId, payload, listOf(first.encoded, ca.caCert.encoded), detached = false),
                )
            assertTrue(verifier.verify(signature.encoded, options()) is Valid)

            // The certificate set and the SignerIdentifier are not covered by the signature: re-point both at `second`.
            val cms = CMSSignedData(signature.encoded)
            val signedData = SignedData.getInstance(cms.toASN1Structure().content)
            val original = SignerInfo.getInstance(signedData.signerInfos.getObjectAt(0))
            val swapped =
                SignerInfo(
                    SignerIdentifier(IssuerAndSerialNumber(JcaX509CertificateHolder(second).toASN1Structure())),
                    original.digestAlgorithm,
                    original.authenticatedAttributes,
                    original.digestEncryptionAlgorithm,
                    original.encryptedDigest,
                    original.unauthenticatedAttributes,
                )
            val withSecond = CMSSignedData.replaceCertificatesAndCRLs(cms, JcaCertStore(listOf(second, ca.caCert)), null, null)
            val rebuilt =
                SignedData(
                    signedData.digestAlgorithms,
                    signedData.encapContentInfo,
                    SignedData.getInstance(withSecond.toASN1Structure().content).certificates,
                    signedData.crLs,
                    DERSet(swapped),
                )
            val forged = ContentInfo(CMSObjectIdentifiers.signedData, rebuilt).encoded

            val result = verifier.verify(forged, options())
            assertTrue(result is Invalid.BadSignature, "expected BadSignature, got $result")
        }

    @Test
    fun `a signature without a signing-certificate attribute is rejected`() =
        runBlocking<Unit> {
            val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            val cert = ca.issue(pair.public, "CN=No ESS")
            val generator = CMSSignedDataGenerator()
            generator.addSignerInfoGenerator(
                JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                    .build(JcaContentSignerBuilder("SHA256withECDSA").build(pair.private), cert),
            )
            generator.addCertificates(JcaCertStore(listOf(cert, ca.caCert)))
            val encoded = generator.generate(CMSProcessableByteArray(payload), true).encoded

            val result = verifier.verify(encoded, options())
            assertTrue(result is Invalid.Malformed, "expected Malformed, got $result")
        }

    @Test
    fun `a back-dated claimed signing time does not rescue an expired certificate`() =
        runBlocking<Unit> {
            val keyId = generateKey()
            val expired =
                ca.issue(
                    kms.publicKey(keyId),
                    "CN=Expired",
                    notBefore = java.util.Date(System.currentTimeMillis() - 30L * 86_400_000),
                    notAfter = java.util.Date(System.currentTimeMillis() - 86_400_000),
                )
            val signature =
                DefaultCadesSigner(kms).sign(
                    CadesSigningRequest(
                        profile = CadesProfile.B_B,
                        keyId = keyId,
                        payload = payload,
                        signerCertificateChain = listOf(expired.encoded, ca.caCert.encoded),
                        signingTime = Clock.System.now() - kotlin.time.Duration.parse("P10D"),
                        detached = false,
                    ),
                )
            val result = verifier.verify(signature.encoded, options())
            assertTrue(result is Invalid.CertificateExpired, "expected CertificateExpired, got $result")
        }

    private suspend fun generateKey(): KeyId {
        val result = kms.generateKey(Algorithm.P256, mapOf("keyId" to "ess-key"))
        return when (result) {
            is GenerateKeyResult.Success -> result.keyHandle.id
            else -> error("KMS keygen failed: $result")
        }
    }
}
