package org.trustweave.did.verification

import org.trustweave.core.util.decodeBase58
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionMetadata
import kotlin.time.Clock

/**
 * DID Document Verification Service.
 *
 * Verifies cryptographic proofs and signatures on DID documents to ensure
 * authenticity and integrity.
 *
 * **Verification Checks:**
 * - Document structure validation
 * - Verification method structure validation
 * - Service structure validation
 * - Integrity verification (digest checking)
 * - Signature verification (method-specific)
 *
 * **Example Usage:**
 * ```kotlin
 * val verificationService = DefaultDidDocumentVerificationService(
 *     canonicalizationService = JsonLdCanonicalizationService()
 * )
 *
 * val result = verificationService.verifyDocument(
 *     document = didDocument,
 *     resolutionMetadata = resolutionMetadata
 * )
 *
 * if (!result.valid) {
 *     println("Verification failed: ${result.errors}")
 * }
 * ```
 */
interface DidDocumentVerificationService {
    /**
     * Verify DID document integrity and authenticity.
     *
     * @param document The DID document to verify
     * @param resolutionMetadata Resolution metadata (may contain digest for integrity check)
     * @return Verification result with validity status and any errors/warnings
     */
    suspend fun verifyDocument(
        document: DidDocument,
        resolutionMetadata: DidResolutionMetadata,
    ): VerificationResult

    /**
     * Like [verifyDocument], and additionally checks that the document is the one for
     * [expectedDid]: `document.id` must equal it exactly. A resolver that returns another
     * subject's document for the requested DID must not pass verification.
     *
     * The default implementation delegates to [verifyDocument] and adds the id check, so existing
     * implementations keep working; [DefaultDidDocumentVerificationService] overrides it.
     *
     * @param expectedDid the DID the caller asked to resolve; `null` skips the id check
     */
    suspend fun verifyDocument(
        document: DidDocument,
        resolutionMetadata: DidResolutionMetadata,
        expectedDid: String?,
    ): VerificationResult {
        val result = verifyDocument(document, resolutionMetadata)
        if (expectedDid == null || document.id.value == expectedDid) return result
        return result.copy(
            valid = false,
            errors = result.errors + "Document id '${document.id.value}' does not match the expected DID '$expectedDid'",
        )
    }

    /**
     * Verify document signature (if method supports it).
     *
     * **This does not verify any cryptographic signature.** For `did:key`, the only method with an
     * implemented check, `true` means *self-certification only*: the key encoded in the DID
     * identifier equals the document's single verification method. Nothing is signed or checked
     * against a signature. Prefer [checkDocumentSelfCertification], whose result says so, or
     * [verifyVerificationMethod] to check an actual signature.
     *
     * **`false` means "not verified", which is not the same as "verified invalid".** For every
     * method without an implemented check, including `did:ion` whose proof chain is not
     * implemented, the result is `false` (fail-closed) and a warning is logged naming the reason.
     *
     * @param document The DID document
     * @param method The DID method name
     * @return true if the document is self-certifying for [method] (no signature is verified),
     *   false otherwise
     */
    suspend fun verifyDocumentSignature(
        document: DidDocument,
        method: String,
    ): Boolean

    /**
     * Same check as [verifyDocumentSignature], but reported honestly: the returned
     * [VerificationResult] states that at most a *self-certification* was established and that
     * **no signature was verified** ([VerificationResult.integrityVerified] is always `false`).
     *
     * The default implementation delegates to [verifyDocumentSignature].
     */
    suspend fun checkDocumentSelfCertification(
        document: DidDocument,
        method: String,
    ): VerificationResult =
        if (verifyDocumentSignature(document, method)) {
            VerificationResult(
                valid = true,
                warnings = listOf("did:$method self-certification only: the identifier matches the key; no signature was verified"),
                integrityVerified = false,
            )
        } else {
            VerificationResult(
                valid = false,
                errors =
                    listOf(
                        "did:$method document could not be verified " +
                            "(self-certification failed or is unsupported); no signature was verified",
                    ),
                integrityVerified = false,
            )
        }

    /**
     * Verify verification method signatures.
     *
     * @param method The verification method
     * @param signature The signature to verify
     * @param data The data that was signed
     * @return true if signature is valid
     */
    suspend fun verifyVerificationMethod(
        method: org.trustweave.did.model.VerificationMethod,
        signature: String,
        data: ByteArray,
    ): Boolean
}

/**
 * Verification result.
 *
 * @property valid no check that was performed failed. This is **not** a statement that the
 *   document's integrity was established: see [integrityVerified].
 * @property integrityVerified `true` only when a digest was supplied and the document's
 *   canonical digest was compared against it and matched. `false` when no digest was supplied
 *   (nothing to compare against; a warning says so) or the comparison could not be made.
 */
