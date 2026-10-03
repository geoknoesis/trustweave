package org.trustweave.credential.proof.internal.engines

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.internal.RevocationChecker
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.credential.spi.status.CredentialStatusChecker
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.UUID
import java.time.Instant as JavaInstant

/**
 * SD-JWT-VC (Selective Disclosure JWT Verifiable Credential) proof engine.
 *
 * Implements the IETF SD-JWT-VC specification (draft-ietf-oauth-sd-jwt-vc) with full
 * selective disclosure support. Each credential subject claim is individually disclosable:
 *
 * - **Issue**: Claims are replaced by `_sd` hashes in the JWT; the raw disclosure strings
 *   are stored in [CredentialProof.SdJwtVcProof.disclosures]. When the credential subject
 *   id is a DID, a `cnf` claim (RFC 7800, kid-style) binds the credential to that holder
 *   DID for presentation-time key binding.
 * - **Verify**: Disclosure hashes are verified against the `_sd` array in the JWT claims.
 * - **Present**: Holder selects which disclosures to reveal; a Key Binding JWT (KB-JWT)
 *   is appended when a `challenge` is provided in [ProofOptions].
 *
 * Compact format: `<Issuer-signed JWT>~<Disclosure 1>~...~<Disclosure N>~[<KB-JWT>]`
 */
