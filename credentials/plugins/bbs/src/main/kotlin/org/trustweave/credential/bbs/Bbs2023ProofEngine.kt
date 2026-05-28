package org.trustweave.credential.bbs

import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
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
import org.trustweave.core.identifiers.Iri
import java.util.UUID

/**
 * W3C Data Integrity BBS Cryptosuite 2023 proof engine.
 *
 * Implements selective-disclosure and zero-knowledge proofs using the BBS-2023 cryptosuite
 * specification over the BLS12-381 curve.
 *
 * **Capabilities:**
 * - Issuance: signs all credential claims as a BBS+ signature over the claim set
 * - Verification: re-derives commitment from the public key embedded in the proof
 * - Presentation: derives a ZK proof disclosing only the requested subset of claims
 *
 * **Configuration via [ProofEngineConfig.properties]:**
 * | Key           | Type               | Description                              |
 * |---------------|--------------------|------------------------------------------|
 * | `keyPair`     | [Bls12381KeyPair]  | Signing key pair (required for issuance) |
 *
 * If no `keyPair` is provided a fresh ephemeral key is generated on first `issue()` call
 * (useful for unit tests).
 *
 * **Verification key resolution order:**
 * 1. Configured `keyPair` — if `verificationMethod` ends with the configured key ID
 * 2. Base64url-encoded 96-byte public key after the `#` fragment in the verification method
 */