data class VerificationResult(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val verifiedAt: kotlin.time.Instant = Clock.System.now(),
    val verificationMethod: String? = null,
    val integrityVerified: Boolean = false,
)

/**
 * Default implementation of document verification service.
 */
class DefaultDidDocumentVerificationService(
    private val canonicalizationService: DidDocumentCanonicalizationService,
) : DidDocumentVerificationService {
    private val logger =
        org.trustweave.did.util.DidLogging
            .getLogger(DefaultDidDocumentVerificationService::class.java)

    override suspend fun verifyDocument(
        document: DidDocument,
        resolutionMetadata: DidResolutionMetadata,
    ): VerificationResult = verifyDocument(document, resolutionMetadata, null)

    override suspend fun verifyDocument(
        document: DidDocument,
        resolutionMetadata: DidResolutionMetadata,
        expectedDid: String?,
    ): VerificationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var integrityVerified = false

        if (expectedDid != null && document.id.value != expectedDid) {
            errors.add("Document id '${document.id.value}' does not match the expected DID '$expectedDid'")
        }

        // 1. Verify document structure
        if (document.id.value.isEmpty()) {
            errors.add("Document ID is empty")
        }

        // 2. Verify verification methods
        document.verificationMethod.forEach { method ->
            if (!verifyVerificationMethodStructure(method)) {
                errors.add("Invalid verification method structure: ${method.id}")
            }
        }

        // 3. Verify services
        document.service.forEach { service ->
            if (!verifyServiceStructure(service)) {
                warnings.add("Invalid service structure: ${service.id}")
            }
        }

        // 4. Verify integrity (if digest provided)
        // Both computedDigest and expectedDigest are multibase-encoded strings (base64url
        // with 'u' prefix). Decode both to raw bytes before comparing so that the comparison
        // operates on the actual digest bytes, not on the ASCII encoding of the encoded form.
        resolutionMetadata.properties["digest"]?.let { expectedDigest ->
            if (!canonicalizationService.isConformant) {
                // The integrity check cannot be performed: comparing against a digest computed by
                // a non-conformant canonicalizer would report a mismatch against any external
                // resolver. But a digest was supplied and not checked, so the document must not
                // be reported as valid either: "could not check" is not "checked and fine".
                errors.add(
                    "Document digest could not be verified: the canonicalization service is not " +
                        "URDNA2015-conformant, and digest verification requires a conformant implementation.",
                )
            } else {
                // digestChecked tracks whether a byte-level comparison was actually performed so
                // that "digest mismatch" is only added when the digest was computed and found
                // unequal — not when an earlier exception already added a more specific error.
                var digestChecked = false
                val digestOk =
                    try {
                        val computedDigest = canonicalizationService.computeDigest(document)
                        val computedBytes = decodeMultibaseDigest(computedDigest)
                        val expectedBytes = decodeMultibaseDigest(expectedDigest)
                        digestChecked = true
                        java.security.MessageDigest.isEqual(computedBytes, expectedBytes)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: IllegalArgumentException) {
                        // An unsupported multibase prefix means we cannot decode the digest.
                        // Fail closed: do not attempt a comparison on raw encoded strings.
                        errors.add("Document digest uses unsupported encoding: ${e.message}")
                        false
                    } catch (e: Exception) {
                        // computeDigest (or any other step) threw unexpectedly — record the failure
                        // rather than propagating an exception out of verifyDocument.
                        errors.add("Document digest computation failed: ${e.message}")
                        false
                    }
                // Only add "digest mismatch" when the comparison was actually performed and failed.
                // When digestChecked is false an error was already recorded above.
                if (digestChecked && !digestOk) errors.add("Document digest mismatch")
                if (digestChecked && digestOk) integrityVerified = true
            }
        } ?: warnings.add(
            "No digest was supplied in the resolution metadata: document integrity was NOT verified " +
                "(valid=true only means the structural checks passed).",
        )

        return VerificationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            integrityVerified = integrityVerified,
        )
    }

    override suspend fun verifyDocumentSignature(
        document: DidDocument,
        method: String,
    ): Boolean {
        if (!document.id.value.startsWith("did:$method:")) {
            logger.warn(
                "verifyDocumentSignature called with method '$method' but document id is " +
                    "'${document.id.value}' — returning false",
            )
            return false
        }
        // Method-specific signature verification
        return when (method) {
            "ion" -> verifyIonDocumentSignature(document)
            "key" -> verifyKeyDocumentSignature(document)
            else -> false // Unknown DID method — cannot verify, fail-closed
        }
    }

    /**
     * Verifies [signature] over [data] with the key of [method].
     *
     * Supported: Ed25519 keys, as `Ed25519VerificationKey2020` / `Ed25519VerificationKey2018` /
     * `Multikey` (`publicKeyMultibase`, with or without the `0xed01` multicodec prefix) or as an
     * OKP/Ed25519 JWK (`JsonWebKey2020`, or any type carrying such a JWK). The signature is the
     * raw 64-byte Ed25519 signature, encoded as multibase base58btc (`z…`), multibase base64url
     * (`u…`), base64url, base64 or hex.
     *
     * Every other key type, an undecodable key or signature, and a wrong signature return `false`.
     */
    override suspend fun verifyVerificationMethod(
        method: org.trustweave.did.model.VerificationMethod,
        signature: String,
        data: ByteArray,
    ): Boolean {
        val publicKey = Ed25519Verifier.publicKeyOf(method)
        if (publicKey == null) {
            logger.warn("verifyVerificationMethod: unsupported or unreadable key for ${method.id} (type ${method.type})")
            return false
        }
        val signatureBytes = Ed25519Verifier.decodeSignature(signature) ?: return false
        return Ed25519Verifier.verify(publicKey, signatureBytes, data)
    }

    private fun verifyVerificationMethodStructure(method: org.trustweave.did.model.VerificationMethod): Boolean =
        method.id.value.isNotEmpty() &&
            method.type.isNotEmpty() &&
            (method.publicKeyJwk != null || method.publicKeyMultibase != null)

    private fun verifyServiceStructure(service: org.trustweave.did.model.DidService): Boolean =
        service.id.isNotEmpty() && service.type.isNotEmpty()

    /**
     * Always `false`: verifying a did:ion document means replaying its Sidetree operation chain
     * (create/update/recover/deactivate, with the commitment/reveal hash checks and each
     * operation's JWS) against anchored batch files, which requires the ION node data this
     * module does not have. That is not implemented, so the document is reported as *not
     * verified* (fail-closed), never as verified, and the reason is logged.
     */
    private suspend fun verifyIonDocumentSignature(document: DidDocument): Boolean {
        logger.warn(
            "verifyDocumentSignature: did:ion proof-chain verification is not implemented; " +
                "${document.id.value} is reported as NOT verified",
        )
        return false
    }

    /** did:key *self-certification* (identifier equals key); no signature is verified. */
    private suspend fun verifyKeyDocumentSignature(document: DidDocument): Boolean {
        // did:key is self-certifying: the DID encodes the public key, so any alteration
        // to the verification method would produce a different DID. Verify by checking
        // that at least one verification method's publicKeyMultibase matches the DID identifier
        // AND that the verification method's controller matches the document's own DID.
        val multibaseFromDid =
            document.id.value
                .substringAfter("did:key:", "")
                .substringBefore("?")
                .substringBefore("#")
        if (multibaseFromDid.isEmpty()) return false
        // did:key is self-certifying: exactly one VM controlled by the DID itself must exist,
        // and its public key must match the key encoded in the DID. Allowing any() would
        // permit an attacker to inject additional VMs that pass the check.
        val matchingVms =
            document.verificationMethod.filter { vm ->
                vm.controller.value == document.id.value
            }
        if (matchingVms.isEmpty()) return false
        // All matching VMs have null publicKeyMultibase — JWK-only key, cannot verify via multibase
        if (matchingVms.all { it.publicKeyMultibase == null }) {
            logger.warn(
                "verifyKeyDocumentSignature: all VMs for ${document.id.value} use JWK-only encoding; " +
                    "multibase self-certification check cannot be performed — returning false",
            )
            return false
        }
        // Self-certification only: the identifier matches the key. No signature is checked.
        return matchingVms.size == 1 && matchingVms.first().publicKeyMultibase == multibaseFromDid
    }

    /**
     * Strict hex: only `[0-9a-fA-F]`. `String.toInt(16)` would also accept `+1` and `-1`
     * (a signed value that silently becomes a different byte), so it is not used on its own.
     */
    private fun decodeStrictHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex digest must have even number of digits, got ${hex.length}" }
        require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Hex digest contains non-hex characters" }
        return ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }

    /**
     * Decodes a multibase-encoded digest string to raw bytes.
     *
     * Supports:
     * - 'u' prefix: base64url without padding (RFC 4648 §5)
     * - 'U' prefix: base64url with padding
     * - 'f' prefix: lowercase hex
     * - 'F' prefix: uppercase hex
     *
     * @throws IllegalArgumentException for unsupported multibase prefixes
     */
    private fun decodeMultibaseDigest(encoded: String): ByteArray {
        require(encoded.isNotEmpty()) { "Digest string is empty" }
        return when (encoded[0]) {
            'u' -> {
                // base64url without padding: JDK's getUrlDecoder() requires canonical padding,
                // so strip any accidental trailing '=' then add the correct amount.
                val s = encoded.substring(1).trimEnd('=')
                val padded = s + "=".repeat((4 - s.length % 4) % 4)
                java.util.Base64
                    .getUrlDecoder()
                    .decode(padded)
            }
            'U' -> {
                // base64url with padding: JDK's getUrlDecoder() does NOT accept '=' padding
                // per the JDK Javadoc, so normalise by stripping existing padding and re-adding
                // the canonical amount.
                val s = encoded.substring(1).trimEnd('=')
                val padded = s + "=".repeat((4 - s.length % 4) % 4)
                java.util.Base64
                    .getUrlDecoder()
                    .decode(padded)
            }
            'f', 'F' -> decodeStrictHex(encoded.substring(1))
            else -> throw IllegalArgumentException("Unsupported multibase prefix: ${encoded[0]}")
        }
    }
}

