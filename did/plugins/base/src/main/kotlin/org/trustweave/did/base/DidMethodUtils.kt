package org.trustweave.did.base

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.*
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.model.DidService
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolutionMetadata
import org.trustweave.kms.KeyHandle
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Common utilities for DID method implementations.
 *
 * Provides helper functions for:
 * - DID parsing and validation
 * - Verification method type mapping (algorithm → verification method type)
 * - DID document builder helpers
 * - Resolution metadata helpers
 */
object DidMethodUtils {

    /**
     * Parses a DID string into method and identifier parts.
     *
     * @param did The DID string (e.g., "did:web:example.com")
     * @return Pair of (method, identifier) or null if invalid
     */
    fun parseDid(did: String): Pair<String, String>? {
        if (!did.startsWith("did:")) {
            return null
        }
        val parts = did.substring(4).split(":", limit = 2)
        if (parts.size != 2) {
            return null
        }
        return parts[0] to parts[1]
    }

    /**
     * Validates that a DID matches a specific method.
     *
     * @param did The DID to validate
     * @param expectedMethod The expected method name
     * @throws IllegalArgumentException if the DID doesn't match the method
     */
    fun validateDidMethod(did: String, expectedMethod: String) {
        val parsed = parseDid(did)
            ?: throw IllegalArgumentException("Invalid DID format: $did")

        if (parsed.first != expectedMethod) {
            throw IllegalArgumentException("DID method mismatch: expected $expectedMethod, got ${parsed.first}")
        }
    }

    /**
     * Maps a cryptographic algorithm to its verification method type.
     *
     * @param algorithm Algorithm name (e.g., "Ed25519", "secp256k1")
     * @return Verification method type (e.g., "Ed25519VerificationKey2020")
     */
    fun algorithmToVerificationMethodType(algorithm: String): String {
        return when (algorithm.uppercase()) {
            "ED25519" -> "Ed25519VerificationKey2020"
            "X25519" -> "X25519KeyAgreementKey2020"
            "SECP256K1" -> "EcdsaSecp256k1VerificationKey2019"
            "P-256", "P256" -> "EcdsaSecp256r1VerificationKey2019"
            "P-384", "P384" -> "EcdsaSecp384r1VerificationKey2019"
            "P-521", "P521" -> "EcdsaSecp521r1VerificationKey2019"
            "RSA" -> "RsaVerificationKey2018"
            else -> "JsonWebKey2020"
        }
    }

    /**
     * Maps a KeyAlgorithm enum to its verification method type.
     *
     * @param algorithm The KeyAlgorithm enum value
     * @return Verification method type
     */
    fun algorithmToVerificationMethodType(algorithm: KeyAlgorithm): String {
        return algorithmToVerificationMethodType(algorithm.algorithmName)
    }

    /**
     * Creates a verification method reference from a key handle.
     *
     * @param did The DID identifier
     * @param keyHandle The key handle from KMS
     * @param algorithm The algorithm name
     * @param controller Optional controller DID (defaults to the DID itself)
     * @return VerificationMethod
     */
    fun createVerificationMethod(
        did: String,
        keyHandle: KeyHandle,
        algorithm: String,
        controller: String? = null
    ): VerificationMethod {
        val didObj = Did(did)
        val verificationMethodIdStr = "$did#${keyHandle.id.value}"
        val verificationMethodId = VerificationMethodId.parse(verificationMethodIdStr, didObj)
        val verificationMethodType = algorithmToVerificationMethodType(algorithm)
        val controllerDid = if (controller != null) Did(controller) else didObj

        return VerificationMethod(
            id = verificationMethodId,
            type = verificationMethodType,
            controller = controllerDid,
            publicKeyJwk = keyHandle.publicKeyJwk,
            publicKeyMultibase = keyHandle.publicKeyMultibase
        )
    }

