package org.trustweave.credential.internal

import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.model.VerificationMethod
import org.trustweave.credential.proof.internal.engines.ProofEngineUtils
import com.nimbusds.jwt.SignedJWT
import kotlinx.serialization.json.*
import java.util.Base64
import java.security.PublicKey
import kotlin.time.Duration.Companion.minutes
import org.slf4j.LoggerFactory

// Helper: extract a String field from the proof's additionalProperties (LinkedDataProof),
// from the Key Binding JWT (SdJwtVcProof: challenge -> "nonce", domain -> "aud"),
// or fall back to null for other proof types.
private fun CredentialProof?.proofStringField(field: String): String? = when (this) {
    is CredentialProof.LinkedDataProof ->
        additionalProperties[field]?.let { (it as? JsonPrimitive)?.contentOrNull }
    is CredentialProof.SdJwtVcProof ->
        PresentationVerification.kbJwtBoundField(sdJwtVc, field)
    else -> null
}

/**
 * Presentation verification utilities.
 * 
 * Extracted from DefaultCredentialService to improve maintainability and testability.
 */
internal object PresentationVerification {

    private val logger = LoggerFactory.getLogger(PresentationVerification::class.java)

    /**
     * Key under [VerificationOptions.additionalOptions] carrying the maximum accepted age
     * of an SD-JWT Key Binding JWT, as a [kotlin.time.Duration].
     *
     * A KB-JWT proves *fresh* possession of the holder's key; an arbitrarily old `iat`
     * would let a captured presentation be replayed indefinitely (subject only to the
     * nonce policy of the verifier). When the option is absent,
     * [DEFAULT_KB_JWT_MAX_AGE] applies. The verifier's `clockSkewTolerance` is added on
     * top of the max age.
     *
     * Example: `VerificationOptions(additionalOptions = mapOf("kbJwtMaxAge" to 5.minutes))`
     */
    const val KB_JWT_MAX_AGE_OPTION = "kbJwtMaxAge"

    /** Default maximum accepted age of a KB-JWT `iat` (see [KB_JWT_MAX_AGE_OPTION]). */
    val DEFAULT_KB_JWT_MAX_AGE: kotlin.time.Duration = 10.minutes

    /**
     * Verify challenge if required.
     * 
     * @param presentation The presentation to verify
     * @param options Verification options
     * @return VerificationResult.Invalid if challenge verification fails, null if valid
     */
    fun verifyChallenge(
        presentation: VerifiablePresentation,
        options: VerificationOptions
    ): VerificationResult.Invalid.InvalidProof? {
        if (!options.verifyChallenge) {
            return null
        }

        if (options.expectedChallenge == null) {
            return VerificationResult.Invalid.InvalidProof(
                credential = presentation.verifiableCredential.first(),
                reason = "verifyChallenge is enabled but no expectedChallenge was provided",
                errors = listOf("verifyChallenge requires expectedChallenge to be set")
            )
        }

        // Per W3C VP spec, challenge must be inside the proof, not the outer envelope.
        val proofChallenge = presentation.proof.proofStringField("challenge")
        if (proofChallenge != options.expectedChallenge) {
            return VerificationResult.Invalid.InvalidProof(
                credential = presentation.verifiableCredential.first(),
                reason = "Challenge mismatch",
                errors = listOf(
                    "Expected challenge '${options.expectedChallenge}', " +
                    "but got '$proofChallenge'"
                )
            )
        }

        return null
    }
    
    /**
     * Verify domain if required.
     * 
     * @param presentation The presentation to verify
     * @param options Verification options
     * @return VerificationResult.Invalid if domain verification fails, null if valid
     */
    fun verifyDomain(
        presentation: VerifiablePresentation,
        options: VerificationOptions
    ): VerificationResult.Invalid.InvalidProof? {
        if (!options.verifyDomain) {
            return null
        }

        if (options.expectedDomain == null) {
            return VerificationResult.Invalid.InvalidProof(
                credential = presentation.verifiableCredential.first(),
                reason = "verifyDomain is enabled but no expectedDomain was provided",
                errors = listOf("verifyDomain requires expectedDomain to be set")
            )
        }

        // Per W3C VP spec, domain must be inside the proof, not the outer envelope.
        val proofDomain = presentation.proof.proofStringField("domain")
        if (proofDomain != options.expectedDomain) {
            return VerificationResult.Invalid.InvalidProof(
                credential = presentation.verifiableCredential.first(),
                reason = "Domain mismatch",
                errors = listOf(
                    "Expected domain '${options.expectedDomain}', " +
                    "but got '$proofDomain'"
                )
            )
        }

        return null
    }
    
