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
import org.trustweave.core.util.decodeBase58
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import java.util.UUID

/**
 * W3C Data Integrity BBS Cryptosuite 2023 proof engine.
 *
 * Implements selective-disclosure and zero-knowledge proofs using the BBS-2023 cryptosuite
 * specification over the BLS12-381 curve.
 *
 * **Capabilities:**
 * - Issuance: signs all credential claims as a BBS+ signature over the claim set
 * - Verification: verifies the BBS+ signature against the public key resolved from the
 *   issuer's DID document (never from key material embedded in the proof itself)
 * - Presentation: derives a ZK proof disclosing only the requested subset of claims
 *
 * **Configuration via [ProofEngineConfig]:**
 * | Key           | Type               | Description                                       |
 * |---------------|--------------------|---------------------------------------------------|
 * | `keyPair`     | [Bls12381KeyPair]  | Signing key pair (required for issuance)          |
 * | `didResolver` | `DidResolver`      | Issuer DID resolver (required for verification);  |
 * |               |                    | also accepted via [ProofEngineConfig.didResolver] |
 *
 * If no `keyPair` is provided a fresh ephemeral key is generated on first `issue()` call
 * (useful for unit tests).
 *
 * **Verification key binding (fail-closed):**
 * 1. The proof's `verificationMethod` must be a DID URL rooted in the credential's issuer DID
 * 2. The issuer DID is resolved via the configured `didResolver`; without a resolver
 *    verification always fails
 * 3. The verification method must be embedded in the resolved DID document's
 *    `verificationMethod` list and referenced by its `assertionMethod` relationship
 * 4. The BLS12-381 G2 public key is extracted from that entry (`publicKeyMultibase`
 *    or `publicKeyJwk.x`); key bytes embedded in the proof are never trusted
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

        // Reference the issuer's key by its identifier. Verifiers must resolve the actual
        // public key from the issuer's DID document — never from this string.
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

            val publicKey = when (val resolution = resolveIssuerPublicKey(credential, proof.verificationMethod)) {
                is IssuerKeyResolution.Resolved -> resolution.publicKey
                is IssuerKeyResolution.InvalidBinding -> return invalidProof(credential, resolution.reason)
                is IssuerKeyResolution.IssuerFailure -> return VerificationResult.Invalid.InvalidIssuer(
                    credential = credential,
                    issuerIri = issuerIri(credential),
                    reason = resolution.reason,
                    errors = listOf(resolution.reason),
                )
            }

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
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Preserve structured cancellation (e.g. from the suspend DID resolver call).
            throw e
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
     * The ID references the issuer's key by its [Bls12381KeyPair.keyId]; it intentionally
     * carries no key material. Verifiers resolve the public key from the issuer's DID
     * document entry with this ID.
     *
     * Format: `{issuerIri}#{keyId}` (or `keyId` as-is when it is already a DID URL rooted
     * in the issuer IRI).
     */
    private fun buildVerificationMethodId(issuer: Issuer, keyPair: Bls12381KeyPair): String {
        val issuerIriValue = when (issuer) {
            is Issuer.IriIssuer -> issuer.id.value
            is Issuer.ObjectIssuer -> issuer.id.value
        }
        val keyId = keyPair.keyId
        return when {
            keyId.startsWith("$issuerIriValue#") -> keyId
            keyId.startsWith("#") -> "$issuerIriValue$keyId"
            else -> "$issuerIriValue#$keyId"
        }
    }

    /**
     * Outcome of resolving the issuer-bound BBS verification key.
     */
    private sealed interface IssuerKeyResolution {
        /** Key successfully resolved from the issuer's DID document. */
        class Resolved(val publicKey: ByteArray) : IssuerKeyResolution

        /** The proof's verification method is not bound to the credential issuer. */
        data class InvalidBinding(val reason: String) : IssuerKeyResolution

        /** The issuer DID could not be resolved or does not authorize the key. */
        data class IssuerFailure(val reason: String) : IssuerKeyResolution
    }

    /**
     * Resolve the BLS12-381 public key for [verificationMethod] from the credential issuer's
     * DID document.
     *
     * Security properties (fail-closed):
     * - The verification method must be rooted in the credential's issuer DID — a proof
     *   pointing at any other controller is rejected outright.
     * - The key bytes always come from the resolved DID document, never from the
     *   verification method string in the attacker-controlled proof.
     * - The verification method must appear in the DID document's `verificationMethod`
     *   list **and** be referenced by its `assertionMethod` relationship.
     * - If no [org.trustweave.did.resolver.DidResolver] is configured, or resolution fails,
     *   verification fails.
     */
    private suspend fun resolveIssuerPublicKey(
        credential: VerifiableCredential,
        verificationMethod: String,
    ): IssuerKeyResolution {
        val issuer = issuerIri(credential)

        val controller = verificationMethod.substringBefore('#', missingDelimiterValue = "")
        if (controller.isEmpty() || !verificationMethod.contains('#')) {
            return IssuerKeyResolution.InvalidBinding(
                "Proof verificationMethod must be a DID URL with a fragment, got: $verificationMethod",
            )
        }
        if (controller != issuer.value) {
            return IssuerKeyResolution.InvalidBinding(
                "Proof verificationMethod '$verificationMethod' is not rooted in " +
                    "issuer DID '${issuer.value}'",
            )
        }

        val resolver = config.getDidResolver()
            ?: return IssuerKeyResolution.IssuerFailure(
                "No DID resolver configured for BBS-2023 verification; cannot resolve issuer " +
                    "'${issuer.value}' — failing closed. Provide a DidResolver via " +
                    "ProofEngineConfig.didResolver or properties[\"didResolver\"].",
            )

        val issuerDid = try {
            Did(controller)
        } catch (e: IllegalArgumentException) {
            return IssuerKeyResolution.IssuerFailure(
                "Issuer '$controller' is not a valid DID and cannot be resolved: ${e.message}",
            )
        }

        val document = when (val resolution = resolver.resolve(issuerDid)) {
            is DidResolutionResult.Success -> resolution.document
            is DidResolutionResult.Failure -> return IssuerKeyResolution.IssuerFailure(
                "Failed to resolve issuer DID '${issuerDid.value}': $resolution",
            )
        }

        val methodEntry = document.verificationMethod.firstOrNull { it.id.value == verificationMethod }
            ?: return IssuerKeyResolution.IssuerFailure(
                "Issuer DID document '${issuerDid.value}' does not contain " +
                    "verification method '$verificationMethod'",
            )

        val authorizedForAssertion = document.assertionMethod.any { it.value == verificationMethod }
        if (!authorizedForAssertion) {
            return IssuerKeyResolution.IssuerFailure(
                "Verification method '$verificationMethod' is not referenced by assertionMethod " +
                    "in issuer DID document '${issuerDid.value}'",
            )
        }

        val publicKey = extractBlsPublicKey(methodEntry)
            ?: return IssuerKeyResolution.IssuerFailure(
                "Could not extract a $BLS_G2_PUBLIC_KEY_SIZE-byte BLS12-381 G2 public key from " +
                    "verification method '$verificationMethod' in issuer DID document " +
                    "'${issuerDid.value}' (expected publicKeyMultibase or publicKeyJwk.x)",
            )

        return IssuerKeyResolution.Resolved(publicKey)
    }

    /**
     * Extract the 96-byte compressed G2 public key from a DID document verification method.
     *
     * Supported representations:
     * - `publicKeyMultibase`: `z` (base58btc) or `u` (base64url, no padding) multibase,
     *   with or without the `bls12_381-g2-pub` multicodec prefix (`0xeb 0x01`)
     * - `publicKeyJwk`: JWK with the raw key in the base64url `x` coordinate
     */
    private fun extractBlsPublicKey(method: VerificationMethod): ByteArray? {
        method.publicKeyMultibase
            ?.let { decodeMultibase(it) }
            ?.let { stripBls12381G2MulticodecPrefix(it) }
            ?.let { return it }

        val jwkX = method.publicKeyJwk?.get("x") as? String ?: return null
        val decoded = try {
            BbsCryptoSuite.decodeBase64Url(jwkX)
        } catch (_: Exception) {
            null
        }
        return decoded?.takeIf { it.size == BLS_G2_PUBLIC_KEY_SIZE }
    }

    private fun decodeMultibase(value: String): ByteArray? = try {
        when {
            value.startsWith("z") -> value.substring(1).decodeBase58()
            value.startsWith("u") -> BbsCryptoSuite.decodeBase64Url(value.substring(1))
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun stripBls12381G2MulticodecPrefix(bytes: ByteArray): ByteArray? = when {
        bytes.size == BLS_G2_PUBLIC_KEY_SIZE -> bytes
        bytes.size == BLS_G2_PUBLIC_KEY_SIZE + 2 &&
            bytes[0] == MULTICODEC_BLS12_381_G2_PUB_PREFIX[0] &&
            bytes[1] == MULTICODEC_BLS12_381_G2_PUB_PREFIX[1] -> bytes.copyOfRange(2, bytes.size)
        else -> null
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

    companion object {
        /** Size in bytes of a compressed BLS12-381 G2 public key. */
        const val BLS_G2_PUBLIC_KEY_SIZE = 96

        /** Multicodec varint prefix for `bls12_381-g2-pub` (0xeb), per the multicodec table. */
        private val MULTICODEC_BLS12_381_G2_PUB_PREFIX = byteArrayOf(0xEB.toByte(), 0x01)
    }
}
