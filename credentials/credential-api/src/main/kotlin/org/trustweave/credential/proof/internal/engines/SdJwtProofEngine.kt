package org.trustweave.credential.proof.internal.engines

import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.credential.spi.status.CredentialStatusChecker
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.identifiers.Did
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.SignatureException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.*
import java.time.Instant as JavaInstant

/**
 * SD-JWT-VC (Selective Disclosure JWT Verifiable Credential) proof engine.
 *
 * Implements the IETF SD-JWT-VC specification (draft-ietf-oauth-sd-jwt-vc) with full
 * selective disclosure support. Each credential subject claim is individually disclosable:
 *
 * - **Issue**: Claims are replaced by `_sd` hashes in the JWT; the raw disclosure strings
 *   are stored in [CredentialProof.SdJwtVcProof.disclosures].
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

    override val capabilities = ProofEngineCapabilities(
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

        val issuerIri = request.issuer.let { issuer ->
            when (issuer) {
                is Issuer.IriIssuer -> issuer.id
                is Issuer.ObjectIssuer -> issuer.id
            }
        }

        val keyId = ProofEngineUtils.extractKeyId(request.issuerKeyId?.value)
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
        val claimsBuilder = JWTClaimsSet.Builder()
            .issuer(issuerIri.value)
            .subject(request.credentialSubject.id?.value ?: "")
            .issueTime(Date.from(now))
            .claim("_sd_alg", "sha-256")
            .claim("vct", request.type.firstOrNull { it.value != "VerifiableCredential" }?.value
                ?: "VerifiableCredential")
            .claim("vc", buildVcClaim(request, sdHashes))

        request.validUntil?.let {
            claimsBuilder.expirationTime(Date.from(JavaInstant.ofEpochSecond(it.epochSeconds)))
        }

        val header = JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(keyId).build()
        val signedJWT = SignedJWT(header, claimsBuilder.build())
        val signer = getSigner(keyId)
            ?: throw IllegalArgumentException(
                "No signer available for key $keyId. Configure KMS via ProofEngineConfig.",
            )
        signedJWT.sign(signer)

        val proof = CredentialProof.SdJwtVcProof(
            sdJwtVc = signedJWT.serialize(),
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
        val proof = credential.proof as? CredentialProof.SdJwtVcProof
            ?: return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "SD-JWT-VC credential must have SdJwtVcProof",
                errors = listOf("Expected SdJwtVcProof but got ${credential.proof?.javaClass?.simpleName}"),
                warnings = emptyList(),
            )

        return try {
            val signedJWT = SignedJWT.parse(proof.sdJwtVc)
            val issuerIri = when (val issuer = credential.issuer) {
                is Issuer.IriIssuer -> issuer.id
                is Issuer.ObjectIssuer -> issuer.id
            }

            val verifier = getVerifier(issuerIri, proof.sdJwtVc)
                ?: return VerificationResult.Invalid.InvalidIssuer(
                    credential = credential,
                    issuerIri = issuerIri,
                    reason = "Could not resolve issuer or get verification key",
                    errors = listOf("Failed to resolve issuer: ${issuerIri.value}"),
                    warnings = emptyList(),
                )

            if (!signedJWT.verify(verifier)) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "JWT signature verification failed",
                    errors = listOf("Invalid signature on SD-JWT-VC"),
                    warnings = emptyList(),
                )
            }

            // Verify disclosure integrity: every disclosed claim must hash to an _sd entry
            val disclosures = proof.disclosures ?: emptyList()
            if (disclosures.isNotEmpty()) {
                val sdHashes = extractSdHashes(signedJWT)
                for (discB64 in disclosures) {
                    val hashB64 = sha256B64(discB64.toByteArray())
                    if (hashB64 !in sdHashes) {
                        return VerificationResult.Invalid.InvalidProof(
                            credential = credential,
                            reason = "Disclosure hash not found in _sd array",
                            errors = listOf("Tampered disclosure detected: hash $hashB64 not in _sd"),
                            warnings = emptyList(),
                        )
                    }
                }
            }

            val claimsSet = signedJWT.jwtClaimsSet
            val subjectIri = claimsSet.subject?.let { Iri(it) } ?: credential.credentialSubject.id
            val issuedAt: kotlinx.datetime.Instant =
                claimsSet.issueTime?.toInstant()
                    ?.let { Instant.fromEpochSeconds(it.epochSecond, it.nano) }
                    ?: credential.issuanceDate
                    ?: credential.validFrom
                    ?: kotlinx.datetime.Clock.System.now()
            val expiresAt = claimsSet.expirationTime?.toInstant()
                ?.let { Instant.fromEpochSeconds(it.epochSecond, it.nano) }
                ?: credential.expirationDate

            // Revocation / suspension check
            val checker = config.properties["statusChecker"] as? CredentialStatusChecker
            if (checker != null && credential.credentialStatus != null) {
                when (val status = checker.checkStatus(credential)) {
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
                    is CredentialStatusCheckResult.CheckFailed -> { /* warn but don't fail */ }
                    else -> {}
                }
            }

            VerificationResult.Valid(
                credential = credential,
                issuerIri = issuerIri,
                subjectIri = subjectIri,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                warnings = emptyList(),
                formatMetadata = buildJsonObject {
                    put("jwt_id", claimsSet.jwtid ?: "")
                    put("disclosed_claims", disclosures.size)
                    put("_sd_alg", claimsSet.getStringClaim("_sd_alg") ?: "sha-256")
                },
            )
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

        val holder = credentials.first().credentialSubject.id
            ?: throw IllegalArgumentException("Cannot create presentation: credential subject has no id")

        // For each credential, filter disclosures to requested claims only
        val presentedCredentials = credentials.map { credential ->
            val proof = credential.proof as? CredentialProof.SdJwtVcProof ?: return@map credential
            val allDisclosures = proof.disclosures ?: return@map credential

            val requestedClaims = (request.proofOptions
                ?.additionalOptions
                ?.get("disclosedClaims") as? Set<*>)
                ?.filterIsInstance<String>()
                ?.toSet()

            val selectedDisclosures = if (requestedClaims == null || requestedClaims.isEmpty()) {
                allDisclosures
            } else {
                allDisclosures.filter { discB64 ->
                    parseDisclosureClaimName(discB64) in requestedClaims
                }
            }

            // Optionally append KB-JWT if challenge is provided
            val challenge = request.proofOptions?.challenge
            val kbJwt = if (challenge != null) {
                buildKbJwt(
                    proof = proof,
                    selectedDisclosures = selectedDisclosures,
                    challenge = challenge,
                    audience = request.proofOptions?.domain,
                )
            } else {
                null
            }

            credential.copy(
                proof = CredentialProof.SdJwtVcProof(
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

        return VerifiablePresentation(
            type = listOf(CredentialType.Custom("VerifiablePresentation")),
            holder = holder,
            verifiableCredential = presentedCredentials,
            proof = null,
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
    private fun createDisclosure(claimName: String, claimValue: JsonElement): Pair<String, String> {
        val saltBytes = ByteArray(16).also { random.nextBytes(it) }
        val salt = b64url.encodeToString(saltBytes)

        val disclosureJson = buildJsonArray {
            add(salt)
            add(claimName)
            add(claimValue)
        }.toString()

        val discB64 = b64url.encodeToString(disclosureJson.toByteArray(Charsets.UTF_8))
        val hashB64 = sha256B64(discB64.toByteArray(Charsets.UTF_8))
        return Pair(discB64, hashB64)
    }

    private fun sha256B64(input: ByteArray): String =
        b64url.encodeToString(MessageDigest.getInstance("SHA-256").digest(input))

    private fun extractSdHashes(signedJWT: SignedJWT): Set<String> {
        val claimsSet = signedJWT.jwtClaimsSet
        @Suppress("UNCHECKED_CAST")
        val vcClaim = claimsSet.getJSONObjectClaim("vc") as? Map<String, Any?> ?: return emptySet()
        @Suppress("UNCHECKED_CAST")
        val credSubject = vcClaim["credentialSubject"] as? Map<String, Any?> ?: return emptySet()
        @Suppress("UNCHECKED_CAST")
        val sdList = credSubject["_sd"] as? List<*> ?: return emptySet()
        return sdList.filterIsInstance<String>().toSet()
    }

    /** Decodes a disclosure and returns the claim name (index 1 in the array). */
    private fun parseDisclosureClaimName(discB64: String): String? {
        return try {
            val json = String(b64urlDec.decode(discB64), Charsets.UTF_8)
            Json.parseToJsonElement(json).jsonArray.getOrNull(1)?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    private fun buildCompactSdJwt(jwt: String, disclosures: List<String>, kbJwt: String?): String {
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
    ): String? {
        val kms = getKms() ?: return null
        val keyId = proof.sdJwtVc.let {
            try { SignedJWT.parse(it).header.keyID } catch (e: Exception) { null }
        } ?: return null

        val compactForHash = buildCompactSdJwt(proof.sdJwtVc, selectedDisclosures, null)
        val sdHash = sha256B64(compactForHash.toByteArray(Charsets.UTF_8))

        val header = JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(keyId).type(
            com.nimbusds.jose.JOSEObjectType("kb+jwt"),
        ).build()

        val claimsBuilder = JWTClaimsSet.Builder()
            .issueTime(Date.from(JavaInstant.now()))
            .claim("nonce", challenge)
            .claim("sd_hash", sdHash)
        audience?.let { claimsBuilder.audience(it) }

        val kbJwt = SignedJWT(header, claimsBuilder.build())
        kbJwt.sign(KmsJwsSigner(keyId, createKmsSigner(kms)))
        return kbJwt.serialize()
    }

    // -------------------------------------------------------------------------
    // VC claim builder
    // -------------------------------------------------------------------------

    private fun buildVcClaim(request: IssuanceRequest, sdHashes: List<String>): Map<String, Any> {
        return buildMap {
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
    }

    // -------------------------------------------------------------------------
    // KMS / Signer helpers
    // -------------------------------------------------------------------------

    private fun getKms(): KeyManagementService? = config.properties["kms"] as? KeyManagementService

    @Suppress("UNCHECKED_CAST")
    private fun getSignerFunction(): (suspend (ByteArray, String) -> ByteArray)? =
        config.properties["signer"] as? (suspend (ByteArray, String) -> ByteArray)

    private fun createKmsSigner(kms: KeyManagementService): suspend (ByteArray, String) -> ByteArray {
        return { data: ByteArray, kid: String ->
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
    }

    private fun getSigner(keyId: String): JWSSigner? {
        val signerFn = getSignerFunction() ?: run {
            val kms = getKms() ?: return null
            createKmsSigner(kms)
        }
        return KmsJwsSigner(keyId, signerFn)
    }

    private suspend fun getVerifier(issuerIri: Iri, jwtString: String): Ed25519Verifier? {
        if (!issuerIri.isDid) return null
        val didResolver = config.getDidResolver() ?: return null
        val keyIdFromJwt = try { SignedJWT.parse(jwtString).header.keyID } catch (e: Exception) { null }
        val verificationMethod = ProofEngineUtils.resolveVerificationMethod(
            issuerIri = issuerIri,
            verificationMethodId = keyIdFromJwt,
            didResolver = didResolver,
        ) ?: return null
        return ProofEngineUtils.createEd25519Verifier(verificationMethod)
    }

    /**
     * JWSSigner adapter bridging async KMS signing into Nimbus JOSE's synchronous interface.
     */
    private class KmsJwsSigner(
        private val keyId: String,
        private val signer: suspend (ByteArray, String) -> ByteArray,
    ) : JWSSigner {
        override fun sign(header: JWSHeader, signingInput: ByteArray): com.nimbusds.jose.util.Base64URL {
            return try {
                val signature = runBlocking { signer(signingInput, keyId) }
                com.nimbusds.jose.util.Base64URL.encode(signature)
            } catch (e: Exception) {
                throw JOSEException("KMS signing failed: ${e.message}", e)
            }
        }

        override fun supportedJWSAlgorithms(): MutableSet<JWSAlgorithm> =
            mutableSetOf(JWSAlgorithm.EdDSA)

        override fun getJCAContext(): com.nimbusds.jose.jca.JCAContext =
            com.nimbusds.jose.jca.JCAContext()
    }
}