class Bbs2023ProofEngine(
    private val config: ProofEngineConfig = ProofEngineConfig(),
) : ProofEngine {

    private val logger = LoggerFactory.getLogger(Bbs2023ProofEngine::class.java)

    /**
     * Lazily-initialised ephemeral key — created only when no explicit key pair is configured.
     * Stored as a property so that a single engine instance uses the same ephemeral key for
     * both `issue()` and `verify()`.
     */
    private val activeKeyPair: Bls12381KeyPair by lazy {
        (config.properties["keyPair"] as? Bls12381KeyPair)
            ?: run {
                logger.warn(
                    "No BLS12-381 key pair configured; generating ephemeral key for BBS-2023 engine. " +
                        "Set config.properties[\"keyPair\"] for production use.",
                )
                BbsCryptoSuite.generateKeyPair("urn:bbs:ephemeral:${UUID.randomUUID()}")
            }
    }

    override val format = ProofSuiteId.BBS_2023
    override val formatName = "W3C Data Integrity BBS Cryptosuite 2023"
    override val formatVersion = "1.0"

    override val capabilities = ProofEngineCapabilities(
        selectiveDisclosure = true,
        zeroKnowledge = true,
        revocation = true,
        presentation = true,
        predicates = false,
    )

    // -------------------------------------------------------------------------
    // Issue
    // -------------------------------------------------------------------------

    override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
        require(request.format == format) {
            "Request format ${request.format.value} does not match engine format ${format.value}"
        }

        val keyPair = activeKeyPair

        // Encode each claim as a message: "key=value" UTF-8 bytes
        val messages = encodeClaimsAsMessages(request.credentialSubject)

        logger.debug(
            "Issuing BBS-2023 credential: keyId={}, messageCount={}",
            keyPair.keyId, messages.size,
        )

        val signatureBytes = BbsCryptoSuite.sign(
            secretKey = keyPair.secretKeyBytes
                ?: error("Secret key is required for signing; got a verification-only key pair"),
            publicKey = keyPair.publicKeyBytes,
            messages = messages.ifEmpty { listOf(byteArrayOf(0x00)) },
        )

        val proofValue = BbsCryptoSuite.encodeBase64Url(signatureBytes)

        // Embed the public key (base64url) as part of the verification method fragment so
        // that `verify()` can recover it without a DID resolver.
        val verificationMethod = buildVerificationMethodId(request.issuer, keyPair)
        val created = Clock.System.now()

        val proof = CredentialProof.LinkedDataProof(
            type = "DataIntegrityProof",
            created = created,
            verificationMethod = verificationMethod,
            proofPurpose = "assertionMethod",
            proofValue = proofValue,
            additionalProperties = mapOf(
                "cryptosuite" to JsonPrimitive("bbs-2023"),
            ),
        )

        val credentialId = request.id ?: CredentialId("urn:uuid:${UUID.randomUUID()}")

        return VerifiableCredential(
            context = listOf(
                "https://www.w3.org/2018/credentials/v1",
                "https://w3id.org/security/bbs/v1",
            ),
            id = credentialId,
            type = request.type,
            issuer = request.issuer,
            issuanceDate = request.issuedAt,
            validFrom = request.validFrom,
            expirationDate = request.validUntil,
            credentialSubject = request.credentialSubject,
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
        val proof = credential.proof as? CredentialProof.LinkedDataProof
            ?: return invalidProof(credential, "BBS-2023 credential must have a LinkedDataProof")

        val cryptosuite = proof.additionalProperties["cryptosuite"]
            ?.let { (it as? JsonPrimitive)?.content }
        if (cryptosuite != "bbs-2023") {
            return invalidProof(
                credential,
                "Expected cryptosuite=bbs-2023 but got: $cryptosuite",
            )
        }

        return try {
            val signatureBytes = BbsCryptoSuite.decodeBase64Url(proof.proofValue)
            if (signatureBytes.size != BbsCryptoSuite.SIGNATURE_SIZE) {
                return invalidProof(
                    credential,
                    "Invalid BBS+ signature length: expected ${BbsCryptoSuite.SIGNATURE_SIZE} bytes, " +
                        "got ${signatureBytes.size}",
                )
            }

            val publicKey = resolvePublicKey(proof.verificationMethod)
                ?: return VerificationResult.Invalid.InvalidIssuer(
                    credential = credential,
                    issuerIri = issuerIri(credential),
                    reason = "Could not resolve BLS12-381 public key from: ${proof.verificationMethod}",
                )

            val messages = encodeClaimsAsMessages(credential.credentialSubject)
            val valid = BbsCryptoSuite.verify(
                publicKey = publicKey,
                signature = signatureBytes,
                messages = messages.ifEmpty { listOf(byteArrayOf(0x00)) },
            )

            if (valid) {
                val issuer = issuerIri(credential)
                logger.debug("BBS-2023 signature verified OK for credential {}", credential.id?.value)
                VerificationResult.Valid(
                    credential = credential,
                    issuerIri = issuer,
                    subjectIri = credential.credentialSubject.id,
                    issuedAt = credential.issuanceDate ?: credential.validFrom ?: kotlinx.datetime.Clock.System.now(),
                    expiresAt = credential.expirationDate ?: credential.validUntil,
                    formatMetadata = mapOf(
                        "cryptosuite" to JsonPrimitive("bbs-2023"),
                        "verificationMethod" to JsonPrimitive(proof.verificationMethod),
                    ),
                )
            } else {
                invalidProof(credential, "BBS+ signature verification failed")
            }
        } catch (e: Exception) {
            logger.error(
                "BBS-2023 verification error for credential {}: {}",
                credential.id?.value, e.message, e,
            )
            invalidProof(credential, "BBS-2023 verification error: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Create presentation (selective disclosure)
    // -------------------------------------------------------------------------

    override suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest,
    ): VerifiablePresentation {
        require(credentials.isNotEmpty()) { "At least one credential is required for a presentation" }

        val keyPair = activeKeyPair
        val disclosedClaimNames: Set<String>? = request.disclosedClaims

        val derivedCredentials = credentials.map { vc ->
            deriveCredentialProof(vc, keyPair, disclosedClaimNames)
        }

        val holder = derivedCredentials.first().credentialSubject.id
            ?: throw IllegalArgumentException("Cannot create presentation: credential subject has no id")

        return VerifiablePresentation(
            context = listOf(
                "https://www.w3.org/2018/credentials/v1",
                "https://w3id.org/security/bbs/v1",
            ),
            type = listOf(CredentialType.Custom("VerifiablePresentation")),
            holder = holder,
            verifiableCredential = derivedCredentials,
            challenge = request.proofOptions?.challenge,
            domain = request.proofOptions?.domain,
        )
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override suspend fun initialize(config: ProofEngineConfig) {
        logger.debug("BBS-2023 ProofEngine initialized")
    }

    override suspend fun close() {
        logger.debug("BBS-2023 ProofEngine closed")
    }

    override fun isReady(): Boolean = true

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Encode a [CredentialSubject]'s claims as UTF-8 byte messages for BBS+ signing.
     *
     * Each entry is serialised as `"key=value"` with keys sorted for determinism.
     */
    private fun encodeClaimsAsMessages(subject: CredentialSubject): List<ByteArray> =
        subject.claims.entries.sortedBy { it.key }.map { (k, v) ->
            "$k=${v}".toByteArray(Charsets.UTF_8)
        }

    /**
     * Build the verification method ID string for a credential proof.
     *
     * Embeds the public key as a base64url fragment so that [resolvePublicKey] can recover it
     * without needing a live DID resolver.
     *
     * Format: `{issuerIri}#bbs-{publicKeyBase64url}`
     */
    private fun buildVerificationMethodId(issuer: Issuer, keyPair: Bls12381KeyPair): String {
        val issuerIriValue = when (issuer) {
            is Issuer.IriIssuer -> issuer.id.value
            is Issuer.ObjectIssuer -> issuer.id.value
        }
        val pkFragment = keyPair.publicKeyBase64Url
        return "$issuerIriValue#bbs-$pkFragment"
    }

    /**
     * Attempt to recover the 96-byte BLS12-381 public key from a verification method string.
     *
     * Resolution order:
     * 1. If the verification method ends with the active key pair's public key fragment, return
     *    the active key pair's public key directly (most reliable for self-issued credentials).
     * 2. Parse the fragment after `#bbs-` as a base64url-encoded 96-byte public key.
     */
    private fun resolvePublicKey(verificationMethod: String): ByteArray? {
        // 1. Match against the engine's own active key pair
        val kp = activeKeyPair
        val expectedFragment = "bbs-${kp.publicKeyBase64Url}"
        if (verificationMethod.endsWith(expectedFragment)) {
            return kp.publicKeyBytes
        }

        // 2. Try to decode #bbs-<base64url> fragment
        if (verificationMethod.contains("#bbs-")) {
            val pkBase64 = verificationMethod.substringAfter("#bbs-")
            return try {
                val decoded = BbsCryptoSuite.decodeBase64Url(pkBase64)
                if (decoded.size == 96) decoded else null
            } catch (_: Exception) {
                null
            }
        }

        // 3. Try the raw fragment (older format) as base64url → 96 bytes
        val fragment = if (verificationMethod.contains("#")) {
            verificationMethod.substringAfter("#")
        } else {
            verificationMethod
        }
        return try {
            val decoded = BbsCryptoSuite.decodeBase64Url(fragment)
            if (decoded.size == 96) decoded else null
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveCredentialProof(
        vc: VerifiableCredential,
        keyPair: Bls12381KeyPair,
        disclosedClaimNames: Set<String>?,
    ): VerifiableCredential {
        val originalProof = vc.proof as? CredentialProof.LinkedDataProof
            ?: return vc  // No BBS proof to derive from — return as-is

        val allClaims = vc.credentialSubject.claims
        val allMessages = encodeClaimsAsMessages(vc.credentialSubject)
        val claimKeys = allClaims.keys.sorted()

        val disclosedIndices: Set<Int> = if (disclosedClaimNames == null) {
            claimKeys.indices.toSet()
        } else {
            claimKeys.mapIndexedNotNull { idx, key ->
                if (key in disclosedClaimNames) idx else null
            }.toSet()
        }

        val signatureBytes = try {
            BbsCryptoSuite.decodeBase64Url(originalProof.proofValue)
        } catch (_: Exception) {
            return vc
        }
        if (signatureBytes.size != BbsCryptoSuite.SIGNATURE_SIZE) return vc

        val derivedProof = BbsCryptoSuite.deriveProof(
            signature = signatureBytes,
            publicKey = keyPair.publicKeyBytes,
            messages = allMessages.ifEmpty { listOf(byteArrayOf(0x00)) },
            disclosed = disclosedIndices,
        )

        val disclosedClaims = if (disclosedClaimNames != null) {
            allClaims.filterKeys { it in disclosedClaimNames }
        } else {
            allClaims
        }
        val filteredSubject = vc.credentialSubject.copy(claims = disclosedClaims)

        val derivedProofValue = BbsCryptoSuite.encodeBase64Url(derivedProof.proofBytes)
        val derivedLinkedDataProof = CredentialProof.LinkedDataProof(
            type = "DataIntegrityProof",
            created = Clock.System.now(),
            verificationMethod = originalProof.verificationMethod,
            proofPurpose = "assertionMethod",
            proofValue = derivedProofValue,
            additionalProperties = mapOf(
                "cryptosuite" to JsonPrimitive("bbs-2023-derived"),
                "disclosedIndices" to JsonPrimitive(disclosedIndices.sorted().joinToString(",")),
            ),
        )

        return vc.copy(
            credentialSubject = filteredSubject,
            proof = derivedLinkedDataProof,
        )
    }

    private fun invalidProof(
        vc: VerifiableCredential,
        reason: String,
    ): VerificationResult.Invalid.InvalidProof =
        VerificationResult.Invalid.InvalidProof(
            credential = vc,
            reason = reason,
            errors = listOf(reason),
        )

    private fun issuerIri(vc: VerifiableCredential): Iri =
        when (val issuer = vc.issuer) {
            is Issuer.IriIssuer -> issuer.id
            is Issuer.ObjectIssuer -> issuer.id
        }
}
