package org.trustweave.signatures.xades

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.trustweave.kms.KeyManagementService
import org.trustweave.signatures.tsa.BouncyCastleTsaClient
import org.trustweave.signatures.tsa.TsaClient
import org.trustweave.signatures.tsa.TsaConfig
import org.trustweave.signatures.tsa.TsaHashAlgorithm
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.crypto.dom.DOMStructure
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.crypto.dsig.DigestMethod
import javax.xml.crypto.dsig.Reference
import javax.xml.crypto.dsig.SignatureMethod
import javax.xml.crypto.dsig.SignedInfo
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLObject
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMSignContext
import javax.xml.crypto.dsig.keyinfo.KeyInfo
import javax.xml.crypto.dsig.keyinfo.X509Data
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.spec.TransformParameterSpec
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Builds an XAdES enveloped signature.
 *
 * # MVP scope
 *
 * - **B-B, B-T, B-LT and B-LTA** — B-T adds an RFC 3161 `SignatureTimeStamp` over the signature value; B-LT
 *   also embeds `CertificateValues` and `RevocationValues` from the request's validation data; B-LTA adds an
 *   XAdES 1.4.1 `ArchiveTimeStamp` over the signature, its signed data and all the unsigned properties above.
 * - **Enveloped signature only** — the produced `<ds:Signature>` is appended inside the supplied
 *   document root. Detached and enveloping forms are NOT implemented; see the `TODO` markers at
 *   the bottom of this file.
 *
 * # KMS interaction note
 *
 * The JDK XMLDSig API ([XMLSignatureFactory] / [DOMSignContext]) requires direct access to a
 * [PrivateKey] instance. TrustWeave's [KeyManagementService] does not expose private-key material —
 * it only exposes a `sign(keyId, bytes, alg)` operation. To bridge the two, the MVP signer accepts
 * an explicit [PrivateKey] override on construction; production deployments that need true HSM
 * isolation must wire a custom XMLDSig context whose `Signature` engine delegates to the KMS.
 * **TODO:** ship that bridge as `kms:plugins:xmldsig-bridge` once a real PAdES use case drives it;
 * tracked in `docs/architecture/eidas-qes-design.md` §13.
 */
interface XadesSigner {
    /**
     * Sign [request].
     *
     * @throws XadesSignerException on any failure: malformed cert chain, unsupported algorithm,
     *         XML-DSig assembly failure.
     */
    suspend fun sign(request: XadesSigningRequest): XadesSignature
}

/** Thrown by [DefaultXadesSigner] on unrecoverable failures during signing. */
class XadesSignerException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Default [XadesSigner] implementation using the JDK's `javax.xml.crypto.dsig` package.
 *
 * @param kms        KMS reference. Retained for parity with the JAdES / CAdES signers; not used by
 *                   the MVP scaffold because JDK XMLDSig demands a [PrivateKey]. See the
 *                   "KMS interaction note" on [XadesSigner].
 * @param privateKey The actual private key used for signing. **Scaffold-only** — production
 *                   callers will eventually pass a KMS-backed JCE [PrivateKey] handle.
 * @param tsaClientFactory Builds the RFC 3161 client for B-T / B-LT / B-LTA; defaults to [BouncyCastleTsaClient].
 */