/**
 * Ed25519 signature verification through the JDK's EdDSA provider (JDK 15+).
 */
internal object Ed25519Verifier {
    private const val KEY_SIZE = 32
    private const val SIGNATURE_SIZE = 64

    /** DER prefix of an X.509 SubjectPublicKeyInfo for an Ed25519 key (OID 1.3.101.112). */
    private val X509_PREFIX =
        byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

    private val ED25519_TYPES = setOf("Ed25519VerificationKey2020", "Ed25519VerificationKey2018", "Multikey")

    /** The raw 32-byte Ed25519 public key of [method], or null if it has none. */
    fun publicKeyOf(method: org.trustweave.did.model.VerificationMethod): ByteArray? {
        method.publicKeyJwk?.let { jwk ->
            if (jwk["kty"] == "OKP" && jwk["crv"] == "Ed25519") {
                val x = jwk["x"] as? String ?: return null
                return decodeBase64Url(x)?.takeIf { it.size == KEY_SIZE }
            }
        }
        if (method.type !in ED25519_TYPES) return null
        val multibase = method.publicKeyMultibase ?: return null
        if (!multibase.startsWith("z")) return null
        val bytes =
            try {
                multibase.substring(1).decodeBase58()
            } catch (e: IllegalArgumentException) {
                return null
            }
        return when {
            bytes.size == KEY_SIZE + 2 && bytes[0] == 0xed.toByte() && bytes[1] == 0x01.toByte() ->
                bytes.copyOfRange(2, bytes.size)
            // Multikey always carries the multicodec prefix; the older types may hold the raw key.
            bytes.size == KEY_SIZE && method.type != "Multikey" -> bytes
            else -> null
        }
    }

