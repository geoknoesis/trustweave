package org.trustweave.signatures.xades

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.trustweave.kms.KeyManagementService
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

/**
 * Builds an XAdES enveloped signature.
 *
 * # MVP scope
 *
 * - **B-B profile only** — no `SignatureTimeStamp` (B-T), no `CompleteCertificateRefs` (B-LT),
 *   no archival time-stamps (B-LTA).
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
class XadesSignerException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Default [XadesSigner] implementation using the JDK's `javax.xml.crypto.dsig` package.
 *
 * @param kms        KMS reference. Retained for parity with the JAdES / CAdES signers; not used by
 *                   the MVP scaffold because JDK XMLDSig demands a [PrivateKey]. See the
 *                   "KMS interaction note" on [XadesSigner].
 * @param privateKey The actual private key used for signing. **Scaffold-only** — production
 *                   callers will eventually pass a KMS-backed JCE [PrivateKey] handle.
 */
class DefaultXadesSigner(
    @Suppress("unused") private val kms: KeyManagementService,
    private val privateKey: PrivateKey,
) : XadesSigner {

    override suspend fun sign(request: XadesSigningRequest): XadesSignature = withContext(Dispatchers.IO) {
        val chain = decodeChain(request.signerCertificateChain)
        val signerCert = chain.first()
        val signingTime = request.signingTime ?: Clock.System.now()
        val signatureMethodUri = signatureMethodForKey(signerCert)
        val digestMethodUri = DigestMethod.SHA256

        val factory = XMLSignatureFactory.getInstance("DOM")
        val keyInfoFactory = factory.keyInfoFactory

        val document: Document = request.document
        val root = document.documentElement
            ?: throw XadesSignerException("source document has no root element")

        // XAdES QualifyingProperties — minimal SignedProperties carrying SigningTime and
        // SigningCertificateV2 (ETSI EN 319 132-1 §5.2). Built by hand because the JDK XMLDSig
        // API does not understand XAdES namespaces.
        val signatureId = "xades-sig-${java.util.UUID.randomUUID()}"
        val signedPropertiesId = "$signatureId-signedprops"
        val qualifyingProperties = buildQualifyingProperties(
            document = document,
            signatureId = signatureId,
            signedPropertiesId = signedPropertiesId,
            signingTime = signingTime,
            signerCert = signerCert,
        )
        val signedPropertiesElement = firstElementChild(qualifyingProperties)
            ?: throw XadesSignerException("Internal: QualifyingProperties has no element children")

        // Two references: one over the document root (enveloped) and one over the
        // SignedProperties block (XAdES baseline §5.2.1).
        val envelopedTransform: Transform = factory.newTransform(
            Transform.ENVELOPED,
            null as TransformParameterSpec?,
        )
        val rootReference: Reference = factory.newReference(
            "",
            factory.newDigestMethod(digestMethodUri, null),
            listOf(envelopedTransform),
            null,
            null,
        )
        val signedPropertiesReference: Reference = factory.newReference(
            "#$signedPropertiesId",
            factory.newDigestMethod(digestMethodUri, null),
            null,
            "http://uri.etsi.org/01903#SignedProperties",
            null,
        )

        val signedInfo: SignedInfo = factory.newSignedInfo(
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
        val xadesObject: XMLObject = factory.newXMLObject(
            listOf(DOMStructure(qualifyingProperties)),
            null,
            null,
            null,
        )

        val xmlSignature = factory.newXMLSignature(
            signedInfo,
            keyInfo,
            listOf(xadesObject),
            signatureId,
            null,
        )

        val signContext = DOMSignContext(privateKey, root)
        // Tell JDK XMLDSig that the SignedProperties element's "Id" attribute is the XML ID it
        // can resolve "#signedPropertiesId" against during reference resolution.
        signContext.setIdAttributeNS(signedPropertiesElement, null, "Id")
        try {
            xmlSignature.sign(signContext)
        } catch (t: Throwable) {
            throw XadesSignerException("XML-DSig signing failed: ${t.message}", t)
        }

        XadesSignature(document = document, profile = XadesProfile.B_B)
    }

    // ---------------------------------------------------------------- helpers

    private fun decodeChain(chain: List<ByteArray>): List<X509Certificate> {
        val cf = CertificateFactory.getInstance("X.509")
        return chain.map { cf.generateCertificate(ByteArrayInputStream(it)) as X509Certificate }
    }

    /**
     * Map signer-cert public-key algorithm to the XML-DSig SignatureMethod URI.
     *
     * TODO(Ed25519): the W3C URI for Ed25519 XMLDSig is
     *   `http://www.w3.org/2021/04/xmldsig-more#eddsa-ed25519` but JDK 21's default
     *   XMLDSig DOM provider (`com.sun.org.apache.xml.internal.security`) does not register
     *   that algorithm. Wiring Ed25519 requires either Apache Santuario or a custom
     *   provider; deferred to the next milestone.
     */
    private fun signatureMethodForKey(signerCert: X509Certificate): String {
        val keyAlg = signerCert.publicKey.algorithm
        return when (keyAlg) {
            "EC", "ECDSA" -> "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"
            "RSA" -> SignatureMethod.RSA_SHA256
            else -> throw XadesSignerException(
                "XAdES MVP scaffold supports EC / RSA only (got '$keyAlg'). " +
                    "Ed25519 + JDK XMLDSig: see TODO in XadesSigner.",
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
        digestValue.textContent = Base64.getEncoder().encodeToString(
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

// TODO(B-T): wire RFC 3161 TSA into XAdES SignatureTimeStamp element (ETSI EN 319 132-1 §5.4.1).
// TODO(detached): support detached XAdES — the SignedInfo carries a Reference to an external
//                 URI rather than to the enclosing document.
// TODO(enveloping): support enveloping XAdES — the signed payload is wrapped inside <ds:Object>
//                   and the SignedInfo references it by id.
