package org.trustweave.signatures.xades

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.IssuerSerial
import org.trustweave.signatures.revocation.CertificateRevocationEvaluator
import org.trustweave.signatures.revocation.RevocationPolicy
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.xades.XadesValidationResult.Invalid
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.security.auth.x500.X500Principal
import javax.xml.crypto.AlgorithmMethod
import javax.xml.crypto.KeySelector
import javax.xml.crypto.KeySelectorException
import javax.xml.crypto.KeySelectorResult
import javax.xml.crypto.XMLCryptoContext
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.crypto.dsig.Reference
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLSignature
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.crypto.dsig.keyinfo.KeyInfo
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Verifier for XAdES B-B signatures (MVP scope).
 *
 * Only enveloped signatures are validated; detached and enveloping forms are rejected as
 * [XadesValidationResult.Invalid.Malformed] (see the `TODO` markers at the bottom of this file).
 */
interface XadesVerifier {
    /**
     * Verify [document] (a parsed XML DOM containing a `<ds:Signature>` element).
     */
    suspend fun verify(
        document: Document,
        options: XadesVerificationOptions,
    ): XadesValidationResult
}

/**
 * Default [XadesVerifier] implementation using the JDK's `javax.xml.crypto.dsig` package.
 *
 * # Anti-wrapping rules (enforced around XML-DSig core validation)
 *
 * - The document must carry exactly one `<ds:Signature>`, and it must be enveloped (a descendant
 *   of the document element), never the document element itself.
 * - `Id` / `ID` / `id` attribute values must be unique across the whole document, so a forged
 *   element cannot shadow the one the signature really covers.
 * - `<ds:SignedInfo>` must contain exactly two references: `URI=""` with the enveloped-signature
 *   transform (covering the whole document) and a
 *   `Type="http://uri.etsi.org/01903#SignedProperties"` reference whose URI points at the
 *   `<xades:SignedProperties>` element *inside this signature*.
 * - The signer certificate is the `<ds:KeyInfo>` certificate whose digest (and issuer/serial, when
 *   present) matches the signed `SigningCertificateV2` / `SigningCertificate` property, and the
 *   signature is validated with *that* certificate's public key — never with whatever key the
 *   `KeyInfo` happens to list first.
 * - `SigningTime` is only the signer's CLAIM unless a trusted RFC 3161 `SignatureTimeStamp` backs
 *   it ([XadesValidationResult.Valid.signingTimeAuthenticated]); see
 *   [XadesVerificationOptions.requireSignatureTimestamp] and `timestampTrustAnchors`. A `QualifiedWithdrawn`
 *   trust result is accepted only for a time-stamped signature that predates the withdrawal
 *   (or when `allowWithdrawnTrustWithoutAuthenticatedTime` is set).
 * - The chain built from `<ds:KeyInfo>` is checked structurally (validity window, `basicConstraints`,
 *   `pathLenConstraint`, `keyUsage`). Revocation is evaluated only when
 *   [XadesVerificationOptions.revocationPolicy] asks for it: CRLs and OCSP responses (embedded
 *   `RevocationValues` plus caller-supplied evidence) are verified against the issuing CA, must be fresh
 *   for the signature, and the signer and each CA below the trust anchor must be shown not revoked.
 * - `SigningTime` is read only from the signed `SignedSignatureProperties`. A malformed value is
 *   [Invalid.Malformed]. When it is missing, the certificate validity window is checked against
 *   the CURRENT time (so a signature by a since-expired certificate is rejected, and the result's
 *   `signingTime` is `null`); set [XadesVerificationOptions.requireSigningTime] to reject such
 *   signatures outright instead.
 * - Only `<ds:KeyInfo>` certificates that chain to the signer (issuer DN == subject DN and a valid
 *   signature) are passed to the [org.trustweave.signatures.trustlists.TrustAnchorResolver]; unrelated
 *   certificates are ignored. The trust result is handled by an exhaustive `when`, so a new
 *   `TrustAnchorMatch` subtype cannot be accepted by default.
 */