    /**
     * Verify presentation proof format is supported.
     * 
     * @param proofFormat The proof format
     * @param engines Available proof engines
     * @param presentation The presentation (for error context)
     * @return VerificationResult.Invalid if format not supported, null if supported
     */
    fun verifyProofFormatSupported(
        proofFormat: ProofSuiteId,
        engines: Map<ProofSuiteId, ProofEngine>,
        presentation: VerifiablePresentation
    ): VerificationResult.Invalid.UnsupportedFormat? {
        if (engines[proofFormat] == null) {
            return VerificationResult.Invalid.UnsupportedFormat(
                credential = presentation.verifiableCredential.first(),
                format = proofFormat,
                errors = listOf(
                    "Presentation proof format '${proofFormat.value}' is not supported. " +
                    "Supported formats: ${engines.keys.map { it.value }}"
                )
            )
        }
        return null
    }
    
    /**
     * Resolve verification method for presentation proof.
     *
     * Enforces proof purpose for presentations:
     * 1. The proof's declared `proofPurpose` must be `authentication`.
     * 2. The verification method must be referenced in the `authentication` relationship
     *    array of the holder's DID document (a key listed only under, e.g., `keyAgreement`
     *    is rejected).
     *
     * @param holderIri The holder IRI
     * @param verificationMethodId The verification method ID
     * @param didResolver DID resolver
     * @param declaredProofPurpose The `proofPurpose` declared by the presentation proof
     * @return VerificationMethod if resolved and authorized, null otherwise
     */
    suspend fun resolvePresentationProofVerificationMethod(
        holderIri: Iri,
        verificationMethodId: String,
        didResolver: DidResolver,
        declaredProofPurpose: String
    ): VerificationMethod? {
        if (!holderIri.isDid) {
            return null
        }

        val expectedPurpose = CredentialConstants.ProofPurposes.AUTHENTICATION
        if (declaredProofPurpose != expectedPurpose) {
            logger.warn(
                "Presentation proof purpose '{}' rejected; expected '{}'",
                declaredProofPurpose, expectedPurpose
            )
            return null
        }

        return ProofEngineUtils.resolveVerificationMethod(
            issuerIri = holderIri,
            verificationMethodId = verificationMethodId,
            didResolver = didResolver,
            expectedProofPurpose = expectedPurpose
        )
    }

