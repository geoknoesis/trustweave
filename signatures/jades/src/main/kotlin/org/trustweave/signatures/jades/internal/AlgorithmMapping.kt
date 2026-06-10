package org.trustweave.signatures.jades.internal

import org.trustweave.kms.Algorithm

/**
 * JOSE algorithm identifiers → TrustWeave [Algorithm] sealed class members + JCA signature schemes.
 *
 * MVP scope: ECDSA on P-256/P-384/P-521 and Ed25519. RSA-PSS and HMAC are intentionally absent —
 * RSA can be added later by extending [forJoseAlg]; HMAC is not relevant for JAdES qualified
 * signatures.
 */
internal object AlgorithmMapping {

    /**
     * Resolve a JOSE `alg` string to a TrustWeave [Algorithm] and the JCA signature scheme name.
     *
     * The JCA scheme is the string accepted by [java.security.Signature.getInstance]. For Ed25519
     * this is `"Ed25519"` (JDK 15+) and the signature output is raw 64 bytes. For ECDSA the JCA
     * output is **DER-encoded** (`SEQUENCE { INTEGER r, INTEGER s }`) — callers MUST convert to
     * raw R||S for JWS via [EcdsaSignatureConversion.derToRaw] and back via [derFromRaw] for
     * verification.
     *
     * @return mapping triple `(Algorithm, jcaName, rawSignatureSize)` or null if the JOSE alg is
     *         unsupported by the MVP. `rawSignatureSize` is the JWS-canonical signature byte
     *         length (R||S concatenation for ECDSA, 64 for Ed25519).
     */
    fun forJoseAlg(jose: String): Mapping? = when (jose) {
        "ES256" -> Mapping(Algorithm.P256, jcaScheme = "SHA256withECDSA", rawSize = 64)
        "ES384" -> Mapping(Algorithm.P384, jcaScheme = "SHA384withECDSA", rawSize = 96)
        "ES512" -> Mapping(Algorithm.P521, jcaScheme = "SHA512withECDSA", rawSize = 132)
        "EdDSA" -> Mapping(Algorithm.Ed25519, jcaScheme = "Ed25519", rawSize = 64)
        else -> null
    }

    /**
     * Inverse of [forJoseAlg]. Returns the canonical JOSE `alg` identifier for [algorithm] or
     * null when there is no MVP mapping.
     */
    fun forAlgorithm(algorithm: Algorithm): String? = when (algorithm) {
        Algorithm.Ed25519 -> "EdDSA"
        Algorithm.P256 -> "ES256"
        Algorithm.P384 -> "ES384"
        Algorithm.P521 -> "ES512"
        else -> null
    }

    data class Mapping(val algorithm: Algorithm, val jcaScheme: String, val rawSize: Int)
}