class DefaultXadesVerifier : XadesVerifier {
    override suspend fun verify(
        document: Document,
        options: XadesVerificationOptions,
    ): XadesValidationResult =
        withContext(Dispatchers.IO) {
            // 1. Locate the single, enveloped <ds:Signature>.
            val signatureNodes = document.getElementsByTagNameNS(DS_NS, "Signature")
            if (signatureNodes.length == 0) {
                return@withContext Invalid.Malformed("document contains no <ds:Signature> element")
            }
            if (signatureNodes.length > 1) {
                return@withContext Invalid.Malformed(
                    "document contains ${signatureNodes.length} <ds:Signature> elements; exactly one is supported",
                )
            }
            val signatureElement = signatureNodes.item(0) as Element
            if (signatureElement.parentNode !is Element) {
                return@withContext Invalid.Malformed(
                    "<ds:Signature> is the document element; only enveloped signatures are supported",
                )
            }

            // 2. Reject duplicate Id attributes anywhere in the document.
            findDuplicateId(document.documentElement)?.let { dup ->
                return@withContext Invalid.Malformed("duplicate Id attribute value '$dup' in document")
            }

            // 3. Locate the SignedProperties that live inside *this* signature.
            val qualifyingProperties =
                childElements(signatureElement, DS_NS, "Object")
                    .flatMap { childElements(it, XADES_NS, "QualifyingProperties") }
            if (qualifyingProperties.size != 1) {
                return@withContext Invalid.Malformed(
                    "expected exactly one <xades:QualifyingProperties> inside <ds:Signature>, " +
                        "found ${qualifyingProperties.size}",
                )
            }
            val qp = qualifyingProperties.single()
            val signatureId = signatureElement.getAttribute("Id")
            val target = qp.getAttribute("Target")
            if (signatureId.isEmpty() || target != "#$signatureId") {
                return@withContext Invalid.Malformed(
                    "QualifyingProperties Target '$target' does not point at the enclosing signature '#$signatureId'",
                )
            }
            val signedProperties =
                childElements(qp, XADES_NS, "SignedProperties").singleOrNull()
                    ?: return@withContext Invalid.Malformed("expected exactly one <xades:SignedProperties>")
            val signedPropertiesId = signedProperties.getAttribute("Id")
            if (signedPropertiesId.isEmpty()) {
                return@withContext Invalid.Malformed("<xades:SignedProperties> has no Id attribute")
            }
            val ssp =
                childElements(signedProperties, XADES_NS, "SignedSignatureProperties").singleOrNull()
                    ?: return@withContext Invalid.Malformed("expected exactly one <xades:SignedSignatureProperties>")

            // 4. Signer certificate: the KeyInfo cert bound by the signed SigningCertificate(V2).
            val keyInfoCerts =
                try {
                    extractKeyInfoCerts(signatureElement)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    return@withContext Invalid.Malformed("<ds:KeyInfo> contains an invalid certificate: ${t.message}")
                }
            if (keyInfoCerts.isEmpty()) {
                return@withContext Invalid.Malformed("<ds:KeyInfo>/<ds:X509Data> did not contain a signer cert")
            }
            val signerCert =
                when (val binding = resolveSigningCertificate(ssp, keyInfoCerts)) {
                    is CertBinding.Bound -> binding.cert
                    is CertBinding.Failed -> return@withContext binding.result
                }

            // 5. Signing time — only from the signed SignedSignatureProperties.
            val signingTimeElements = childElements(ssp, XADES_NS, "SigningTime")
            if (signingTimeElements.size > 1) {
                return@withContext Invalid.Malformed("more than one <xades:SigningTime>")
            }
            val signingTimeText = signingTimeElements.singleOrNull()?.textContent?.trim()
            if (signingTimeText == null && options.requireSigningTime) {
                return@withContext Invalid.Malformed(
                    "<xades:SigningTime> is absent from the signed properties and requireSigningTime is set",
                )
            }
            val signingTime: Instant? =
                if (signingTimeText == null) {
                    null
                } else {
                    try {
                        Instant.parse(signingTimeText)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        return@withContext Invalid.Malformed("SigningTime is not a valid ISO 8601 instant: '$signingTimeText'")
                    }
                }

            // 6. Unmarshal and check the reference layout before validating.
            val context = DOMValidateContext(FixedKeySelector(signerCert), signatureElement)
            context.setProperty("org.jcp.xml.dsig.secureValidation", true)
            context.setIdAttributeNS(signedProperties, null, "Id")
            val factory = XMLSignatureFactory.getInstance("DOM")
            val xmlSig: XMLSignature =
                try {
                    factory.unmarshalXMLSignature(context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    return@withContext Invalid.Malformed("could not parse <ds:Signature>: ${t.message}")
                }
            checkReferences(xmlSig, signedPropertiesId)?.let { return@withContext it }

            // 7. Validate XML-DSig with the bound signer key.
            val signatureValid =
                try {
                    xmlSig.validate(context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    return@withContext Invalid.BadSignature("validation threw: ${t.message}")
                }
            if (!signatureValid) {
                return@withContext Invalid.BadSignature("XML-DSig validation failed")
            }

            // 8. Signature time-stamp (B-T). Without one, SigningTime is the signer's own claim.
            val timestampRequired =
                options.requireSignatureTimestamp || options.requiredProfile.atLeast(XadesProfile.B_T)
            val authenticatedTime: Instant? =
                when (
                    val ts =
                        XadesTimestamps.evaluate(qp, signatureElement, options.timestampTrustAnchors, C14N_ALGORITHMS)
                ) {
                    is XadesTimestamps.Outcome.Valid -> {
                        val skew = options.maxClockSkewSeconds * 1000
                        if (signingTime != null && signingTime.toEpochMilliseconds() > ts.genTime.toEpochMilliseconds() + skew) {
                            return@withContext Invalid.TimeStampInvalid(
                                "claimed SigningTime ($signingTime) is later than the time-stamp (${ts.genTime})",
                            )
                        }
                        ts.genTime
                    }
                    is XadesTimestamps.Outcome.Invalid -> return@withContext Invalid.TimeStampInvalid(ts.reason)
                    XadesTimestamps.Outcome.None ->
                        if (timestampRequired) {
                            return@withContext Invalid.TimeStampInvalid("signature carries no <xades:SignatureTimeStamp>")
                        } else {
                            null
                        }
                    XadesTimestamps.Outcome.NoAnchors ->
                        if (timestampRequired) {
                            return@withContext Invalid.TimeStampInvalid(
                                "a SignatureTimeStamp is present but no timestampTrustAnchors are configured to trust it",
                            )
                        } else {
                            null
                        }
                }
            val effectiveTime: Instant = authenticatedTime ?: signingTime ?: Clock.System.now()

            // 9. Cert validity at the (authenticated, else claimed, else current) time.
            if (!options.allowExpiredCertificateAtSigningTime) {
                val notAfter = signerCert.notAfter.toInstant().toKotlinInstant()
                val notBefore = signerCert.notBefore.toInstant().toKotlinInstant()
                if (effectiveTime > notAfter) return@withContext Invalid.CertificateExpired(notAfter)
                if (effectiveTime < notBefore) return@withContext Invalid.CertificateNotYetValid(notBefore)
            }

            // 10. Certificate chain checks. Only KeyInfo certificates that actually chain to the
            //     signer (issuer/subject linkage + signature) are considered; unrelated
            //     certificates an attacker appended to <ds:KeyInfo> are ignored.
            val chainCerts = chainToSigner(signerCert, keyInfoCerts)
            validateChain(signerCert, chainCerts, effectiveTime, options.allowExpiredCertificateAtSigningTime)
                ?.let { return@withContext it }

            // 11. Trust anchor resolution.
            val trust = options.trustAnchorResolver.resolve(signerCert, chainCerts, authenticatedTime)
            // Exhaustive on purpose: a future TrustAnchorMatch subtype must be classified here
            // (accepted or refused) before this compiles, so it can never pass by default.
            when (trust) {
                is TrustAnchorMatch.NotTrusted -> return@withContext Invalid.UntrustedSigner(signerCert)
                is TrustAnchorMatch.QualifiedActive -> Unit
                is TrustAnchorMatch.QualifiedWithdrawn -> {
                    // A withdrawn service only vouches for signatures made before it was withdrawn.
                    if (authenticatedTime != null) {
                        if (authenticatedTime >= trust.withdrawnAt) {
                            return@withContext Invalid.TrustWithdrawn(
                                signerCert,
                                trust.withdrawnAt,
                                "time-stamped at $authenticatedTime, not before the withdrawal at ${trust.withdrawnAt}",
                            )
                        }
                    } else {
                        if (!options.allowWithdrawnTrustWithoutAuthenticatedTime) {
                            return@withContext Invalid.TrustWithdrawn(
                                signerCert,
                                trust.withdrawnAt,
                                "the trust-list service was withdrawn and the signing time is not authenticated by a " +
                                    "time-stamp; set allowWithdrawnTrustWithoutAuthenticatedTime to accept",
                            )
                        }
                        if (signingTime != null && signingTime >= trust.withdrawnAt) {
                            return@withContext Invalid.TrustWithdrawn(
                                signerCert,
                                trust.withdrawnAt,
                                "claimed SigningTime $signingTime is not before the withdrawal at ${trust.withdrawnAt}",
                            )
                        }
                    }
                }
            }

            // 12. Revocation (CRL / OCSP). Requesting B-LT implies REQUIRED: a long-term signature is only
            //     as good as the validation data it proves.
            val revocationPolicy =
                if (options.requiredProfile == XadesProfile.B_LT && options.revocationPolicy == RevocationPolicy.NOT_CHECKED) {
                    RevocationPolicy.REQUIRED
                } else {
                    options.revocationPolicy
                }
            val embeddedEvidence = XadesRevocationValues.embedded(qp)
            val skewMillis = options.maxClockSkewSeconds * 1000
            var revocationChecked = false
            var embeddedCoversChain = false
            if (revocationPolicy != RevocationPolicy.NOT_CHECKED) {
                val now = Clock.System.now()
                val statuses =
                    CertificateRevocationEvaluator.evaluate(
                        signer = signerCert,
                        candidates = keyInfoCerts,
                        issuerCertificates = options.revocationIssuerCertificates,
                        evidence = options.revocationEvidence + embeddedEvidence,
                        authenticatedTime = authenticatedTime,
                        now = now,
                        skewMillis = skewMillis,
                    )
                statuses.filterIsInstance<CertificateRevocationEvaluator.Status.Revoked>().firstOrNull()?.let {
                    return@withContext Invalid.CertificateRevoked(it.cert, it.at, it.reason)
                }
                val unavailable = statuses.filterIsInstance<CertificateRevocationEvaluator.Status.Unavailable>()
                if (statuses.isEmpty() && revocationPolicy == RevocationPolicy.REQUIRED) {
                    return@withContext Invalid.RevocationUnavailable(
                        "no certificate below a trust anchor was available to check for revocation",
                    )
                }
                if (unavailable.isNotEmpty() && revocationPolicy == RevocationPolicy.REQUIRED) {
                    return@withContext Invalid.RevocationUnavailable(unavailable.joinToString("; ") { it.reason })
                }
                revocationChecked = unavailable.isEmpty() && statuses.isNotEmpty()
                // B-LT: the evidence carried inside the signature must, on its own, cover the chain.
                if (revocationChecked && authenticatedTime != null && !embeddedEvidence.isEmpty) {
                    val own =
                        CertificateRevocationEvaluator.evaluate(
                            signer = signerCert,
                            candidates = keyInfoCerts,
                            issuerCertificates = options.revocationIssuerCertificates,
                            evidence = embeddedEvidence,
                            authenticatedTime = authenticatedTime,
                            now = now,
                            skewMillis = skewMillis,
                        )
                    embeddedCoversChain = own.isNotEmpty() && own.all { it is CertificateRevocationEvaluator.Status.Good }
                }
            }

            val foundProfile =
                when {
                    authenticatedTime != null && embeddedCoversChain -> XadesProfile.B_LT
                    authenticatedTime != null -> XadesProfile.B_T
                    else -> XadesProfile.B_B
                }
            if (!foundProfile.atLeast(options.requiredProfile)) {
                return@withContext Invalid.WrongProfile(found = foundProfile, required = options.requiredProfile)
            }

            XadesValidationResult.Valid(
                signerCert = signerCert,
                trust = trust,
                signingTime = signingTime,
                profile = foundProfile,
                signingTimeAuthenticated = authenticatedTime != null,
                signatureTimeStamp = authenticatedTime,
                revocationChecked = revocationChecked,
            )
        }

    /**
     * Structural chain checks (no revocation): the signer's `keyUsage` (when present) must allow
     * signing, and each issuer certificate must be inside its validity window, be a CA
     * (`basicConstraints`), respect `pathLenConstraint`, and (when `keyUsage` is present) allow
     * `keyCertSign`.
     */
    private fun validateChain(
        signer: X509Certificate,
        chain: List<X509Certificate>,
        at: Instant,
        allowExpired: Boolean,
    ): Invalid? {
        signer.keyUsage?.let { ku ->
            val signs = ku.getOrElse(KU_DIGITAL_SIGNATURE) { false } || ku.getOrElse(KU_NON_REPUDIATION) { false }
            if (!signs) {
                return Invalid.CertificateChainInvalid(
                    "signer certificate keyUsage permits neither digitalSignature nor nonRepudiation",
                )
            }
        }
        chain.forEachIndexed { index, ca ->
            val name = ca.subjectX500Principal.name
            if (!allowExpired) {
                if (at > ca.notAfter.toInstant().toKotlinInstant()) {
                    return Invalid.CertificateChainInvalid("CA certificate '$name' had expired at $at")
                }
                if (at < ca.notBefore.toInstant().toKotlinInstant()) {
                    return Invalid.CertificateChainInvalid("CA certificate '$name' was not yet valid at $at")
                }
            }
            // getBasicConstraints: -1 when the extension is absent or cA is false.
            val pathLen = ca.basicConstraints
            if (pathLen < 0) {
                return Invalid.CertificateChainInvalid("issuer certificate '$name' is not a CA (basicConstraints cA not set)")
            }
            // `index` CA certificates sit between this one and the signer.
            if (pathLen < index) {
                return Invalid.CertificateChainInvalid("CA certificate '$name' pathLenConstraint $pathLen is exceeded")
            }
            ca.keyUsage?.let { ku ->
                if (!ku.getOrElse(KU_KEY_CERT_SIGN) { false }) {
                    return Invalid.CertificateChainInvalid("CA certificate '$name' keyUsage lacks keyCertSign")
                }
            }
        }
        return null
    }

    // ---------------------------------------------------------------- helpers

    private sealed class CertBinding {
        class Bound(
            val cert: X509Certificate,
        ) : CertBinding()

        class Failed(
            val result: Invalid,
        ) : CertBinding()
    }

    /**
     * Bind the signer certificate: the first `<xades:Cert>` of the signed `SigningCertificateV2`
     * (or legacy `SigningCertificate`) must match exactly one `<ds:KeyInfo>` certificate by digest
     * and, when present, by issuer/serial.
     */
    private fun resolveSigningCertificate(
        ssp: Element,
        keyInfoCerts: List<X509Certificate>,
    ): CertBinding {
        val v2 = childElements(ssp, XADES_NS, "SigningCertificateV2")
        val v1 = childElements(ssp, XADES_NS, "SigningCertificate")
        if (v2.size + v1.size != 1) {
            return CertBinding.Failed(
                Invalid.Malformed("expected exactly one SigningCertificateV2/SigningCertificate signed property"),
            )
        }
        val isV2 = v2.isNotEmpty()
        val container = (v2 + v1).single()
        val certRef =
            childElements(container, XADES_NS, "Cert").firstOrNull()
                ?: return CertBinding.Failed(Invalid.Malformed("SigningCertificate property has no <xades:Cert>"))
        val certDigest =
            childElements(certRef, XADES_NS, "CertDigest").singleOrNull()
                ?: return CertBinding.Failed(Invalid.Malformed("<xades:Cert> has no <xades:CertDigest>"))
        val digestAlgUri =
            childElements(certDigest, DS_NS, "DigestMethod").singleOrNull()?.getAttribute("Algorithm")
                ?: return CertBinding.Failed(Invalid.Malformed("<xades:CertDigest> has no <ds:DigestMethod>"))
        val jcaDigest =
            DIGEST_ALGORITHMS[digestAlgUri]
                ?: return CertBinding.Failed(Invalid.Malformed("unsupported CertDigest algorithm '$digestAlgUri'"))
        val digestValueText =
            childElements(certDigest, DS_NS, "DigestValue").singleOrNull()?.textContent?.trim()
                ?: return CertBinding.Failed(Invalid.Malformed("<xades:CertDigest> has no <ds:DigestValue>"))
        val expectedDigest =
            try {
                Base64.getMimeDecoder().decode(digestValueText)
            } catch (_: IllegalArgumentException) {
                return CertBinding.Failed(Invalid.Malformed("CertDigest DigestValue is not base64"))
            }

        val matches =
            keyInfoCerts
                .filter {
                    MessageDigest.isEqual(MessageDigest.getInstance(jcaDigest).digest(it.encoded), expectedDigest)
                }.distinct()
        val signerCert =
            when (matches.size) {
                0 -> return CertBinding.Failed(
                    Invalid.BadSignature("SigningCertificate digest does not match any <ds:KeyInfo> certificate"),
                )
                1 -> matches.single()
                else -> return CertBinding.Failed(
                    Invalid.Malformed("SigningCertificate digest matches more than one <ds:KeyInfo> certificate"),
                )
            }

        val issuerSerialMatches =
            try {
                if (isV2) {
                    childElements(certRef, XADES_NS, "IssuerSerialV2")
                        .singleOrNull()
                        ?.let { issuerSerialV2Matches(it.textContent.trim(), signerCert) } ?: true
                } else {
                    childElements(certRef, XADES_NS, "IssuerSerial")
                        .singleOrNull()
                        ?.let { issuerSerialV1Matches(it, signerCert) } ?: true
                }
            } catch (t: Exception) {
                return CertBinding.Failed(
                    Invalid.Malformed("could not parse SigningCertificate issuer/serial: ${t.message}"),
                )
            }
        if (!issuerSerialMatches) {
            return CertBinding.Failed(
                Invalid.BadSignature("SigningCertificate issuer/serial does not match the signer certificate"),
            )
        }
        return CertBinding.Bound(signerCert)
    }

    /**
     * The `<ds:KeyInfo>` certificates that form a chain upwards from [signer]: each one is the
     * issuer of the previous (subject == issuer DN AND the previous certificate's signature verifies
     * under its key). Self-signed certificates end the walk; unrelated certificates are dropped.
     */
    private fun chainToSigner(
        signer: X509Certificate,
        candidates: List<X509Certificate>,
    ): List<X509Certificate> {
        val chain = mutableListOf<X509Certificate>()
        var current = signer
        val remaining = candidates.filter { it != signer }.distinct().toMutableList()
        while (current.subjectX500Principal != current.issuerX500Principal) {
            val issuer =
                remaining.firstOrNull { candidate ->
                    candidate.subjectX500Principal == current.issuerX500Principal && signedBy(current, candidate)
                } ?: break
            remaining.remove(issuer)
            chain.add(issuer)
            current = issuer
        }
        return chain
    }

    private fun signedBy(
        cert: X509Certificate,
        issuer: X509Certificate,
    ): Boolean =
        try {
            cert.verify(issuer.publicKey)
            true
        } catch (_: GeneralSecurityException) {
            false
        }

    private fun issuerSerialV2Matches(
        b64: String,
        cert: X509Certificate,
    ): Boolean {
        val issuerSerial =
            IssuerSerial.getInstance(
                ASN1Primitive.fromByteArray(Base64.getMimeDecoder().decode(b64)),
            )
        if (issuerSerial.serial.value != cert.serialNumber) return false
        val certIssuer = X500Name.getInstance(cert.issuerX500Principal.encoded)
        return issuerSerial.issuer.names.any {
            it.tagNo == GeneralName.directoryName && X500Name.getInstance(it.name) == certIssuer
        }
    }

    private fun issuerSerialV1Matches(
        el: Element,
        cert: X509Certificate,
    ): Boolean {
        val issuerName = childElements(el, DS_NS, "X509IssuerName").single().textContent.trim()
        val serial = BigInteger(childElements(el, DS_NS, "X509SerialNumber").single().textContent.trim())
        return serial == cert.serialNumber && X500Principal(issuerName) == cert.issuerX500Principal
    }

    /** Returns a failure when the SignedInfo references are not exactly the enveloped XAdES pair. */
    private fun checkReferences(
        xmlSig: XMLSignature,
        signedPropertiesId: String,
    ): Invalid? {
        val references = xmlSig.signedInfo.references.filterIsInstance<Reference>()
        if (references.size != 2) {
            return Invalid.Malformed("expected exactly 2 <ds:Reference> elements, found ${references.size}")
        }
        val documentRef =
            references.singleOrNull { it.uri == "" }
                ?: return Invalid.Malformed("signature does not cover the whole document (no Reference with URI=\"\")")
        val docTransforms = documentRef.transforms.filterIsInstance<Transform>().map { it.algorithm }
        if (docTransforms.firstOrNull() != Transform.ENVELOPED || docTransforms.drop(1).any { it !in C14N_ALGORITHMS }) {
            return Invalid.Malformed(
                "document Reference must use the enveloped-signature transform (optionally followed by c14n); " +
                    "got $docTransforms",
            )
        }
        val propsRef =
            references.singleOrNull { it.type == SIGNED_PROPERTIES_TYPE && it !== documentRef }
                ?: return Invalid.Malformed("signature has no single SignedProperties Reference")
        if (propsRef.uri != "#$signedPropertiesId") {
            return Invalid.Malformed(
                "SignedProperties Reference URI '${propsRef.uri}' does not point at " +
                    "'#$signedPropertiesId' inside this signature",
            )
        }
        val propsTransforms = propsRef.transforms.filterIsInstance<Transform>().map { it.algorithm }
        if (propsTransforms.any { it !in C14N_ALGORITHMS }) {
            return Invalid.Malformed("SignedProperties Reference uses unsupported transforms $propsTransforms")
        }
        return null
    }

    private fun extractKeyInfoCerts(signatureElement: Element): List<X509Certificate> {
        val cf = CertificateFactory.getInstance("X.509")
        return childElements(signatureElement, DS_NS, "KeyInfo")
            .flatMap { childElements(it, DS_NS, "X509Data") }
            .flatMap { childElements(it, DS_NS, "X509Certificate") }
            .map { node ->
                val der = Base64.getMimeDecoder().decode(node.textContent.trim())
                cf.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
            }
    }

    private fun childElements(
        parent: Element,
        ns: String,
        localName: String,
    ): List<Element> {
        val out = mutableListOf<Element>()
        var c = parent.firstChild
        while (c != null) {
            if (c is Element && c.namespaceURI == ns && c.localName == localName) out.add(c)
            c = c.nextSibling
        }
        return out
    }

    private fun findDuplicateId(root: Element?): String? {
        if (root == null) return null
        val seen = HashSet<String>()
        val stack = ArrayDeque<Element>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val el = stack.removeLast()
            val attrs = el.attributes
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i)
                val name = attr.localName ?: attr.nodeName
                if (name in ID_ATTRIBUTE_NAMES && !seen.add(attr.nodeValue)) return attr.nodeValue
            }
            var c = el.firstChild
            while (c != null) {
                if (c is Element) stack.addLast(c)
                c = c.nextSibling
            }
        }
        return null
    }

    /** [KeySelector] that only ever yields the bound signer certificate's public key. */
    private class FixedKeySelector(
        private val signerCert: X509Certificate,
    ) : KeySelector() {
        override fun select(
            keyInfo: KeyInfo?,
            purpose: Purpose,
            method: AlgorithmMethod,
            context: XMLCryptoContext,
        ): KeySelectorResult {
            if (purpose != Purpose.VERIFY) throw KeySelectorException("unsupported key purpose $purpose")
            return KeySelectorResult { signerCert.publicKey }
        }
    }

    private companion object {
        val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
        val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
        val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"
        const val KU_DIGITAL_SIGNATURE = 0
        const val KU_NON_REPUDIATION = 1
        const val KU_KEY_CERT_SIGN = 5
        val ID_ATTRIBUTE_NAMES = setOf("Id", "ID", "id")
        val DIGEST_ALGORITHMS =
            mapOf(
                "http://www.w3.org/2001/04/xmlenc#sha256" to "SHA-256",
                "http://www.w3.org/2001/04/xmldsig-more#sha384" to "SHA-384",
                "http://www.w3.org/2001/04/xmlenc#sha512" to "SHA-512",
            )
        val C14N_ALGORITHMS =
            setOf(
                CanonicalizationMethod.INCLUSIVE,
                CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS,
                CanonicalizationMethod.EXCLUSIVE,
                CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS,
                "http://www.w3.org/2006/12/xml-c14n11",
                "http://www.w3.org/2006/12/xml-c14n11#WithComments",
            )
    }
}

// TODO(detached): support detached XAdES — verify that external URIs resolve.
// TODO(enveloping): support enveloping XAdES — verify the wrapped <ds:Object> reference.