    /**
     * Creates a verification method reference from a key handle using KeyAlgorithm enum.
     *
     * @param did The DID identifier
     * @param keyHandle The key handle from KMS
     * @param algorithm The KeyAlgorithm enum value
     * @param controller Optional controller DID (defaults to the DID itself)
     * @return VerificationMethod
     */
    fun createVerificationMethod(
        did: String,
        keyHandle: KeyHandle,
        algorithm: KeyAlgorithm,
        controller: String? = null
    ): VerificationMethod {
        return createVerificationMethod(did, keyHandle, algorithm.algorithmName, controller)
    }

    /**
     * Builds a DID document with standard structure.
     *
     * @param did The DID identifier
     * @param verificationMethod The verification methods
     * @param authentication Optional authentication methods (defaults to first verification method)
     * @param assertionMethod Optional assertion methods
     * @param keyAgreement Optional key agreement methods
     * @param service Optional service endpoints
     * @return DidDocument
     */
    fun buildDidDocument(
        did: String,
        verificationMethod: List<VerificationMethod>,
        authentication: List<String>? = null,
        assertionMethod: List<String>? = null,
        keyAgreement: List<String>? = null,
        service: List<DidService>? = null
    ): DidDocument {
        require(verificationMethod.isNotEmpty()) { "DID document must have at least one verification method" }

        val didObj = Did(did)
        val defaultAuth = listOf(verificationMethod.first().id)
        val auth = authentication?.map { VerificationMethodId.parse(it, didObj) } ?: defaultAuth
        val assertion = assertionMethod?.map { VerificationMethodId.parse(it, didObj) } ?: emptyList()
        val keyAgr = keyAgreement?.map { VerificationMethodId.parse(it, didObj) } ?: emptyList()

        return DidDocument(
            id = didObj,
            verificationMethod = verificationMethod,
            authentication = auth,
            assertionMethod = assertion,
            keyAgreement = keyAgr,
            service = service ?: emptyList()
        )
    }

    /**
     * Creates a successful DID resolution result.
     *
     * @param document The resolved DID document
     * @param method The DID method name
     * @param created Optional creation timestamp (defaults to now)
     * @param updated Optional update timestamp (defaults to now)
     * @param deactivated Whether the DID has been deactivated (defaults to false)
     * @return DidResolutionResult.Success
     */
    fun createSuccessResolutionResult(
        document: DidDocument,
        method: String,
        created: Instant? = null,
        updated: Instant? = null,
        deactivated: Boolean = false
    ): DidResolutionResult {
        val now = Clock.System.now()
        return DidResolutionResult.Success(
            document = document,
            documentMetadata = DidDocumentMetadata(
                created = created ?: now,
                updated = updated ?: now,
                deactivated = deactivated
            ),
            resolutionMetadata = DidResolutionMetadata(
                pattern = method,
                properties = mapOf("driver" to "TrustWeave")
            )
        )
    }

    /**
     * Creates an error DID resolution result.
     *
     * @param error Error code (e.g., "notFound", "invalidDid")
     * @param message Optional error message
     * @param method The DID method name
     * @param did Optional DID string for error context
     * @return DidResolutionResult.Failure
     */
    fun createErrorResolutionResult(
        error: String,
        message: String? = null,
        method: String? = null,
        did: String? = null
    ): DidResolutionResult {
        val metadata = DidResolutionMetadata(
            error = error,
            errorMessage = message,
            pattern = method
        )

        return when (error.lowercase()) {
            "notfound", "did_not_found" -> DidResolutionResult.Failure.NotFound(
                did = Did(did ?: "did:unknown:unknown"),
                reason = message,
                resolutionMetadata = metadata
            )
            "invalidformat", "invalid_did_format", "invaliddid" -> DidResolutionResult.Failure.InvalidFormat(
                did = did ?: "unknown",
                reason = message ?: "Invalid DID format",
                resolutionMetadata = metadata
            )
            "methodnotregistered", "method_not_registered" -> DidResolutionResult.Failure.MethodNotRegistered(
                method = method ?: "unknown",
                availableMethods = emptyList(),
                resolutionMetadata = metadata
            )
            else -> DidResolutionResult.Failure.ResolutionError(
                did = Did(did ?: "did:unknown:unknown"),
                reason = message ?: error,
                resolutionMetadata = metadata
            )
        }
    }

