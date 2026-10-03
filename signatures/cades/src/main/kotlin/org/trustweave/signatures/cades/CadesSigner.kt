package org.trustweave.signatures.cades

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.SignedData
import org.bouncycastle.asn1.cms.SignerInfo
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.ess.ESSCertIDv2
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator
import org.bouncycastle.cms.SignerInfoGenerator
import org.bouncycastle.cms.SignerInfoGeneratorBuilder
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.SignerInformationStore
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.signatures.cades.internal.AlgorithmMapping
import org.trustweave.signatures.tsa.BouncyCastleTsaClient
import org.trustweave.signatures.tsa.TsaClient
import org.trustweave.signatures.tsa.TsaConfig
import org.trustweave.signatures.tsa.TsaHashAlgorithm
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Hashtable

/**
 * Builds a CAdES signature for an arbitrary binary payload.
 *
 * The signing key never leaves the configured [KeyManagementService] — the signer constructs the
 * CMS `SignedAttributes` blob locally and only the DER-encoded attributes cross the KMS boundary
 * for signing. This matches the JAdES signer's HSM-friendly contract.
 */
interface CadesSigner {
    /**
     * Sign [request].
     *
     * @throws CadesSignerException on any failure: KMS unavailable, key not found, TSA failure,
     *         malformed cert chain, or unsupported algorithm.
     */
    suspend fun sign(request: CadesSigningRequest): CadesSignature
}

/** Thrown by [DefaultCadesSigner] on unrecoverable failures during signing. */
class CadesSignerException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Default [CadesSigner] implementation using Bouncy Castle's CMS package.
 *
 * @param kms              Key management service holding the signing key.
 * @param tsaClientFactory Optional factory the signer calls to obtain a [TsaClient] when the
 *                         signing request asks for [CadesProfile.B_T]. Defaults to
 *                         [BouncyCastleTsaClient]; override in tests for a stub.
 */
