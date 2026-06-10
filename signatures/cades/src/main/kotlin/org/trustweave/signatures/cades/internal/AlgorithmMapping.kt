package org.trustweave.signatures.cades.internal

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.trustweave.kms.Algorithm

/**
 * Maps TrustWeave [Algorithm] values to the ASN.1 OIDs used inside a CAdES `SignerInfo`.
 *
 * MVP scope: ECDSA on P-256/P-384/P-521 with SHA-256/384/512 and Ed25519. RSA-PSS is intentionally
 * absent — it can be added later by extending [forAlgorithm]. HMAC is not relevant for CAdES
 * qualified signatures.
 */
internal object AlgorithmMapping {

    /**
     * Resolve a TrustWeave [Algorithm] to the [Mapping] containing the JCA name (for the KMS),
     * the signature-algorithm OID for the CMS `SignerInfo`, and the digest-algorithm OID.
     */
    fun forAlgorithm(algorithm: Algorithm): Mapping? = when (algorithm) {
        Algorithm.P256 -> Mapping(algorithm, "SHA256withECDSA", ECDSA_WITH_SHA256, NIST_SHA256)
        Algorithm.P384 -> Mapping(algorithm, "SHA384withECDSA", ECDSA_WITH_SHA384, NIST_SHA384)
        Algorithm.P521 -> Mapping(algorithm, "SHA512withECDSA", ECDSA_WITH_SHA512, NIST_SHA512)
        // Ed25519: RFC 8419 — single OID id-Ed25519 (1.3.101.112), no separate hash. The CAdES
        // verifier knows to feed the SignedAttributes bytes directly to Ed25519.
        Algorithm.Ed25519 -> Mapping(algorithm, "Ed25519", ED_25519, NIST_SHA512)
        else -> null
    }

    data class Mapping(
        val algorithm: Algorithm,
        val jcaScheme: String,
        val sigOid: ASN1ObjectIdentifier,
        val digestOid: ASN1ObjectIdentifier,
    ) {
        fun signatureAlgorithmIdentifier(): AlgorithmIdentifier = AlgorithmIdentifier(sigOid)
        fun digestAlgorithmIdentifier(): AlgorithmIdentifier = AlgorithmIdentifier(digestOid)
    }

    // ANSI X9.62 ECDSA-with-SHAx OIDs.
    private val ECDSA_WITH_SHA256 = ASN1ObjectIdentifier("1.2.840.10045.4.3.2")
    private val ECDSA_WITH_SHA384 = ASN1ObjectIdentifier("1.2.840.10045.4.3.3")
    private val ECDSA_WITH_SHA512 = ASN1ObjectIdentifier("1.2.840.10045.4.3.4")
    // RFC 8410 / 8419 Ed25519.
    private val ED_25519 = ASN1ObjectIdentifier("1.3.101.112")
    // NIST OIDs for SHA-2.
    private val NIST_SHA256 = ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1")
    private val NIST_SHA384 = ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.2")
    private val NIST_SHA512 = ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.3")
}