    /**
     * Percent-decodes a single did:web method-specific-id segment (RFC 3986 §2.1).
     *
     * Per the W3C did:web specification, each colon-separated segment of the
     * method-specific identifier MUST be percent-decoded before constructing the
     * HTTPS URL. This is what allows ports to be expressed:
     * `did:web:example.com%3A8080` → host `example.com:8080`.
     *
     * @param segment The raw (possibly percent-encoded) segment
     * @return The decoded segment (UTF-8)
     * @throws IllegalArgumentException if the segment contains a malformed percent-encoding
     */
    fun percentDecode(segment: String): String {
        if ('%' !in segment) return segment
        // Strict RFC 3986 hex digit — String.toIntOrNull(16) must NOT be used here
        // because it accepts a sign ("%+3" would decode as 0x03).
        fun hexDigit(c: Char): Int = when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> throw IllegalArgumentException("Malformed percent-encoding in segment: $segment")
        }
        val out = StringBuilder()
        val byteBuffer = java.io.ByteArrayOutputStream()
        fun flushBytes() {
            if (byteBuffer.size() > 0) {
                out.append(byteBuffer.toByteArray().toString(Charsets.UTF_8))
                byteBuffer.reset()
            }
        }
        var i = 0
        while (i < segment.length) {
            val c = segment[i]
            if (c == '%') {
                require(i + 2 < segment.length) { "Malformed percent-encoding in segment: $segment" }
                val value = (hexDigit(segment[i + 1]) shl 4) or hexDigit(segment[i + 2])
                byteBuffer.write(value)
                i += 3
            } else {
                flushBytes()
                out.append(c)
                i++
            }
        }
        flushBytes()
        return out.toString()
    }

    /**
     * Normalizes a domain name for did:web.
     *
     * Converts domain to lowercase and validates format. The domain may include
     * a port (`example.com:8080`); the port separator is percent-encoded by
     * [buildWebDid] when the DID identifier is constructed.
     *
     * @param domain The domain name
     * @return Normalized domain name
     * @throws IllegalArgumentException if domain is invalid
     */
    fun normalizeDomain(domain: String): String {
        val normalized = domain.lowercase().trim()

        if (normalized.isEmpty()) {
            throw IllegalArgumentException("Domain cannot be empty")
        }

        // Basic validation - domain should not contain protocol or path
        if (normalized.contains("://") || normalized.startsWith("/")) {
            throw IllegalArgumentException("Domain should not contain protocol or path: $domain")
        }

        return normalized
    }

    /**
     * Builds a did:web identifier from a domain.
     *
     * Per the W3C did:web specification, a port separator in the host must be
     * percent-encoded so it is not confused with the path delimiter:
     * `example.com:8080` → `did:web:example.com%3A8080`.
     *
     * @param domain The domain name, optionally with port (e.g., "example.com", "example.com:8080")
     * @param path Optional path (e.g., "user:alice")
     * @return DID string (e.g., "did:web:example.com:user:alice", "did:web:example.com%3A8080")
     */
    fun buildWebDid(domain: String, path: String? = null): String {
        val normalized = normalizeDomain(domain).replace(":", "%3A")
        return if (path != null && path.isNotBlank()) {
            "did:web:$normalized:$path"
        } else {
            "did:web:$normalized"
        }
    }

    // ──────────────────────────── Multibase / Multicodec ────────────────────────────

    /**
     * Returns the 2-byte multicodec prefix for [algorithm].
     *
     * Supports the algorithms used by did:key and did:peer.
     * Prefixes are the varint encodings from the multicodec table:
     * `ed25519-pub` (0xed), `x25519-pub` (0xec), `secp256k1-pub` (0xe7),
     * `p256-pub` (0x1200), `p384-pub` (0x1201), `p521-pub` (0x1202).
     *
     * @throws IllegalArgumentException if the algorithm has no known multicodec prefix.
     */
    fun getMulticodecPrefix(algorithm: String): ByteArray = when (algorithm.uppercase()) {
        "ED25519"  -> byteArrayOf(0xed.toByte(), 0x01)
        "X25519"   -> byteArrayOf(0xec.toByte(), 0x01)
        "SECP256K1" -> byteArrayOf(0xe7.toByte(), 0x01)
        "P-256"    -> byteArrayOf(0x80.toByte(), 0x24)
        "P-384"    -> byteArrayOf(0x81.toByte(), 0x24)
        "P-521"    -> byteArrayOf(0x82.toByte(), 0x24)
        else -> throw IllegalArgumentException("No multicodec prefix for algorithm: $algorithm")
    }

    /**
     * Parses a multicodec-prefixed byte array and returns a (algorithm, publicKeyBytes) pair.
     *
     * Returns `null` if the prefix is not recognised.
     */
    fun parseMulticodecKey(prefixedKey: ByteArray): Pair<String, ByteArray>? {
        if (prefixedKey.size < 2) return null
        val b1 = prefixedKey[0].toInt() and 0xFF
        val b2 = prefixedKey[1].toInt() and 0xFF
        val algorithm = when {
            b1 == 0xed && b2 == 0x01 -> "ED25519"
            b1 == 0xec && b2 == 0x01 -> "X25519"
            b1 == 0xe7 && b2 == 0x01 -> "SECP256K1"
            b1 == 0x80 && b2 == 0x24 -> "P-256"
            b1 == 0x81 && b2 == 0x24 -> "P-384"
            b1 == 0x82 && b2 == 0x24 -> "P-521"
            else -> return null
        }
        return algorithm to prefixedKey.sliceArray(2 until prefixedKey.size)
    }

    // ──────────────────────────── EC point compression ────────────────────────────

    /**
     * Domain parameters for the short-Weierstrass curves supported by did:key
     * (y² = x³ + ax + b over GF(p)). All four primes satisfy p ≡ 3 (mod 4),
     * so a modular square root is `c^((p+1)/4) mod p`.
     */
    private class EcCurveParams(
        val p: java.math.BigInteger,
        val a: java.math.BigInteger,
        val b: java.math.BigInteger,
        val coordinateSize: Int
    )

    private val ecCurves: Map<String, EcCurveParams> by lazy {
        fun big(hex: String) = java.math.BigInteger(hex, 16)
        val three = java.math.BigInteger.valueOf(3)
        val pK1 = big("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F")
        val p256 = big("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF")
        val p384 = big("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFF")
        val p521 = java.math.BigInteger.ONE.shiftLeft(521).subtract(java.math.BigInteger.ONE)
        mapOf(
            "SECP256K1" to EcCurveParams(pK1, java.math.BigInteger.ZERO, java.math.BigInteger.valueOf(7), 32),
            "P-256" to EcCurveParams(
                p256,
                p256.subtract(three),
                big("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B"),
                32
            ),
            "P-384" to EcCurveParams(
                p384,
                p384.subtract(three),
                big("B3312FA7E23EE7E4988E056BE3F82D19181D9C6EFE8141120314088F5013875AC656398D8A2ED19D2A85C8EDD3EC2AEF"),
                48
            ),
            "P-521" to EcCurveParams(
                p521,
                p521.subtract(three),
                big("0051953EB9618E1C9A1F929A21A0B68540EEA2DA725B99B315F3B8B489918EF109E156193951EC7E937B1652C0BD3BB1BF073573DF883D2C34F1EF451FD46B503F00"),
                66
            )
        )
    }

    /**
     * Returns the field-element (coordinate) size in bytes for [algorithm],
     * or `null` if it is not a supported short-Weierstrass EC algorithm.
     */
    fun ecCoordinateSize(algorithm: String): Int? = ecCurves[algorithm.uppercase()]?.coordinateSize

    /**
     * Compresses an EC public key point to SEC1 compressed form (`0x02/0x03 || x`),
     * as required by the multicodec table for `secp256k1-pub`, `p256-pub`,
     * `p384-pub` and `p521-pub`.
     *
     * Accepts either an uncompressed point (`0x04 || x || y`) or an
     * already-compressed point (returned unchanged).
     *
     * @throws IllegalArgumentException if [algorithm] is not a supported EC curve
     *         or [point] is not a valid SEC1 encoding
     */
    fun compressEcPublicKey(algorithm: String, point: ByteArray): ByteArray {
        val curve = ecCurves[algorithm.uppercase()]
            ?: throw IllegalArgumentException("Not a supported EC algorithm: $algorithm")
        val size = curve.coordinateSize
        return when {
            point.size == size + 1 && (point[0] == 0x02.toByte() || point[0] == 0x03.toByte()) -> point
            point.size == 2 * size + 1 && point[0] == 0x04.toByte() -> {
                val x = point.copyOfRange(1, 1 + size)
                val yIsOdd = (point[point.size - 1].toInt() and 1) == 1
                byteArrayOf(if (yIsOdd) 0x03 else 0x02) + x
            }
            else -> throw IllegalArgumentException(
                "Invalid SEC1 point encoding for $algorithm: ${point.size} bytes"
            )
        }
    }

    /**
     * Decompresses an EC public key point to SEC1 uncompressed form (`0x04 || x || y`).
     *
     * Accepts a compressed point (`0x02/0x03 || x`, decompressed by solving
     * y² = x³ + ax + b over GF(p)) or a legacy uncompressed point (returned
     * unchanged, for backward compatibility with pre-spec did:key encodings).
     *
     * @throws IllegalArgumentException if [algorithm] is not a supported EC curve,
     *         [point] is not a valid SEC1 encoding, or x is not on the curve
     */
    fun decompressEcPublicKey(algorithm: String, point: ByteArray): ByteArray {
        val curve = ecCurves[algorithm.uppercase()]
            ?: throw IllegalArgumentException("Not a supported EC algorithm: $algorithm")
        val size = curve.coordinateSize
        return when {
            point.size == 2 * size + 1 && point[0] == 0x04.toByte() -> point
            point.size == size + 1 && (point[0] == 0x02.toByte() || point[0] == 0x03.toByte()) -> {
                val p = curve.p
                val x = java.math.BigInteger(1, point.copyOfRange(1, 1 + size))
                require(x < p) { "EC point x coordinate out of range for $algorithm" }
                // rhs = x³ + ax + b (mod p)
                val rhs = x.multiply(x).multiply(x).add(curve.a.multiply(x)).add(curve.b).mod(p)
                // p ≡ 3 (mod 4) for all supported curves → sqrt(c) = c^((p+1)/4) mod p
                var y = rhs.modPow(p.add(java.math.BigInteger.ONE).shiftRight(2), p)
                if (y.multiply(y).mod(p) != rhs) {
                    throw IllegalArgumentException("Invalid EC point: x is not on curve $algorithm")
                }
                val wantOdd = point[0] == 0x03.toByte()
                if (y.testBit(0) != wantOdd) {
                    y = p.subtract(y)
                }
                byteArrayOf(0x04) + x.toFixedBytes(size) + y.toFixedBytes(size)
            }
            else -> throw IllegalArgumentException(
                "Invalid SEC1 point encoding for $algorithm: ${point.size} bytes"
            )
        }
    }

    private fun java.math.BigInteger.toFixedBytes(size: Int): ByteArray {
        val raw = toByteArray()
        return when {
            raw.size == size -> raw
            raw.size == size + 1 && raw[0] == 0.toByte() -> raw.copyOfRange(1, raw.size)
            raw.size < size -> ByteArray(size - raw.size) + raw
            else -> throw IllegalArgumentException("Value does not fit in $size bytes")
        }
    }
}