class DefaultCadesSigner(
    private val kms: KeyManagementService,
    private val tsaClientFactory: (TsaConfig) -> TsaClient = ::BouncyCastleTsaClient,
) : CadesSigner {
    override suspend fun sign(request: CadesSigningRequest): CadesSignature =
        withContext(Dispatchers.IO) {
            val publicKey =
                when (val r = kms.getPublicKey(request.keyId)) {
                    is GetPublicKeyResult.Success -> r.keyHandle
                    else -> throw CadesSignerException("Cannot resolve public key for ${request.keyId}: $r")
                }
            val algorithm =
                Algorithm.parse(publicKey.algorithm)
                    ?: throw CadesSignerException("Unknown key algorithm '${publicKey.algorithm}'")
            val mapping =
                AlgorithmMapping.forAlgorithm(algorithm)
                    ?: throw CadesSignerException(
                        "Algorithm $algorithm has no CAdES mapping — MVP supports ECDSA P-256/384/521 and Ed25519",
                    )

            val chain = decodeChain(request.signerCertificateChain)
            val signerCert = chain.first()
            val signingTime = request.signingTime ?: Clock.System.now()

            // Bouncy Castle's ContentSigner SPI is synchronous, so the KMS cannot be called from
            // inside it without blocking a thread. Instead: pass 1 lets BC assemble the CMS with a
            // capturing signer that records the DER SignedAttributes (the exact signing input);
            // the suspending KMS call happens here, in the coroutine; pass 2 splices the real
            // signature value into the SignerInfo.
            val contentSigner = CapturingContentSigner(mapping.signatureAlgorithmIdentifier())
            val signedAttrGen =
                CadesSignedAttributeTableGenerator(
                    signingTimeMillis = signingTime.toEpochMilliseconds(),
                    signerCert = signerCert,
                )
            // Lower-level SignerInfoGeneratorBuilder lets us pin the SignerInfo's digestAlgorithm
            // explicitly via setContentDigest(). This is necessary because BC's default digest
            // finder doesn't always derive a digest for Ed25519 (RFC 8419 says id-Ed25519 has no
            // companion "withHash" OID; the SignerInfo MUST use id-sha512).
            val signerInfoGen: SignerInfoGenerator =
                SignerInfoGeneratorBuilder(BcDigestCalculatorProvider())
                    .setContentDigest(mapping.digestAlgorithmIdentifier())
                    .setSignedAttributeGenerator(signedAttrGen)
                    .build(contentSigner, X509CertificateHolder(signerCert.encoded))

            val cmsGenerator = CMSSignedDataGenerator()
            cmsGenerator.addSignerInfoGenerator(signerInfoGen)
            cmsGenerator.addCertificates(JcaCertStore(chain))
            val content = CMSProcessableByteArray(request.payload)
            val unsignedCms = cmsGenerator.generate(content, !request.detached)
            val toBeSigned = contentSigner.capturedSigningInput()
            val signatureValue =
                when (val r = kms.sign(request.keyId, toBeSigned, mapping.algorithm)) {
                    is SignResult.Success -> r.signature
                    else -> throw CadesSignerException("KMS sign failed: $r")
                }
            val cms = withSignatureValue(unsignedCms, signatureValue, if (request.detached) content else null)

            val finalCms =
                if (request.profile == CadesProfile.B_T) {
                    val tsaConfig =
                        request.tsaConfig
                            ?: throw CadesSignerException("tsaConfig is required for CadesProfile.B_T")
                    val tsa = tsaClientFactory(tsaConfig)
                    addSignatureTimeStamp(cms, tsa)
                } else {
                    cms
                }

            CadesSignature(
                encoded = finalCms.encoded,
                detached = request.detached,
                profile = request.profile,
            )
        }

    // ---------------------------------------------------------------- sigTst (B-T)

    private suspend fun addSignatureTimeStamp(
        cms: CMSSignedData,
        tsa: TsaClient,
    ): CMSSignedData {
        val signerInfo = cms.signerInfos.signers.single()
        val signatureValue = signerInfo.signature
        val imprint = MessageDigest.getInstance("SHA-256").digest(signatureValue)
        val token =
            try {
                tsa.requestTimeStamp(imprint, TsaHashAlgorithm.SHA_256)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                throw CadesSignerException("TSA request failed: ${t.message}", t)
            }
        val tokenAsn1 = ASN1Primitive.fromByteArray(token.encoded)
        val sigTstAttr =
            Attribute(
                PKCSObjectIdentifiers.id_aa_signatureTimeStampToken,
                DERSet(tokenAsn1),
            )
        val unsignedAttrs = AttributeTable(sigTstAttr)
        val updatedSigner = SignerInformation.replaceUnsignedAttributes(signerInfo, unsignedAttrs)
        val updatedStore = SignerInformationStore(listOf(updatedSigner))
        return CMSSignedData.replaceSigners(cms, updatedStore)
    }

    // ---------------------------------------------------------------- helpers

    private fun decodeChain(chain: List<ByteArray>): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        return chain.map { factory.generateCertificate(ByteArrayInputStream(it)) as X509Certificate }
    }

    // ---------------------------------------------------------------- two-pass KMS signing

    /**
     * Replaces the (placeholder) signature value of the single SignerInfo in [cms] with
     * [signatureValue]. The signed attributes — and therefore the signing input — are unchanged.
     * For ECDSA keys the KMS returns a DER signature, which is exactly what CMS expects (unlike
     * JAdES, which wants raw R||S).
     */
    private fun withSignatureValue(
        cms: CMSSignedData,
        signatureValue: ByteArray,
        detachedContent: CMSProcessableByteArray?,
    ): CMSSignedData {
        val signedData = SignedData.getInstance(cms.toASN1Structure().content)
        val signerInfos = signedData.signerInfos
        if (signerInfos.size() != 1) {
            throw CadesSignerException("Internal: expected exactly one SignerInfo, found ${signerInfos.size()}")
        }
        val original = SignerInfo.getInstance(signerInfos.getObjectAt(0))
        val signed =
            SignerInfo(
                original.sid,
                original.digestAlgorithm,
                original.authenticatedAttributes,
                original.digestEncryptionAlgorithm,
                DEROctetString(signatureValue),
                original.unauthenticatedAttributes,
            )
        val rebuilt =
            SignedData(
                signedData.digestAlgorithms,
                signedData.encapContentInfo,
                signedData.certificates,
                signedData.crLs,
                DERSet(signed),
            )
        val contentInfo = ContentInfo(CMSObjectIdentifiers.signedData, rebuilt)
        return if (detachedContent != null) CMSSignedData(detachedContent, contentInfo) else CMSSignedData(contentInfo)
    }

    /**
     * A Bouncy Castle [ContentSigner] that only records the signing input BC writes to it (the
     * DER-encoded `SignedAttributes`) and returns an empty placeholder signature. The real
     * signature is produced afterwards by the suspending KMS call — see [withSignatureValue].
     */
    private class CapturingContentSigner(
        private val sigAlgId: AlgorithmIdentifier,
    ) : ContentSigner {
        private val buffer = ByteArrayOutputStream()
        private var captured: ByteArray? = null

        override fun getAlgorithmIdentifier(): AlgorithmIdentifier = sigAlgId

        override fun getOutputStream(): OutputStream = buffer

        override fun getSignature(): ByteArray {
            captured = buffer.toByteArray()
            return ByteArray(0)
        }

        fun capturedSigningInput(): ByteArray =
            captured ?: throw CadesSignerException("Internal: Bouncy Castle did not request a signature")
    }

    // ---------------------------------------------------------------- signed-attributes

    /**
     * Subclasses [DefaultSignedAttributeTableGenerator] so we inherit its (raw `Map`) signature
     * exactly. The BC base class generates the mandatory `content-type` + `message-digest`
     * attributes; we layer the two CAdES baseline signed attributes on top:
     *   - `signing-time` (RFC 5652 §11.3)
     *   - `signing-certificate-v2` (RFC 5035 §3 / ETSI EN 319 122-1 §5.2.2.3)
     */
    private class CadesSignedAttributeTableGenerator(
        private val signingTimeMillis: Long,
        private val signerCert: X509Certificate,
    ) : DefaultSignedAttributeTableGenerator() {
        override fun createStandardAttributeTable(parameters: MutableMap<*, *>?): Hashtable<*, *> {
            // Java raw Hashtable comes back here; we re-cast to a usable mutable map and add the
            // two CAdES baseline attributes.
            @Suppress("UNCHECKED_CAST")
            val base = super.createStandardAttributeTable(parameters) as Hashtable<Any, Any>

            // signing-time (RFC 5652 §11.3) — the BC base class would default this to "now";
            // overwrite with the caller-supplied instant so the wire signature carries the
            // claimed signing time the request asked for.
            base[CMSAttributes.signingTime] =
                Attribute(
                    CMSAttributes.signingTime,
                    DERSet(Time(java.util.Date(signingTimeMillis))),
                )

            // signing-certificate-v2 (RFC 5035 §3 / ETSI EN 319 122-1 §5.2.2.3)
            val digest = MessageDigest.getInstance("SHA-256").digest(signerCert.encoded)
            val essIdV2 = ESSCertIDv2(digest)
            val scV2 = SigningCertificateV2(arrayOf(essIdV2))
            base[PKCSObjectIdentifiers.id_aa_signingCertificateV2] =
                Attribute(
                    PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                    DERSet(scV2),
                )

            return base
        }
    }
}