internal class SdJwtProofEngine(
    private val config: ProofEngineConfig = ProofEngineConfig(),
) : ProofEngine {
    override val format = ProofSuiteId.SD_JWT_VC
    override val formatName = "SD-JWT-VC"
    override val formatVersion = "draft-ietf-oauth-sd-jwt-vc-04"

    override val capabilities =
        ProofEngineCapabilities(
            selectiveDisclosure = true,
            zeroKnowledge = false,
            revocation = true,
            presentation = true,
            predicates = false,
        )

    private val b64url = Base64.getUrlEncoder().withoutPadding()
    private val b64urlDec = Base64.getUrlDecoder()
    private val random = SecureRandom()

    // -------------------------------------------------------------------------
    // Issue
    // -------------------------------------------------------------------------

    override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
        require(request.format == format) {
            "Request format ${request.format.value} does not match engine format ${format.value}"
        }

        val issuerIri =
            request.issuer.let { issuer ->
                when (issuer) {
                    is Issuer.IriIssuer -> issuer.id
                    is Issuer.ObjectIssuer -> issuer.id
                }
            }

        val keyId =
            ProofEngineUtils.extractKeyId(request.issuerKeyId?.value)
                ?: throw IllegalArgumentException("issuerKeyId is required for SD-JWT-VC signing")

        // Build per-claim disclosures
        val disclosures = mutableListOf<String>()
        val sdHashes = mutableListOf<String>()

        for ((claimName, claimValue) in request.credentialSubject.claims) {
            val (discB64, hashB64) = createDisclosure(claimName, claimValue)
            disclosures.add(discB64)
            sdHashes.add(hashB64)
        }

        val now = JavaInstant.now()
        val claimsBuilder =
            JWTClaimsSet
                .Builder()
                .issuer(issuerIri.value)
                .subject(request.credentialSubject.id?.value ?: "")
                .issueTime(Date.from(now))
                .claim("_sd_alg", "sha-256")
                .claim(
                    "vct",
                    request.type.firstOrNull { it.value != "VerifiableCredential" }?.value
                        ?: "VerifiableCredential",
                ).claim("vc", buildVcClaim(request, sdHashes))

        request.validUntil?.let {
            claimsBuilder.expirationTime(Date.from(JavaInstant.ofEpochSecond(it.epochSeconds)))
        }

        // Holder binding (RFC 7800 / SD-JWT VC): when the issuance request identifies the
        // holder — a DID-valued credentialSubject.id — embed a `cnf` claim with kid-style
        // binding to that DID. Presentation verification then REQUIRES the Key Binding JWT
        // to be signed by an authentication-authorized key of this DID, regardless of what
        // the (unsigned, attacker-rewritable) presentation envelope claims the holder is.
        // When no holder DID is known (subject id absent or not a DID), `cnf` is omitted
        // and presentations of the credential fall back to the weaker envelope-holder
        // binding (see PresentationVerification.verifySdJwtKeyBinding). JWK-style `cnf`
        // binding is not emitted: issuance requests carry no holder JWK.
        request.credentialSubject.id?.takeIf { it.isDid }?.let { holder ->
            claimsBuilder.claim("cnf", mapOf("kid" to holder.value))
        }

        // `typ` marks the token as an SD-JWT VC (draft-ietf-oauth-sd-jwt-vc); verifiers refuse
        // an issuer JWT whose typ is anything else, so a JWT minted for another purpose by the
        // same key cannot be replayed as a credential.
        val header =
            JWSHeader
                .Builder(JWSAlgorithm.EdDSA)
                .type(com.nimbusds.jose.JOSEObjectType(SD_JWT_TYP_DC))
                .keyID(keyId)
                .build()
        val signerFn =
            getSignerFunctionOrKms()
                ?: throw IllegalArgumentException(
                    "No signer available for key $keyId. Configure KMS via ProofEngineConfig.",
                )
        val compactJwt = signJws(header, claimsBuilder.build(), keyId, signerFn)

        val proof =
            CredentialProof.SdJwtVcProof(
                sdJwtVc = compactJwt,
                disclosures = disclosures.toList(),
            )

        return VerifiableCredential(
            id = request.id ?: CredentialId("urn:uuid:${UUID.randomUUID()}"),
            type = request.type,
            issuer = request.issuer,
            issuanceDate = Instant.fromEpochSeconds(now.epochSecond, now.nano),
            validFrom = request.validFrom,
            credentialSubject = request.credentialSubject,
            expirationDate = request.validUntil,
            credentialStatus = request.credentialStatus,
            credentialSchema = request.credentialSchema,
            evidence = request.evidence,
            proof = proof,
        )
    }

    // -------------------------------------------------------------------------
    // Verify
    // -------------------------------------------------------------------------

    override suspend fun verify(
        credential: VerifiableCredential,
        options: VerificationOptions,
    ): VerificationResult {
        val proof =
            credential.proof as? CredentialProof.SdJwtVcProof
                ?: return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "SD-JWT-VC credential must have SdJwtVcProof",
                    errors = listOf("Expected SdJwtVcProof but got ${credential.proof?.javaClass?.simpleName}"),
                    warnings = emptyList(),
                )

        return try {
            // The sdJwtVc field may carry the full compact SD-JWT
            // (`<JWT>~<Disclosure 1>~...~[<KB-JWT>]`, e.g. after presentation); the
            // issuer-signed JWT is always the first '~'-separated segment.
            val signedJWT = SignedJWT.parse(proof.sdJwtVc.substringBefore("~"))
            checkIssuerJwtType(credential, signedJWT, options)?.let { return it }
            val issuerIri =
                when (val issuer = credential.issuer) {
                    is Issuer.IriIssuer -> issuer.id
                    is Issuer.ObjectIssuer -> issuer.id
                }

            val issuerVerificationMethod =
                getIssuerVerificationMethod(issuerIri, proof.sdJwtVc)
                    ?: return VerificationResult.Invalid.InvalidIssuer(
                        credential = credential,
                        issuerIri = issuerIri,
                        reason = "Could not resolve issuer or get verification key",
                        errors = listOf("Failed to resolve issuer: ${issuerIri.value}"),
                        warnings = emptyList(),
                    )

            if (!ProofEngineUtils.verifyEd25519Jws(signedJWT, issuerVerificationMethod)) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "JWT signature verification failed",
                    errors = listOf("Invalid signature on SD-JWT-VC"),
                    warnings = emptyList(),
                )
            }

            val claimsSet = signedJWT.jwtClaimsSet

            // Finding 4b: the signed 'iss' claim is authoritative — the unsigned envelope
            // issuer must match it, otherwise the envelope has been re-attributed.
            if (claimsSet.issuer != issuerIri.value) {
                return VerificationResult.Invalid.InvalidIssuer(
                    credential = credential,
                    issuerIri = issuerIri,
                    reason =
                        "Signed 'iss' claim does not match the credential envelope issuer " +
                            "(possible envelope tampering)",
                    errors =
                        listOf(
                            "Signed iss '${claimsSet.issuer}' does not match envelope issuer '${issuerIri.value}'",
                        ),
                    warnings = emptyList(),
                )
            }

            // Finding 4a: signed temporal claims (exp/nbf) are authoritative. The unsigned
            // envelope dates are only metadata and can be stripped or rewritten.
            validateSignedTemporalClaims(credential, claimsSet, options)?.let { return it }

            // _sd_alg: absent means sha-256 (SD-JWT §4.1.1); only sha-256 is supported.
            val sdAlg = claimsSet.getClaim("_sd_alg")
            if (sdAlg != null && sdAlg != SD_ALG_SHA256) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "Unsupported _sd_alg '$sdAlg'",
                    errors = listOf("Only '$SD_ALG_SHA256' is supported for _sd_alg, got '$sdAlg'"),
                    warnings = emptyList(),
                )
            }

            // Resolve disclosures against the signed payload (SD-JWT §7.1): every disclosure
            // must be referenced exactly once, digests and claim names must be unique, and
            // nested `_sd` objects / `{"...": digest}` array elements are resolved recursively.
            val disclosures = proof.disclosures ?: emptyList()
            val processedSubjectClaims =
                try {
                    processSdPayload(claimsSet, disclosures)
                } catch (e: SdJwtDisclosureException) {
                    return VerificationResult.Invalid.InvalidProof(
                        credential = credential,
                        reason = e.message ?: "Invalid SD-JWT disclosures",
                        errors = listOf("Invalid disclosures: ${e.message}"),
                        warnings = emptyList(),
                    )
                }

            // Finding 4c: every envelope credentialSubject claim must be backed by a verified
            // disclosure or a non-selectively-disclosed signed claim — name AND value.
            reconcileEnvelopeClaims(credential, signedJWT, processedSubjectClaims)?.let { return it }

            val subjectIri =
                claimsSet.subject?.takeIf { it.isNotBlank() }?.let { Iri(it) }
                    ?: credential.credentialSubject.id
            val issuedAt: kotlinx.datetime.Instant =
                claimsSet.issueTime
                    ?.toInstant()
                    ?.let { Instant.fromEpochSeconds(it.epochSecond, it.nano) }
                    ?: credential.issuanceDate
                    ?: credential.validFrom
                    ?: kotlinx.datetime.Clock.System
                        .now()
            val expiresAt =
                claimsSet.expirationTime
                    ?.toInstant()
                    ?.let { Instant.fromEpochSeconds(it.epochSecond, it.nano) }
                    ?: credential.expirationDate

            // Revocation / suspension check. An undeterminable status is routed through the
            // configured RevocationFailurePolicy (fail closed by default).
            val statusWarnings = mutableListOf<String>()
            val checker = config.properties["statusChecker"] as? CredentialStatusChecker
            if (checker != null && credential.credentialStatus != null) {
                val outcome =
                    RevocationChecker.checkWithStatusChecker(credential, checker, options.revocationFailurePolicy)
                outcome.failure?.let { return it }
                statusWarnings += outcome.warnings
                when (val status = outcome.status) {
                    is CredentialStatusCheckResult.Revoked -> return VerificationResult.Invalid.Revoked(
                        credential = credential,
                        revokedAt = null,
                        errors = listOf("Credential has been revoked: ${status.reason ?: "no reason provided"}"),
                    )
                    is CredentialStatusCheckResult.Suspended -> return VerificationResult.Invalid.InvalidProof(
                        credential = credential,
                        reason = "Credential is suspended: ${status.reason ?: "no reason provided"}",
                        errors = listOf("Credential suspended"),
                    )
                    else -> {}
                }
            }

            VerificationResult.Valid(
                credential = credential,
                issuerIri = issuerIri,
                subjectIri = subjectIri,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                warnings = statusWarnings,
                formatMetadata =
                    buildJsonObject {
                        put("jwt_id", claimsSet.jwtid ?: "")
                        put("disclosed_claims", disclosures.size)
                        put("_sd_alg", claimsSet.getStringClaim("_sd_alg") ?: "sha-256")
                    },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Failed to parse or verify SD-JWT-VC: ${e.message}",
                errors = listOf("JWT error: ${e.message}"),
                warnings = emptyList(),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Create presentation (selective disclosure + optional KB-JWT)
    // -------------------------------------------------------------------------

    override suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest,
    ): VerifiablePresentation {
        if (credentials.isEmpty()) {
            throw IllegalArgumentException("At least one credential is required for presentation")
        }

        val holder =
            credentials.first().credentialSubject.id
                ?: throw IllegalArgumentException("Cannot create presentation: credential subject has no id")

        // For each credential, filter disclosures to requested claims only
        val presentedCredentials =
            credentials.map { credential ->
                val proof = credential.proof as? CredentialProof.SdJwtVcProof ?: return@map credential
                val allDisclosures = proof.disclosures ?: return@map credential

                val requestedClaims =
                    (
                        request.proofOptions
                            ?.additionalOptions
                            ?.get("disclosedClaims") as? Set<*>
                    )?.filterIsInstance<String>()
                        ?.toSet()

                val selectedDisclosures =
                    if (requestedClaims == null || requestedClaims.isEmpty()) {
                        allDisclosures
                    } else {
                        allDisclosures.filter { discB64 ->
                            parseDisclosureClaimName(discB64) in requestedClaims
                        }
                    }

                // Optionally append KB-JWT if challenge is provided
                val challenge = request.proofOptions?.challenge
                val kbJwt =
                    if (challenge != null) {
                        buildKbJwt(
                            proof = proof,
                            selectedDisclosures = selectedDisclosures,
                            challenge = challenge,
                            audience = request.proofOptions?.domain,
                            holderVerificationMethod = request.proofOptions?.verificationMethod,
                        )
                    } else {
                        null
                    }

                // When only a subset of claims is disclosed, the unsigned envelope must not leak
                // the withheld claims — and verification reconciles envelope claims against the
                // disclosures, so the envelope must only carry the selected claims.
                val presentedSubject =
                    if (requestedClaims.isNullOrEmpty()) {
                        credential.credentialSubject
                    } else {
                        val selectedNames = selectedDisclosures.mapNotNull { parseDisclosureClaimName(it) }.toSet()
                        credential.credentialSubject.copy(
                            claims = credential.credentialSubject.claims.filterKeys { it in selectedNames },
                        )
                    }

                credential.copy(
                    credentialSubject = presentedSubject,
                    proof =
                        CredentialProof
                            .SdJwtVcProof(
                                sdJwtVc = proof.sdJwtVc,
                                disclosures = selectedDisclosures,
                            ).let {
                                // Annotate with KB-JWT via additionalProperties is not possible on SdJwtVcProof
                                // but we can embed it in the sdJwtVc field as the full compound token
                                if (kbJwt != null) {
                                    val compound = buildCompactSdJwt(proof.sdJwtVc, selectedDisclosures, kbJwt)
                                    CredentialProof.SdJwtVcProof(sdJwtVc = compound, disclosures = selectedDisclosures)
                                } else {
                                    it
                                }
                            },
                )
            }

        // Surface the first credential's compact SD-JWT (with KB-JWT appended) as the
        // presentation proof so verifiers can verify holder key binding.
        val presentationProof =
            (presentedCredentials.firstOrNull()?.proof as? CredentialProof.SdJwtVcProof)
                ?.takeIf { it.sdJwtVc.substringAfterLast("~", "").isNotBlank() }

        return VerifiablePresentation(
            type = listOf(CredentialType.Custom("VerifiablePresentation")),
            holder = holder,
            verifiableCredential = presentedCredentials,
            proof = presentationProof,
            challenge = request.proofOptions?.challenge,
            domain = request.proofOptions?.domain,
        )
    }

    override suspend fun initialize(config: ProofEngineConfig) {}

    override suspend fun close() {}

    override fun isReady(): Boolean = true

    // -------------------------------------------------------------------------
    // Disclosure helpers
    // -------------------------------------------------------------------------

    /**
     * Creates an SD-JWT disclosure for a single claim.
     *
     * Returns (disclosureBase64url, hashBase64url).
     *
     * Disclosure format: base64url(`["<salt>", "<name>", <value>]`)
     */
    private fun createDisclosure(
        claimName: String,
        claimValue: JsonElement,
    ): Pair<String, String> {
        val saltBytes = ByteArray(16).also { random.nextBytes(it) }
        val salt = b64url.encodeToString(saltBytes)

        val disclosureJson =
            buildJsonArray {
                add(salt)
                add(claimName)
                add(claimValue)
            }.toString()

        val discB64 = b64url.encodeToString(disclosureJson.toByteArray(Charsets.UTF_8))
        val hashB64 = sha256B64(discB64.toByteArray(Charsets.UTF_8))
        return Pair(discB64, hashB64)
    }

    private fun sha256B64(input: ByteArray): String = b64url.encodeToString(MessageDigest.getInstance("SHA-256").digest(input))

    /** A decoded disclosure: `[salt, name, value]` (object property) or `[salt, value]` (array element). */
    private data class ParsedDisclosure(
        val name: String?,
        val value: JsonElement,
    )

    private class SdJwtDisclosureException(
        message: String,
    ) : Exception(message)

    /**
     * Processes the signed payload with the presented [disclosures] (SD-JWT §7.1) and returns the
     * resulting `vc.credentialSubject` claims (without `id`).
     *
     * @throws SdJwtDisclosureException on a malformed, duplicate, unreferenced or misplaced
     *         disclosure, a duplicate digest, or a disclosed claim name that collides with an
     *         existing one.
     */
    private fun processSdPayload(
        claimsSet: JWTClaimsSet,
        disclosures: List<String>,
    ): Map<String, JsonElement> {
        val byDigest = LinkedHashMap<String, ParsedDisclosure>()
        for (discB64 in disclosures) {
            val digest = sha256B64(discB64.toByteArray(Charsets.US_ASCII))
            val parsed =
                parseDisclosureStrict(discB64)
                    ?: throw SdJwtDisclosureException("Malformed disclosure: not [salt, name, value] or [salt, value]")
            if (byDigest.put(digest, parsed) != null) {
                throw SdJwtDisclosureException("Duplicate disclosure with digest $digest")
            }
        }
        val seenDigests = HashSet<String>()
        val used = HashSet<String>()

        val processor =
            object {
                fun processValue(value: JsonElement): JsonElement =
                    when (value) {
                        is JsonObject -> processObject(value)
                        is JsonArray -> processArray(value)
                        else -> value
                    }

                fun processObject(obj: JsonObject): JsonObject {
                    val result = LinkedHashMap<String, JsonElement>()
                    for ((k, v) in obj) {
                        if (k != "_sd") result[k] = processValue(v)
                    }
                    val sd = obj["_sd"] ?: return JsonObject(result)
                    val digests =
                        (sd as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                            ?: throw SdJwtDisclosureException("_sd must be an array of digest strings")
                    for (digest in digests) {
                        if (digest == null) throw SdJwtDisclosureException("_sd must be an array of digest strings")
                        if (!seenDigests.add(digest)) throw SdJwtDisclosureException("Digest $digest appears more than once")
                        val disclosure = byDigest[digest] ?: continue // undisclosed claim or decoy
                        val name =
                            disclosure.name
                                ?: throw SdJwtDisclosureException("Array-element disclosure referenced from an object _sd")
                        if (name == "_sd" || name == "...") {
                            throw SdJwtDisclosureException("Disclosure uses reserved claim name '$name'")
                        }
                        if (result.containsKey(name)) {
                            throw SdJwtDisclosureException("Disclosed claim name '$name' collides with an existing claim")
                        }
                        used += digest
                        result[name] = processValue(disclosure.value)
                    }
                    return JsonObject(result)
                }

                fun processArray(arr: JsonArray): JsonArray {
                    val out = mutableListOf<JsonElement>()
                    for (element in arr) {
                        val ref = (element as? JsonObject)?.takeIf { it.size == 1 && it.containsKey("...") }
                        if (ref == null) {
                            out += processValue(element)
                            continue
                        }
                        val digest =
                            (ref["..."] as? JsonPrimitive)?.takeIf { it.isString }?.content
                                ?: throw SdJwtDisclosureException("Array element digest must be a string")
                        if (!seenDigests.add(digest)) throw SdJwtDisclosureException("Digest $digest appears more than once")
                        val disclosure = byDigest[digest] ?: continue // undisclosed element or decoy: removed
                        if (disclosure.name != null) {
                            throw SdJwtDisclosureException("Object-property disclosure referenced from an array element")
                        }
                        used += digest
                        out += processValue(disclosure.value)
                    }
                    return JsonArray(out)
                }
            }

        val payload =
            anyToJsonElement(claimsSet.toJSONObject()) as? JsonObject
                ?: throw SdJwtDisclosureException("JWT payload is not a JSON object")
        val processed = processor.processObject(JsonObject(payload.filterKeys { it != "_sd_alg" }))

        val unreferenced = byDigest.keys - used
        if (unreferenced.isNotEmpty()) {
            throw SdJwtDisclosureException(
                "Disclosure hash not found in _sd array: ${unreferenced.first()} (tampered or misplaced disclosure)",
            )
        }

        val subject = (processed["vc"] as? JsonObject)?.get("credentialSubject") as? JsonObject
        return subject?.filterKeys { it != "id" } ?: emptyMap()
    }

    /** Strict disclosure decoding: exactly `[salt, name, value]` or `[salt, value]`. */
    private fun parseDisclosureStrict(discB64: String): ParsedDisclosure? =
        try {
            val array = Json.parseToJsonElement(String(b64urlDec.decode(discB64), Charsets.UTF_8)).jsonArray
            val salt = array.getOrNull(0) as? JsonPrimitive
            if (salt == null || !salt.isString) {
                null
            } else {
                when (array.size) {
                    3 -> {
                        val name = (array[1] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        name?.let { ParsedDisclosure(it, array[2]) }
                    }
                    2 -> ParsedDisclosure(null, array[1])
                    else -> null
                }
            }
        } catch (e: Exception) {
            null
        }

    /** Decodes a disclosure and returns the claim name, or null for array-element / malformed disclosures. */
    private fun parseDisclosureClaimName(discB64: String): String? = parseDisclosureStrict(discB64)?.name

    // -------------------------------------------------------------------------
    // Signed-claim validation (the signed JWT is authoritative; the unsigned
    // envelope is only a convenience view and must agree with it)
    // -------------------------------------------------------------------------

    /**
     * Validates the SIGNED temporal claims (`exp`, `nbf`) honoring the verifier's
     * clock-skew tolerance, and rejects credentials whose unsigned envelope expiry
     * disagrees with the signed `exp` claim (stripped or rewritten envelope dates).
     */
    private fun validateSignedTemporalClaims(
        credential: VerifiableCredential,
        claimsSet: JWTClaimsSet,
        options: VerificationOptions,
    ): VerificationResult.Invalid? {
        val now = Clock.System.now()
        val skew = options.clockSkewTolerance

        val signedExp =
            claimsSet.expirationTime
                ?.toInstant()
                ?.let { Instant.fromEpochSeconds(it.epochSecond, it.nano) }
        val signedNbf =
            claimsSet.notBeforeTime
                ?.toInstant()
                ?.let { Instant.fromEpochSeconds(it.epochSecond, it.nano) }

        if (options.checkExpiration && signedExp != null && now > signedExp.plus(skew)) {
            return VerificationResult.Invalid.Expired(
                credential = credential,
                expiredAt = signedExp,
                errors =
                    listOf(
                        "Signed 'exp' claim has passed: $signedExp (current time: $now, " +
                            "accounting for $skew clock skew tolerance)",
                    ),
            )
        }

        if (options.checkNotBefore && signedNbf != null && now < signedNbf.minus(skew)) {
            return VerificationResult.Invalid.NotYetValid(
                credential = credential,
                validFrom = signedNbf,
                errors =
                    listOf(
                        "Signed 'nbf' claim is in the future: $signedNbf (current time: $now, " +
                            "accounting for $skew clock skew tolerance)",
                    ),
            )
        }

        // The unsigned envelope expiry must agree with the signed exp claim. A stripped
        // envelope expirationDate would otherwise bypass the format-agnostic expiry check.
        val envelopeExpiry =
            if (credential.isVc2 && !credential.isVc1) {
                credential.validUntil
            } else {
                credential.validUntil ?: credential.expirationDate
            }
        val expiryMismatch =
            when {
                signedExp == null && envelopeExpiry == null -> false
                signedExp == null || envelopeExpiry == null -> true
                // JWT exp has second precision; compare at that granularity.
                else -> signedExp.epochSeconds != envelopeExpiry.epochSeconds
            }
        if (expiryMismatch) {
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason =
                    "Credential envelope expiration does not match the signed 'exp' claim " +
                        "(possible envelope tampering)",
                errors =
                    listOf(
                        "Envelope expiry '$envelopeExpiry' does not match signed exp '$signedExp'",
                    ),
                warnings = emptyList(),
            )
        }
        return null
    }

    /**
     * Reconciles every unsigned envelope `credentialSubject` claim against the verified
     * disclosures and the non-selectively-disclosed signed claims. An envelope claim whose
     * name or value is not backed by signed/disclosed data is rejected as tampered.
     */
    private fun reconcileEnvelopeClaims(
        credential: VerifiableCredential,
        signedJWT: SignedJWT,
        verifiedSubjectClaims: Map<String, JsonElement>,
    ): VerificationResult.Invalid.InvalidProof? {
        // Subject identifier: the signed 'sub' claim is authoritative (issuance writes
        // the subject id, or "" when absent, into 'sub').
        val signedSubject = signedJWT.jwtClaimsSet.subject.orEmpty()
        val envelopeSubject =
            credential.credentialSubject.id
                ?.value
                .orEmpty()
        if (signedSubject != envelopeSubject) {
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason =
                    "Envelope credentialSubject.id does not match the signed 'sub' claim " +
                        "(possible envelope tampering)",
                errors = listOf("Envelope subject '$envelopeSubject' != signed sub '$signedSubject'"),
                warnings = emptyList(),
            )
        }

        for ((name, envelopeValue) in credential.credentialSubject.claims) {
            val backedValue =
                verifiedSubjectClaims[name]
                    ?: return VerificationResult.Invalid.InvalidProof(
                        credential = credential,
                        reason =
                            "Envelope claim '$name' is not backed by a verified disclosure " +
                                "or signed claim",
                        errors = listOf("Unbacked envelope claim: $name"),
                        warnings = emptyList(),
                    )
            if (backedValue != envelopeValue) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason =
                        "Envelope claim '$name' does not match the verified disclosure value " +
                            "(possible envelope tampering)",
                    errors =
                        listOf(
                            "Envelope claim '$name' value '$envelopeValue' != verified value '$backedValue'",
                        ),
                    warnings = emptyList(),
                )
            }
        }
        return null
    }

    /** Converts Nimbus' untyped JSON values to kotlinx [JsonElement] for comparison. */
    private fun anyToJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is JsonElement -> value
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> ->
                buildJsonObject {
                    value.forEach { (k, v) -> if (k is String) put(k, anyToJsonElement(v)) }
                }
            is Collection<*> -> buildJsonArray { value.forEach { add(anyToJsonElement(it)) } }
            else -> JsonPrimitive(value.toString())
        }

    private fun buildCompactSdJwt(
        jwt: String,
        disclosures: List<String>,
        kbJwt: String?,
    ): String {
        val sb = StringBuilder(jwt)
        for (disc in disclosures) sb.append("~").append(disc)
        sb.append("~")
        if (kbJwt != null) sb.append(kbJwt)
        return sb.toString()
    }

    // -------------------------------------------------------------------------
    // Key Binding JWT
    // -------------------------------------------------------------------------

    private suspend fun buildKbJwt(
        proof: CredentialProof.SdJwtVcProof,
        selectedDisclosures: List<String>,
        challenge: String,
        audience: String?,
        holderVerificationMethod: String?,
    ): String? {
        val kms = getKms() ?: return null
        // Prefer the holder's verification method (proofOptions.verificationMethod); the
        // KB-JWT proves possession of the HOLDER's key. Fall back to the issuer JWT's kid
        // for self-issued credentials where holder and issuer keys coincide.
        val keyId =
            ProofEngineUtils.extractKeyId(holderVerificationMethod)
                ?: proof.sdJwtVc.let {
                    try {
                        SignedJWT.parse(it.substringBefore("~")).header.keyID
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        null
                    }
                } ?: return null
        val headerKeyId = holderVerificationMethod ?: keyId

        val compactForHash = buildCompactSdJwt(proof.sdJwtVc, selectedDisclosures, null)
        val sdHash = sha256B64(compactForHash.toByteArray(Charsets.UTF_8))

        val header =
            JWSHeader
                .Builder(JWSAlgorithm.EdDSA)
                .keyID(headerKeyId)
                .type(
                    com.nimbusds.jose.JOSEObjectType("kb+jwt"),
                ).build()

        val claimsBuilder =
            JWTClaimsSet
                .Builder()
                .issueTime(Date.from(JavaInstant.now()))
                .claim("nonce", challenge)
                .claim("sd_hash", sdHash)
        audience?.let { claimsBuilder.audience(it) }

        return signJws(header, claimsBuilder.build(), keyId, createKmsSigner(kms))
    }

    // -------------------------------------------------------------------------
    // VC claim builder
    // -------------------------------------------------------------------------

    private fun buildVcClaim(
        request: IssuanceRequest,
        sdHashes: List<String>,
    ): Map<String, Any> =
        buildMap {
            put("@context", listOf("https://www.w3.org/2018/credentials/v1"))
            put("type", request.type.map { it.value })
            put(
                "credentialSubject",
                buildMap {
                    request.credentialSubject.id?.let { put("id", it.value) }
                    put("_sd", sdHashes)
                },
            )
            request.credentialStatus?.let { status ->
                put(
                    "credentialStatus",
                    buildMap {
                        put("id", status.id.value)
                        put("type", status.type)
                        put("statusPurpose", status.statusPurpose.name.lowercase())
                        status.statusListIndex?.let { put("statusListIndex", it) }
                        status.statusListCredential?.let { put("statusListCredential", it.value) }
                    },
                )
            }
            request.credentialSchema?.let { schema ->
                put(
                    "credentialSchema",
                    buildMap {
                        put("id", schema.id.value)
                        put("type", schema.type)
                    },
                )
            }
        }

    // -------------------------------------------------------------------------
    // KMS / Signer helpers
    // -------------------------------------------------------------------------

    private fun getKms(): KeyManagementService? = config.properties["kms"] as? KeyManagementService

    @Suppress("UNCHECKED_CAST")
    private fun getSignerFunction(): (suspend (ByteArray, String) -> ByteArray)? =
        config.properties["signer"] as? (suspend (ByteArray, String) -> ByteArray)

    private fun createKmsSigner(kms: KeyManagementService): suspend (ByteArray, String) -> ByteArray =
        { data: ByteArray, kid: String ->
            when (val result = kms.sign(KeyId(kid), data)) {
                is SignResult.Success -> result.signature
                is SignResult.Failure.KeyNotFound ->
                    throw IllegalStateException("SD-JWT sign failed: key not found: ${result.keyId.value}")
                is SignResult.Failure.UnsupportedAlgorithm ->
                    throw IllegalStateException("SD-JWT sign failed: unsupported algorithm")
                is SignResult.Failure.Error ->
                    throw IllegalStateException("SD-JWT sign failed: ${result.reason}", result.cause)
            }
        }

    private fun getSignerFunctionOrKms(): (suspend (ByteArray, String) -> ByteArray)? =
        getSignerFunction() ?: getKms()?.let { createKmsSigner(it) }

    /**
     * Produces a compact JWS without blocking: the JWS signing input is computed up front, the
     * (suspending) KMS signature is awaited in the caller's coroutine, and the compact form is
     * assembled by hand. Nimbus' synchronous [com.nimbusds.jose.JWSSigner] is deliberately not
     * used — bridging it to the suspending KMS would need `runBlocking` on the caller's thread.
     */
    private suspend fun signJws(
        header: JWSHeader,
        claims: JWTClaimsSet,
        keyId: String,
        signer: suspend (ByteArray, String) -> ByteArray,
    ): String {
        val signingInput = SignedJWT(header, claims).signingInput
        val signature = signer(signingInput, keyId)
        return String(signingInput, Charsets.US_ASCII) + "." + b64url.encodeToString(signature)
    }

    private suspend fun getIssuerVerificationMethod(
        issuerIri: Iri,
        jwtString: String,
    ): org.trustweave.did.model.VerificationMethod? {
        if (!issuerIri.isDid) return null
        val didResolver = config.getDidResolver() ?: return null
        val keyIdFromJwt =
            try {
                SignedJWT.parse(jwtString.substringBefore("~")).header.keyID
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                null
            }
        // The issuer key signs assertions (credentials): it must be authorized under the
        // DID document's `assertionMethod` relationship, not merely present in
        // `verificationMethod` (fail closed on purpose mismatch).
        return ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = keyIdFromJwt,
            didResolver = didResolver,
            expectedProofPurpose = CredentialConstants.ProofPurposes.ASSERTION_METHOD,
        )
    }

    /**
     * The issuer-signed JWT must declare `typ` `dc+sd-jwt` or `vc+sd-jwt`. A token without `typ`
     * (issued before this engine set it) is refused unless the verifier opts in with
     * `additionalOptions["allowLegacySdJwtTyp"] = true`; any other `typ` is always refused.
     */
    private fun checkIssuerJwtType(
        credential: VerifiableCredential,
        jwt: SignedJWT,
        options: VerificationOptions,
    ): VerificationResult.Invalid.InvalidProof? {
        val typ = jwt.header.type?.toString()
        val acceptable =
            when {
                typ == null -> options.additionalOptions[ALLOW_LEGACY_TYP_OPTION] == true
                else -> SD_JWT_TYPS.any { it.equals(typ, ignoreCase = true) }
            }
        if (acceptable) return null
        return VerificationResult.Invalid.InvalidProof(
            credential = credential,
            reason =
                if (typ == null) {
                    "SD-JWT-VC issuer JWT has no 'typ' header (expected one of $SD_JWT_TYPS)"
                } else {
                    "SD-JWT-VC issuer JWT 'typ' is '$typ' (expected one of $SD_JWT_TYPS)"
                },
            errors =
                listOf(
                    "Unexpected issuer JWT typ: $typ. Credentials issued before typ was emitted can be " +
                        "accepted with VerificationOptions.additionalOptions[\"$ALLOW_LEGACY_TYP_OPTION\"] = true",
                ),
            warnings = emptyList(),
        )
    }

    companion object {
        const val SD_JWT_TYP_DC = "dc+sd-jwt"
        const val SD_JWT_TYP_VC = "vc+sd-jwt"

        /** `additionalOptions` key; set to `true` to accept issuer JWTs that carry no `typ` header. */
        const val ALLOW_LEGACY_TYP_OPTION = "allowLegacySdJwtTyp"

        private val SD_JWT_TYPS = listOf(SD_JWT_TYP_DC, SD_JWT_TYP_VC)
        private const val SD_ALG_SHA256 = "sha-256"
    }
}