    /** Decodes a 64-byte signature from the encodings listed on verifyVerificationMethod. */
    fun decodeSignature(signature: String): ByteArray? {
        if (signature.isEmpty()) return null
        val candidates =
            sequence<() -> ByteArray?> {
                if (signature.startsWith("z")) yield { signature.substring(1).decodeBase58() }
                if (signature.startsWith("u")) yield { decodeBase64Url(signature.substring(1)) }
                yield { decodeBase64Url(signature) }
                yield {
                    java.util.Base64
                        .getDecoder()
                        .decode(signature)
                }
                if (signature.length == SIGNATURE_SIZE * 2) yield { decodeHex(signature) }
            }
        return candidates
            .mapNotNull { decode ->
                try {
                    decode()
                } catch (e: IllegalArgumentException) {
                    null
                }
            }.firstOrNull { it.size == SIGNATURE_SIZE }
    }

    fun verify(
        publicKey: ByteArray,
        signature: ByteArray,
        data: ByteArray,
    ): Boolean =
        try {
            val key =
                java.security.KeyFactory
                    .getInstance("Ed25519")
                    .generatePublic(java.security.spec.X509EncodedKeySpec(X509_PREFIX + publicKey))
            java.security.Signature.getInstance("Ed25519").run {
                initVerify(key)
                update(data)
                verify(signature)
            }
        } catch (e: java.security.GeneralSecurityException) {
            false
        }

    private fun decodeBase64Url(value: String): ByteArray? =
        try {
            java.util.Base64
                .getUrlDecoder()
                .decode(value.trimEnd('='))
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun decodeHex(value: String): ByteArray? {
        if (value.length % 2 != 0 || !value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