class DefaultXadesSigner(
    @Suppress("unused") private val kms: KeyManagementService,
    private val privateKey: PrivateKey,
    private val tsaClientFactory: (TsaConfig) -> TsaClient,
) : XadesSigner {
    /** Signer without a time-stamp authority: B-B only (B-T, B-LT and B-LTA need [tsaClientFactory]). */
    constructor(kms: KeyManagementService, privateKey: PrivateKey) :
        this(kms, privateKey, ::BouncyCastleTsaClient)

    override suspend fun sign(request: XadesSigningRequest): XadesSignature =
        withContext(Dispatchers.IO) {
            val chain = decodeChain(request.signerCertificateChain)
            val signerCert = chain.first()
            val signingTime = request.signingTime ?: Clock.System.now()
            val signatureMethodUri = signatureMethodForKey(signerCert)
            val digestMethodUri = DigestMethod.SHA256

            val factory = XMLSignatureFactory.getInstance("DOM")
            val keyInfoFactory = factory.keyInfoFactory

            val document: Document = request.document
            val root =
                document.documentElement
                    ?: throw XadesSignerException("source document has no root element")

            // XAdES QualifyingProperties — minimal SignedProperties carrying SigningTime and
            // SigningCertificateV2 (ETSI EN 319 132-1 §5.2). Built by hand because the JDK XMLDSig
            // API does not understand XAdES namespaces.
            val signatureId = "xades-sig-${java.util.UUID.randomUUID()}"
            val signedPropertiesId = "$signatureId-signedprops"
            val qualifyingProperties =
                buildQualifyingProperties(
                    document = document,
                    signatureId = signatureId,
                    signedPropertiesId = signedPropertiesId,
                    signingTime = signingTime,
                    signerCert = signerCert,
                )
            val signedPropertiesElement =
                firstElementChild(qualifyingProperties)
                    ?: throw XadesSignerException("Internal: QualifyingProperties has no element children")

            // Two references: one over the document root (enveloped) and one over the
            // SignedProperties block (XAdES baseline §5.2.1).
            val envelopedTransform: Transform =
                factory.newTransform(
                    Transform.ENVELOPED,
                    null as TransformParameterSpec?,
                )
            val rootReference: Reference =
                factory.newReference(
                    "",
                    factory.newDigestMethod(digestMethodUri, null),
                    listOf(envelopedTransform),
                    null,
                    null,
                )
            val signedPropertiesReference: Reference =
                factory.newReference(
                    "#$signedPropertiesId",
                    factory.newDigestMethod(digestMethodUri, null),
                    null,
                    "http://uri.etsi.org/01903#SignedProperties",
                    null,
                )

            val signedInfo: SignedInfo =
                factory.newSignedInfo(
                    factory.newCanonicalizationMethod(
                        CanonicalizationMethod.INCLUSIVE,
                        null as C14NMethodParameterSpec?,
                    ),
                    factory.newSignatureMethod(signatureMethodUri, null),
                    listOf(rootReference, signedPropertiesReference),
                )

            // KeyInfo carries the signer X.509 chain.
            val x509Data: X509Data = keyInfoFactory.newX509Data(chain)
            val keyInfo: KeyInfo = keyInfoFactory.newKeyInfo(listOf(x509Data))

            // Wrap the XAdES QualifyingProperties as an XMLObject so JDK XMLDSig serialises it as a
            // child of <ds:Signature> alongside <ds:Object>.
            val xadesObject: XMLObject =
                factory.newXMLObject(
                    listOf(DOMStructure(qualifyingProperties)),
                    null,
                    null,
                    null,
                )

            val xmlSignature =
                factory.newXMLSignature(
                    signedInfo,
                    keyInfo,
                    listOf(xadesObject),
                    signatureId,
                    null,
                )

            val signContext = DOMSignContext(privateKey, root)
            if (request.profile.atLeast(XadesProfile.B_LTA)) {
                // The archive time-stamp covers the dereferenced reference octets; keep them from signing.
                signContext.setProperty("javax.xml.crypto.dsig.cacheReference", true)
            }
            // Tell JDK XMLDSig that the SignedProperties element's "Id" attribute is the XML ID it
            // can resolve "#signedPropertiesId" against during reference resolution.
            signContext.setIdAttributeNS(signedPropertiesElement, null, "Id")
            try {
                xmlSignature.sign(signContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                throw XadesSignerException("XML-DSig signing failed: ${t.message}", t)
            }

            if (request.profile.atLeast(XadesProfile.B_T)) {
                val referenceOctets =
                    if (request.profile.atLeast(XadesProfile.B_LTA)) {
                        XadesArchiveTimestamps.referenceOctets(xmlSignature)
                            ?: throw XadesSignerException("Internal: signed reference data was not retained")
                    } else {
                        emptyList()
                    }
                addUnsignedProperties(document, signatureId, request, referenceOctets)
            }

            XadesSignature(document = document, profile = request.profile)
        }

    /**
     * Appends `<xades:UnsignedProperties>` to the signature just produced: the RFC 3161
     * `SignatureTimeStamp` (B-T), for B-LT `CertificateValues` and `RevocationValues`, and for B-LTA an
     * `ArchiveTimeStamp` over everything before it. These are unsigned properties, so adding them does not disturb the signature value.
     */
    private suspend fun addUnsignedProperties(
        document: Document,
        signatureId: String,
        request: XadesSigningRequest,
        referenceOctets: List<ByteArray>,
    ) {
        val xades = "http://uri.etsi.org/01903/v1.3.2#"
        val ds = "http://www.w3.org/2000/09/xmldsig#"
        val signature =
            (0 until document.getElementsByTagNameNS(ds, "Signature").length)
                .map { document.getElementsByTagNameNS(ds, "Signature").item(it) as Element }
                .firstOrNull { it.getAttribute("Id") == signatureId }
                ?: throw XadesSignerException("Internal: produced <ds:Signature> '$signatureId' not found")
        val signatureValue =
            signature.getElementsByTagNameNS(ds, "SignatureValue").item(0) as? Element
                ?: throw XadesSignerException("Internal: produced signature has no <ds:SignatureValue>")
        val qualifyingProperties =
            signature.getElementsByTagNameNS(xades, "QualifyingProperties").item(0) as? Element
                ?: throw XadesSignerException("Internal: produced signature has no <xades:QualifyingProperties>")

        val imprint = MessageDigest.getInstance("SHA-256").digest(XadesTimestamps.imprintInput(signatureValue))
        val tsa = tsaClientFactory(request.tsaConfig!!)
        val token =
            try {
                tsa.requestTimeStamp(imprint, TsaHashAlgorithm.SHA_256)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                throw XadesSignerException("TSA request failed: ${t.message}", t)
            }

        val unsignedProperties = document.createElementNS(xades, "xades:UnsignedProperties")
        val unsignedSignatureProperties = document.createElementNS(xades, "xades:UnsignedSignatureProperties")
        unsignedProperties.appendChild(unsignedSignatureProperties)

        val signatureTimeStamp = document.createElementNS(xades, "xades:SignatureTimeStamp")
        signatureTimeStamp.appendChild(textElement(document, xades, "xades:EncapsulatedTimeStamp", token.encoded))
        unsignedSignatureProperties.appendChild(signatureTimeStamp)

        if (request.profile.atLeast(XadesProfile.B_LT)) {
            val data = request.validationData!!
            if (data.certificates.isNotEmpty()) {
                val certificateValues = document.createElementNS(xades, "xades:CertificateValues")
                data.certificates.forEach {
                    certificateValues.appendChild(textElement(document, xades, "xades:EncapsulatedX509Certificate", it))
                }
                unsignedSignatureProperties.appendChild(certificateValues)
            }
            val revocationValues = document.createElementNS(xades, "xades:RevocationValues")
            if (data.revocation.crls.isNotEmpty()) {
                val crlValues = document.createElementNS(xades, "xades:CRLValues")
                data.revocation.crls.forEach {
                    crlValues.appendChild(textElement(document, xades, "xades:EncapsulatedCRLValue", it))
                }
                revocationValues.appendChild(crlValues)
            }
            if (data.revocation.ocspResponses.isNotEmpty()) {
                val ocspValues = document.createElementNS(xades, "xades:OCSPValues")
                data.revocation.ocspResponses.forEach {
                    ocspValues.appendChild(textElement(document, xades, "xades:EncapsulatedOCSPValue", it))
                }
                revocationValues.appendChild(ocspValues)
            }
            unsignedSignatureProperties.appendChild(revocationValues)
        }
        qualifyingProperties.appendChild(unsignedProperties)

        if (request.profile.atLeast(XadesProfile.B_LTA)) {
            val c14n = CanonicalizationMethod.INCLUSIVE
            val archiveImprint =
                try {
                    MessageDigest.getInstance("SHA-256").digest(
                        XadesArchiveTimestamps.imprintInput(
                            signature,
                            referenceOctets,
                            XadesArchiveTimestamps.unsignedProperties(qualifyingProperties),
                            c14n,
                        ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Exception) {
                    throw XadesSignerException("could not canonicalise the signature for the archive time-stamp: ${t.message}", t)
                }
            val archiveToken =
                try {
                    tsa.requestTimeStamp(archiveImprint, TsaHashAlgorithm.SHA_256)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    throw XadesSignerException("TSA request for the archive time-stamp failed: ${t.message}", t)
                }
            val xades141 = XadesArchiveTimestamps.XADES141_NS
            val archiveTimeStamp = document.createElementNS(xades141, "xades141:ArchiveTimeStamp")
            archiveTimeStamp.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xades141", xades141)
            archiveTimeStamp.appendChild(
                document.createElementNS(ds, "ds:CanonicalizationMethod").apply { setAttribute("Algorithm", c14n) },
            )
            archiveTimeStamp.appendChild(textElement(document, xades, "xades:EncapsulatedTimeStamp", archiveToken.encoded))
            unsignedSignatureProperties.appendChild(archiveTimeStamp)
        }
    }

    private fun textElement(
        document: Document,
        namespace: String,
        qualifiedName: String,
        bytes: ByteArray,
    ): Element =
        document.createElementNS(namespace, qualifiedName).apply {
            textContent = Base64.getEncoder().encodeToString(bytes)
        }

    // ---------------------------------------------------------------- helpers

    private fun decodeChain(chain: List<ByteArray>): List<X509Certificate> {
        val cf = CertificateFactory.getInstance("X.509")
        return chain.map { cf.generateCertificate(ByteArrayInputStream(it)) as X509Certificate }
    }

    /**
     * Map signer-cert public-key algorithm to the XML-DSig SignatureMethod URI.
     *
     * **Ed25519 is not supported, and fails explicitly.** RFC 9231 defines
     * `http://www.w3.org/2021/04/xmldsig-more#eddsa-ed25519`, but the JDK's built-in XML-DSig
     * provider (Apache Santuario fork, JDK 21) does not register it, so a signature could not be
     * produced or verified. Signing with an Ed25519 certificate throws [XadesSignerException]
     * rather than silently falling back to another algorithm. Supporting it needs a standalone
     * Santuario (xmlsec 3.x+) provider; use an EC (P-256) or RSA signing certificate.
     */
    private fun signatureMethodForKey(signerCert: X509Certificate): String {
        val keyAlg = signerCert.publicKey.algorithm
        return when (keyAlg) {
            "EC", "ECDSA" -> "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"
            "RSA" -> SignatureMethod.RSA_SHA256
            else -> throw XadesSignerException(
                "XAdES signing supports EC and RSA certificates only (got '$keyAlg'). " +
                    "Ed25519 (RFC 9231) is not available in the JDK XML-DSig provider; " +
                    "use an EC P-256 or RSA signing certificate.",
            )
        }
    }

    private fun buildQualifyingProperties(
        document: Document,
        signatureId: String,
        signedPropertiesId: String,
        signingTime: Instant,
        signerCert: X509Certificate,
    ): Element {
        val xades = "http://uri.etsi.org/01903/v1.3.2#"
        val ds = "http://www.w3.org/2000/09/xmldsig#"

        val qualifyingProperties = document.createElementNS(xades, "xades:QualifyingProperties")
        qualifyingProperties.setAttribute("Target", "#$signatureId")
        // Declare the prefixes explicitly: a DOM built with createElementNS carries no xmlns attributes, so the
        // canonical form the signature (and any time-stamp) is computed over would differ from the one of the
        // serialised and re-parsed document, which the serialiser adds them to.
        qualifyingProperties.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xades", xades)
        qualifyingProperties.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:ds", ds)

        val signedProperties = document.createElementNS(xades, "xades:SignedProperties")
        signedProperties.setAttribute("Id", signedPropertiesId)
        qualifyingProperties.appendChild(signedProperties)

        val signedSignatureProperties = document.createElementNS(xades, "xades:SignedSignatureProperties")
        signedProperties.appendChild(signedSignatureProperties)

        val signingTimeEl = document.createElementNS(xades, "xades:SigningTime")
        signingTimeEl.textContent = signingTime.toString()
        signedSignatureProperties.appendChild(signingTimeEl)

        // SigningCertificateV2 — XAdES §5.2.2, mandatory in B-B baseline.
        val signingCertV2 = document.createElementNS(xades, "xades:SigningCertificateV2")
        val cert = document.createElementNS(xades, "xades:Cert")
        val certDigest = document.createElementNS(xades, "xades:CertDigest")
        val digestMethod = document.createElementNS(ds, "ds:DigestMethod")
        digestMethod.setAttribute("Algorithm", "http://www.w3.org/2001/04/xmlenc#sha256")
        val digestValue = document.createElementNS(ds, "ds:DigestValue")
        digestValue.textContent =
            Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(signerCert.encoded),
            )
        certDigest.appendChild(digestMethod)
        certDigest.appendChild(digestValue)
        cert.appendChild(certDigest)
        signingCertV2.appendChild(cert)
        signedSignatureProperties.appendChild(signingCertV2)

        return qualifyingProperties
    }

    private fun firstElementChild(parent: Element): Element? {
        var c = parent.firstChild
        while (c != null) {
            if (c is Element) return c
            c = c.nextSibling
        }
        return null
    }
}

// TODO(detached): support detached XAdES — the SignedInfo carries a Reference to an external
//                 URI rather than to the enclosing document.
// TODO(enveloping): support enveloping XAdES — the signed payload is wrapped inside <ds:Object>
//                   and the SignedInfo references it by id.