    /**
     * Verify presentation signature.
     *
     * Validates the cryptographic signature on a verifiable presentation.
     * Currently only supports Ed25519Signature2020 proof suite.
     *
     * Per the W3C Data Integrity specification, the verified payload is
     * `SHA-256(canonical proof options) || SHA-256(canonical presentation document)`,
     * where the proof options are the proof node without `proofValue`/`jws`. This binds
     * `challenge`, `domain`, `created`, `proofPurpose` and `verificationMethod` to the
     * signature, so a captured presentation cannot be replayed with a rewritten challenge.
     *
     * **Security Considerations:**
     * - Fail-closed: any canonicalization error yields `false`
     * - Uses constant-time operations where possible (Java Signature API)
     * - Canonicalization enforces document size limits (DoS protection)
     *
     * @param vpDocument The presentation document as JSON (without the proof node)
     * @param proof The presentation's Linked Data Proof
     * @param verificationMethod Verification method containing the public key
     * @return true if signature is valid, false otherwise
     */
    fun verifyPresentationSignature(
        vpDocument: JsonObject,
        proof: CredentialProof.LinkedDataProof,
        verificationMethod: VerificationMethod
    ): Boolean {
        // Input validation
        if (proof.proofValue.isBlank()) {
            return false
        }

        // Only support Ed25519Signature2020 for now
        if (proof.type != CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020) {
            return false
        }

        val payload = try {
            val canonical = JsonLdUtils.canonicalizeDocument(vpDocument)
            val proofOptionsDocument = ProofEngineUtils.buildProofOptionsDocument(
                context = extractContexts(vpDocument),
                proofType = proof.type,
                created = proof.created.toString(),
                verificationMethod = proof.verificationMethod,
                proofPurpose = proof.proofPurpose,
                additionalProperties = proof.additionalProperties
            )
            val canonicalProofOptions = JsonLdUtils.canonicalizeDocument(proofOptionsDocument)
            ProofEngineUtils.composeDataIntegrityPayload(canonicalProofOptions, canonical)
        } catch (e: Exception) {
            // Fail closed: a presentation that cannot be canonicalized cannot be verified.
            logger.warn("Presentation canonicalization failed during proof verification: {}", e.message)
            return false
        }

        return try {
            // Extract public key from verification method
            val publicKey = ProofEngineUtils.extractPublicKey(verificationMethod) ?: return false

            // Decode signature (base64url)
            val signatureBytes = try {
                Base64.getUrlDecoder().decode(proof.proofValue)
            } catch (e: IllegalArgumentException) {
                // Invalid base64url encoding
                return false
            } catch (e: Exception) {
                // Other decoding errors
                return false
            }

            // Validate signature length (Ed25519 signatures are always 64 bytes)
            if (signatureBytes.size != SecurityConstants.ED25519_SIGNATURE_LENGTH_BYTES) {
                return false
            }

            // Verify Ed25519 signature using Java Security API
            // Note: Java's Signature API uses constant-time operations internally
            val signature = java.security.Signature.getInstance("Ed25519")
            signature.initVerify(publicKey)
            signature.update(payload)
            signature.verify(signatureBytes)
        } catch (e: java.security.InvalidKeyException) {
            // Invalid public key format
            false
        } catch (e: java.security.SignatureException) {
            // Signature verification failure
            false
        } catch (e: Exception) {
            // Other errors (e.g., unsupported algorithm)
            false
        }
    }

    // ---------------------------------------------------------------------------------
    // SD-JWT-VC Key Binding JWT (KB-JWT) verification
    // ---------------------------------------------------------------------------------

    /**
     * Extract the Key Binding JWT from a compact SD-JWT
     * (`<Issuer-signed JWT>~<Disclosure 1>~...~<KB-JWT>`), or null when absent.
     */
    fun extractKbJwt(compactSdJwt: String): String? {
        if (!compactSdJwt.contains('~')) return null
        return compactSdJwt.substringAfterLast('~').takeIf { it.isNotBlank() }
    }

