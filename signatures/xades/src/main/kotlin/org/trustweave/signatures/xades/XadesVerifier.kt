package org.trustweave.signatures.xades

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.xades.XadesValidationResult.Invalid
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.xml.crypto.AlgorithmMethod
import javax.xml.crypto.KeySelector
import javax.xml.crypto.KeySelectorException
import javax.xml.crypto.KeySelectorResult
import javax.xml.crypto.XMLCryptoContext
import javax.xml.crypto.dsig.XMLSignature
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.crypto.dsig.keyinfo.KeyInfo
import javax.xml.crypto.dsig.keyinfo.X509Data

/**
 * Verifier for XAdES B-B signatures (MVP scope).
 *
 * Only enveloped signatures are validated; detached and enveloping forms are flagged with `TODO`
 * markers at the bottom of this file.
 */
interface XadesVerifier {
    /**
     * Verify [document] (a parsed XML DOM containing a `<ds:Signature>` element).
     */
    suspend fun verify(document: Document, options: XadesVerificationOptions): XadesValidationResult
}

/** Default [XadesVerifier] implementation using the JDK's `javax.xml.crypto.dsig` package. */
class DefaultXadesVerifier : XadesVerifier {

    override suspend fun verify(
        document: Document,
        options: XadesVerificationOptions,
    ): XadesValidationResult = withContext(Dispatchers.IO) {
        // 1. Locate <ds:Signature>.
        val signatureNodes = document.getElementsByTagNameNS(
            "http://www.w3.org/2000/09/xmldsig#",
            "Signature",
        )
        if (signatureNodes.length == 0) {
            return@withContext Invalid.Malformed("document contains no <ds:Signature> element")
        }
        val signatureElement = signatureNodes.item(0) as Element

        // 2. Mark the SignedProperties Id attribute so XMLDSig can resolve "#…" references during
        //    validation. The producer placed an Id on every <xades:SignedProperties> element.
        val xadesNs = "http://uri.etsi.org/01903/v1.3.2#"
        val signedPropertiesNodes = document.getElementsByTagNameNS(xadesNs, "SignedProperties")
        for (i in 0 until signedPropertiesNodes.length) {
            val el = signedPropertiesNodes.item(i) as Element
            if (el.hasAttribute("Id")) {
                el.setIdAttribute("Id", true)
            }
        }

        // 3. Extract the signer certificate from <ds:X509Certificate>.
        val signerCert = extractSignerCert(signatureElement)
            ?: return@withContext Invalid.Malformed("<ds:KeyInfo>/<ds:X509Data> did not contain a signer cert")

        // 4. Validate XML-DSig.
        val context = DOMValidateContext(X509KeySelector(), signatureElement)
        val factory = XMLSignatureFactory.getInstance("DOM")
        val xmlSig: XMLSignature = try {
            factory.unmarshalXMLSignature(context)
        } catch (t: Throwable) {
            return@withContext Invalid.Malformed("could not parse <ds:Signature>: ${t.message}")
        }
        val signatureValid = try {
            xmlSig.validate(context)
        } catch (t: Throwable) {
            return@withContext Invalid.BadSignature("validation threw: ${t.message}")
        }
        if (!signatureValid) {
            return@withContext Invalid.BadSignature("XML-DSig validation failed")
        }

        // 5. Extract XAdES SigningTime.
        val signingTime = extractSigningTime(signatureElement)

        // 6. Cert validity at signing time.
        if (!options.allowExpiredCertificateAtSigningTime && signingTime != null) {
            val notAfter = signerCert.notAfter.toInstant().toKotlinInstant()
            val notBefore = signerCert.notBefore.toInstant().toKotlinInstant()
            if (signingTime > notAfter) return@withContext Invalid.CertificateExpired(notAfter)
            if (signingTime < notBefore) return@withContext Invalid.CertificateExpired(notAfter)
        }

        // 7. Trust anchor resolution.
        val otherCerts = extractCertChain(signatureElement).filter { it != signerCert }
        val trust = options.trustAnchorResolver.resolve(signerCert, otherCerts)
        if (trust is TrustAnchorMatch.NotTrusted) {
            return@withContext Invalid.UntrustedSigner(signerCert)
        }

        // 8. Profile check — MVP supports only B-B.
        if (options.requiredProfile != XadesProfile.B_B) {
            return@withContext Invalid.WrongProfile(
                found = XadesProfile.B_B,
                required = options.requiredProfile,
            )
        }

        XadesValidationResult.Valid(
            signerCert = signerCert,
            trust = trust,
            signingTime = signingTime,
            profile = XadesProfile.B_B,
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun extractSignerCert(signatureElement: Element): X509Certificate? =
        extractCertChain(signatureElement).firstOrNull()

    private fun extractCertChain(signatureElement: Element): List<X509Certificate> {
        val cf = CertificateFactory.getInstance("X.509")
        val certNodes = signatureElement.getElementsByTagNameNS(
            "http://www.w3.org/2000/09/xmldsig#",
            "X509Certificate",
        )
        val result = mutableListOf<X509Certificate>()
        for (i in 0 until certNodes.length) {
            val text = certNodes.item(i).textContent.trim()
            val der = java.util.Base64.getMimeDecoder().decode(text)
            result.add(cf.generateCertificate(ByteArrayInputStream(der)) as X509Certificate)
        }
        return result
    }

    private fun extractSigningTime(signatureElement: Element): Instant? {
        val nodes = signatureElement.getElementsByTagNameNS(
            "http://uri.etsi.org/01903/v1.3.2#",
            "SigningTime",
        )
        if (nodes.length == 0) return null
        val text = nodes.item(0).textContent.trim()
        return try {
            Instant.parse(text)
        } catch (_: Throwable) {
            null
        }
    }

    /** JDK XMLDSig [KeySelector] that pulls the public key out of the first `<ds:X509Certificate>`. */
    private class X509KeySelector : KeySelector() {
        override fun select(
            keyInfo: KeyInfo,
            purpose: Purpose,
            method: AlgorithmMethod,
            context: XMLCryptoContext,
        ): KeySelectorResult {
            for (info in keyInfo.content) {
                if (info is X509Data) {
                    for (entry in info.content) {
                        if (entry is X509Certificate) {
                            return KeySelectorResult { entry.publicKey }
                        }
                    }
                }
            }
            throw KeySelectorException("no X509Certificate present in KeyInfo")
        }
    }
}

// TODO(B-T): when XAdES B-T ships, validate the SignatureTimeStamp element against the embedded
//            RFC 3161 TimeStampToken's messageImprint and genTime, mirroring CadesVerifier.
// TODO(detached): support detached XAdES — verify that external URIs resolve.
// TODO(enveloping): support enveloping XAdES — verify the wrapped <ds:Object> reference.
