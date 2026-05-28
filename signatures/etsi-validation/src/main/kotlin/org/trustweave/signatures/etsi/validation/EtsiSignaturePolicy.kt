package org.trustweave.signatures.etsi.validation

import org.trustweave.signatures.trustlists.TspServiceStatus
import kotlin.time.Duration

/**
 * Caller-supplied policy enforced by [EtsiSignatureValidator].
 *
 * Encodes the algorithm allow-lists, signing-time tolerance, time-stamp requirement, and the
 * set of accepted trust-list status URIs that ETSI EN 319 102-1 §5.5.1/§5.5.3 expect as
 * inputs to the Signature Validation Algorithm.
 *
 * Defaults align with the JAdES MVP profile: ECDSA + EdDSA, SHA-2 family digests, 5-minute
 * clock skew, GRANTED-only trust status, time-stamp optional.
 *
 * @property acceptedSignatureAlgorithms JOSE `alg` identifiers accepted by §5.5.1 crypto check.
 * @property acceptedDigestAlgorithms    Digest algorithm names accepted by §5.5.1 crypto check.
 *                                        Currently informational — the JAdES MVP fixes the
 *                                        digest by JOSE alg, so this list is exposed for future
 *                                        explicit-digest extensions and report introspection.
 * @property requireSigningTime          When true, the protected header MUST carry a `sigT`.
 * @property maxClockSkew                Tolerance applied to signing-time / cert-validity /
 *                                        TSA-time comparisons.
 * @property requireTimeStamp            When true, the signature MUST embed at least one
 *                                        sigTst token (B-T or higher).
 * @property allowedTrustStatusUris      ETSI service-status URIs the caller considers
 *                                        acceptable for §5.5.3 signature-policy compliance.
 *                                        Defaults to GRANTED only.
 */
data class EtsiSignaturePolicy(
    val acceptedSignatureAlgorithms: Set<String> = setOf("ES256", "ES384", "ES512", "EdDSA"),
    val acceptedDigestAlgorithms: Set<String> = setOf("SHA-256", "SHA-384", "SHA-512"),
    val requireSigningTime: Boolean = true,
    val maxClockSkew: Duration = Duration.parse("PT5M"),
    val requireTimeStamp: Boolean = false,
    val allowedTrustStatusUris: Set<String> = setOf(
        TspServiceStatus.GRANTED.uri,
    ),
)