    /**
     * Read a presentation-binding field from the KB-JWT of a compact SD-JWT.
     *
     * Maps the generic field names used by [verifyChallenge]/[verifyDomain] onto the
     * KB-JWT claim names mirrored from creation: `challenge` -> `nonce`, `domain` -> `aud`.
     *
     * Note: callers must verify the KB-JWT signature first ([verifySdJwtKeyBinding]);
     * this helper only reads claims.
     */
    fun kbJwtBoundField(compactSdJwt: String, field: String): String? {
        val kbJwtString = extractKbJwt(compactSdJwt) ?: return null
        return try {
            val claims = SignedJWT.parse(kbJwtString).jwtClaimsSet
            when (field) {
                "challenge" -> claims.getStringClaim("nonce")
                "domain" -> claims.audience?.firstOrNull()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verify the Key Binding JWT of an SD-JWT-VC presentation.
     *
     * Mirrors what `SdJwtProofEngine.buildKbJwt` produces:
     * - header: `alg=EdDSA`, `typ=kb+jwt`, `kid` referencing the holder's key
     * - claims: `iat`, `nonce` (the challenge), `sd_hash` (SHA-256 over the presented
     *   compact SD-JWT without the KB-JWT), optional `aud` (the domain)
     *
     * Checks performed here (fail-closed):
     * 1. KB-JWT present and parseable, with `typ` of `kb+jwt`
     * 2. Signature verifies against a key in the HOLDER's DID document that is
     *    authorized for the `authentication` verification relationship
     * 3. `sd_hash` matches the presented token (binds the KB-JWT to exactly these
     *    disclosures — no mix-and-match)
     * 4. `iat` present and not in the future beyond the clock-skew tolerance
     *
     * `nonce`/`aud` are checked against the verifier's expected challenge/domain by
     * [verifyChallenge]/[verifyDomain] via [kbJwtBoundField] (options-driven).
     *
     * @return null when the key binding is valid, otherwise the failure result
     */
    suspend fun verifySdJwtKeyBinding(
        presentation: VerifiablePresentation,
        proof: CredentialProof.SdJwtVcProof,
        options: VerificationOptions,
        didResolver: DidResolver
    ): VerificationResult.Invalid.InvalidProof? {
        val firstCredential = presentation.verifiableCredential.first()
        val compactSdJwt = proof.sdJwtVc

        val kbJwtString = extractKbJwt(compactSdJwt)
            ?: return VerificationResult.Invalid.InvalidProof(
                credential = firstCredential,
                reason = "SD-JWT-VC presentation proof is missing the Key Binding JWT",
                errors = listOf(
                    "Presentation proof verification requires a KB-JWT appended to the compact SD-JWT"
                )
            )

        val holderIri = presentation.holder
        if (!holderIri.isDid) {
            return VerificationResult.Invalid.InvalidProof(
                credential = firstCredential,
                reason = "Presentation holder '${holderIri.value}' is not a DID",
                errors = listOf("Non-DID holder IRI cannot be verified: ${holderIri.value}")
            )
        }

        val kbJwt = try {
            SignedJWT.parse(kbJwtString)
        } catch (e: Exception) {
            return VerificationResult.Invalid.InvalidProof(
                credential = firstCredential,
                reason = "Key Binding JWT could not be parsed",
                errors = listOf("Malformed KB-JWT: ${e.message}")
            )
        }

        if (kbJwt.header.type?.toString() != "kb+jwt") {
            return VerificationResult.Invalid.InvalidProof(
                credential = firstCredential,
                reason = "Key Binding JWT 'typ' header must be 'kb+jwt'",
                errors = listOf("Unexpected KB-JWT typ: ${kbJwt.header.type}")
            )
        }

        // The KB-JWT must be signed by a key of the declared HOLDER that is authorized
        // for authentication — this is the holder-binding guarantee.
        val verificationMethod = ProofEngineUtils.resolveVerificationMethod(
            issuerIri = holderIri,
            verificationMethodId = kbJwt.header.keyID,
            didResolver = didResolver,
            expectedProofPurpose = CredentialConstants.ProofPurposes.AUTHENTICATION
        ) ?: return VerificationResult.Invalid.InvalidProof(
            credential = firstCredential,
            reason = "Could not resolve a holder verification method for the KB-JWT key " +
                "'${kbJwt.header.keyID}', or the key is not authorized for 'authentication' " +
                "on holder '${holderIri.value}'",
            errors = listOf("KB-JWT key does not belong to the presentation holder")
        )

        if (!ProofEngineUtils.verifyEd25519Jws(kbJwt, verificationMethod)) {
            return VerificationResult.Invalid.InvalidProof(
                credential = firstCredential,
                reason = "Key Binding JWT signature verification failed",
                errors = listOf("Invalid signature on KB-JWT")
            )
        }

        val claims = kbJwt.jwtClaimsSet

        // sd_hash binds the KB-JWT to exactly the presented disclosures.
        val presentedPart = compactSdJwt.substringBeforeLast("~") + "~"
        val expectedSdHash = Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(presentedPart.toByteArray(Charsets.UTF_8))
        )
        val sdHash = try {
            claims.getStringClaim("sd_hash")
        } catch (e: Exception) {
            null
        }
        if (sdHash == null || sdHash != expectedSdHash) {
            return VerificationResult.Invalid.InvalidProof(
                credential = firstCredential,
                reason = "Key Binding JWT sd_hash does not match the presented SD-JWT",
                errors = listOf("KB-JWT sd_hash mismatch (disclosures may have been altered)")
            )
        }

        // iat must be present and not in the future (beyond clock-skew tolerance).
        val issuedAt = claims.issueTime
            ?: return VerificationResult.Invalid.InvalidProof(
                credential = firstCredential,
                reason = "Key Binding JWT is missing the 'iat' claim",
                errors = listOf("KB-JWT iat is required")
            )
        val iatInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(issuedAt.time)
        val now = kotlinx.datetime.Clock.System.now()
        if (iatInstant > now.plus(options.clockSkewTolerance)) {
            return VerificationResult.Invalid.InvalidProof(
                credential = firstCredential,
                reason = "Key Binding JWT 'iat' is in the future",
                errors = listOf(
                    "KB-JWT iat $iatInstant is later than $now plus ${options.clockSkewTolerance} skew"
                )
            )
        }

        // iat must also be fresh: an old-but-valid KB-JWT is a replay vector. The max age
        // is configurable via the KB_JWT_MAX_AGE_OPTION additional option (default
        // DEFAULT_KB_JWT_MAX_AGE), with clockSkewTolerance added on top.
        val maxAge = kbJwtMaxAge(options)
        if (iatInstant < now.minus(maxAge).minus(options.clockSkewTolerance)) {
            return VerificationResult.Invalid.InvalidProof(
                credential = firstCredential,
                reason = "Key Binding JWT 'iat' is too old (max age $maxAge)",
                errors = listOf(
                    "KB-JWT iat $iatInstant is older than $now minus $maxAge max age " +
                        "and ${options.clockSkewTolerance} skew; the presentation may be a replay. " +
                        "Verifiers can tune this via VerificationOptions.additionalOptions" +
                        "[\"$KB_JWT_MAX_AGE_OPTION\"]"
                )
            )
        }

        return null
    }

    /**
     * Resolve the effective KB-JWT max age from [options]: the
     * [KB_JWT_MAX_AGE_OPTION] additional option when it carries a positive
     * [kotlin.time.Duration], otherwise [DEFAULT_KB_JWT_MAX_AGE].
     *
     * A present-but-invalid value (wrong type, or zero/negative) throws instead of
     * silently falling back: a verifier intending a stricter policy must not be
     * weakened to the default without noticing.
     */
    internal fun kbJwtMaxAge(options: VerificationOptions): kotlin.time.Duration {
        val raw = options.additionalOptions[KB_JWT_MAX_AGE_OPTION] ?: return DEFAULT_KB_JWT_MAX_AGE
        val configured = raw as? kotlin.time.Duration
            ?: throw IllegalArgumentException(
                "Verification option '$KB_JWT_MAX_AGE_OPTION' must be a kotlin.time.Duration, " +
                    "got ${raw::class.simpleName}"
            )
        require(configured.isPositive()) {
            "Verification option '$KB_JWT_MAX_AGE_OPTION' must be positive, got $configured"
        }
        return configured
    }

    /**
     * Holder binding for presentation proofs: the proof's `verificationMethod` must belong
     * to the declared holder DID — exact DID equality of the pre-fragment part.
     *
     * A prefix comparison is NOT sufficient: `did:example:abc` must not be satisfied by
     * `did:example:abcdef#key-1`.
     */
    fun verificationMethodBelongsToHolder(verificationMethod: String, holderDid: String): Boolean =
        verificationMethod.substringBefore('#') == holderDid

    /**
     * Extract the `@context` list from a presentation document, defaulting to the W3C VC
     * base context when absent.
     */
    private fun extractContexts(vpDocument: JsonObject): List<String> {
        return when (val context = vpDocument["@context"]) {
            is JsonArray -> context.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(context.contentOrNull)
            else -> listOf(CredentialConstants.VcContexts.VC_1_1)
        }
    }
}

