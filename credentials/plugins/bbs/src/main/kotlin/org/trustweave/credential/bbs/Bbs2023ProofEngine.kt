package org.trustweave.credential.bbs

import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import org.slf4j.LoggerFactory
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
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
import org.trustweave.core.exception.ConfigException
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.util.decodeBase58
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import java.util.UUID

/**
 * W3C Data Integrity BBS Cryptosuite 2023 proof engine.
 *
 * Implements base BBS-2023 credential signing and verification following the W3C BBS-2023
 * cryptosuite data formats over the BLS12-381 curve. Selective-disclosure derived proofs
 * are NOT supported by the underlying BBS implementation (see below).
 *
 * **Capabilities:**
 * - Issuance: signs all credential claims as a BBS+ signature over the claim set
 * - Verification: verifies the BBS+ signature against the public key resolved from the
 *   issuer's DID document (never from key material embedded in the proof itself)
 * - Presentation / selective disclosure: **not supported** — see below
 *
 * **Configuration via [ProofEngineConfig]:**
 * | Key           | Type               | Description                                       |
 * |---------------|--------------------|---------------------------------------------------|
 * | `keyPair`     | [Bls12381KeyPair]  | Signing key pair (required for issuance)          |
 * | `didResolver` | `DidResolver`      | Issuer DID resolver (required for verification);  |
 * |               |                    | also accepted via [ProofEngineConfig.didResolver] |
 *
 * If no `keyPair` is configured, [issue] fails with a [ConfigException]. Signing keys are
 * never fabricated: a credential signed with an ephemeral key that is published in no DID
 * document is permanently unverifiable by any third party. Verification is unaffected — it
 * resolves the issuer key from the issuer's DID document.
 *
 * **Claim message encoding (breaking change, security fix):** each claim is signed as the
 * injective byte message `len(keyBytes) || keyBytes || len(valueBytes) || valueBytes`
 * (4-byte big-endian lengths, UTF-8 bytes); see [encodeClaimsAsMessages]. Earlier releases
 * signed the ambiguous string `"key=value"`, under which distinct claim pairs such as
 * `("a", "b=c")` and `("a=b", "c")` produced the same signed message and could be swapped
 * without invalidating the signature (claim forgery). Credentials issued with the legacy
 * encoding intentionally no longer verify — their signatures never unambiguously committed
 * to the claims and were forgeable.
 *
 * **Derived proofs / presentations are not supported:** the underlying BBS implementation
 * ([BbsCryptoSuite]) is an HMAC-based emulation — no real BLS12-381 pairing library is on
 * the classpath — and it cannot produce or cryptographically verify unlinkable derived
 * proofs (its `verifyDerivedProof` accepts any non-zero proof bytes). Therefore
 * [createPresentation] fails fast with [UnsupportedOperationException] instead of emitting
 * unverifiable pseudo-proofs, and [verify] rejects `cryptosuite=bbs-2023-derived` proofs
 * with an explicit "not supported by the underlying BBS implementation" reason.
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
     * The explicitly configured signing key pair, or `null` when the engine is
     * verification-only. Ephemeral keys are never generated: an issuer key that no
     * verifier can resolve from a DID document produces permanently unverifiable
     * credentials, so [issue] fails fast instead.
     */
    private val configuredKeyPair: Bls12381KeyPair? =
        config.properties["keyPair"] as? Bls12381KeyPair

    override val format = ProofSuiteId.BBS_2023
    override val formatName = "W3C Data Integrity BBS Cryptosuite 2023"
    override val formatVersion = "1.0"

    // Selective disclosure / ZK / presentation are advertised as unsupported: the
    // HMAC-based BbsCryptoSuite emulation cannot produce or verify real BBS derived
    // proofs, and this engine refuses to emit unverifiable pseudo-proofs (see class KDoc).
    override val capabilities = ProofEngineCapabilities(
        selectiveDisclosure = false,
        zeroKnowledge = false,
        revocation = true,
        presentation = false,
        predicates = false,
    )

    // -------------------------------------------------------------------------
    // Issue
    // -------------------------------------------------------------------------

    /**
     * Issue a BBS-2023 credential signed with the configured issuer key pair.
     *
     * @throws ConfigException if no `keyPair` is configured — ephemeral signing keys are
     *   never fabricated, because a credential signed with a key absent from every DID
     *   document can never be verified by anyone
     */
    override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
        require(request.format == format) {
            "Request format ${request.format.value} does not match engine format ${format.value}"
        }

        val keyPair = configuredKeyPair ?: throw ConfigException.InvalidFormat(
            parseError = "No BLS12-381 key pair configured for BBS-2023 issuance. " +
                "Provide config.properties[\"keyPair\"] (a Bls12381KeyPair whose public key " +
                "is published in the issuer's DID document). Ephemeral signing keys are " +
                "never fabricated: a credential signed with a key that no verifier can " +
                "resolve is permanently unverifiable.",
            field = "keyPair",
        )

        // Encode each claim as an injective length-prefixed message (see encodeClaimsAsMessages)
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
                "cryptosuite" to JsonPrimitive(BASE_CRYPTOSUITE),
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
        if (cryptosuite == DERIVED_CRYPTOSUITE) {
            // Honest, explicit rejection (not a generic cryptosuite mismatch): the
            // underlying BBS implementation is an HMAC-based emulation without real
            // BLS12-381 pairings and cannot cryptographically verify derived proofs.
            return invalidProof(
                credential,
                "Derived-proof verification (cryptosuite=$DERIVED_CRYPTOSUITE) is not " +
                    "supported by the underlying BBS implementation: BbsCryptoSuite is an " +
                    "HMAC-based emulation without real BLS12-381 pairing operations and " +
                    "cannot cryptographically verify unlinkable derived proofs. This engine " +
                    "also refuses to create such proofs (createPresentation fails fast). " +
                    "Integrate a real BBS library to enable selective disclosure.",
            )
        }
        if (cryptosuite != BASE_CRYPTOSUITE) {
            return invalidProof(
                credential,
                "Expected cryptosuite=$BASE_CRYPTOSUITE but got: $cryptosuite",
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
                        "cryptosuite" to JsonPrimitive(BASE_CRYPTOSUITE),
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
    // Create presentation (selective disclosure) — unsupported, fails fast
    // -------------------------------------------------------------------------

    /**
     * BBS selective-disclosure presentations are **not supported** by this engine.
     *
     * Real BBS proof derivation requires the original signature plus the ISSUER's public
     * key and BLS12-381 pairing operations. The underlying implementation
     * ([BbsCryptoSuite]) is an HMAC-based emulation: the "derived proofs" it can produce
     * are not real BBS proofs — no verifier (including this engine) can cryptographically
     * validate them, and its `verifyDerivedProof` accepts any non-zero proof bytes.
     * Earlier releases silently derived such pseudo-proofs (and additionally keyed them
     * with the HOLDER's key instead of the issuer's); this method now fails fast instead
     * of emitting unverifiable presentations.
     *
     * @throws UnsupportedOperationException always
     */
    override suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest,
    ): VerifiablePresentation {
        throw UnsupportedOperationException(
            "BBS-2023 derived-proof presentations are not supported by the underlying BBS " +
                "implementation: BbsCryptoSuite is an HMAC-based emulation without real " +
                "BLS12-381 pairing operations, so the derived proofs it would produce are " +
                "not verifiable BBS proofs (no verifier can cryptographically validate " +
                "them). Failing fast instead of emitting unverifiable presentations. " +
                "Integrate a real BBS/BLS12-381 library to enable selective disclosure.",
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
     * Encode a [CredentialSubject]'s claims as byte messages for BBS+ signing.
     *
     * Each entry (keys sorted for determinism) is encoded injectively as:
     *
     * ```
     * len(keyBytes) || keyBytes || len(valueBytes) || valueBytes
     * ```
     *
     * where `len(x)` is the length of `x` as an unsigned 4-byte big-endian integer,
     * `keyBytes` is the UTF-8 encoding of the claim key, and `valueBytes` is the UTF-8
     * encoding of the claim value's canonical JSON serialisation
     * ([kotlinx.serialization.json.JsonElement.toString]). Because every component is
     * length-prefixed, the encoding is injective: distinct `(key, value)` pairs always
     * produce distinct messages, so a signature commits unambiguously to each claim.
     *
     * **Breaking change (security fix):** earlier releases encoded each claim as the
     * ambiguous UTF-8 string `"key=value"`, under which distinct claim pairs — e.g.
     * `("a", "b=c")` and `("a=b", "c")` — could produce identical signed messages,
     * allowing claim forgery without invalidating the signature. Credentials issued with
     * the legacy encoding intentionally no longer verify: their signatures never
     * unambiguously committed to the claims and were forgeable.
     */
    private fun encodeClaimsAsMessages(subject: CredentialSubject): List<ByteArray> =
        subject.claims.entries.sortedBy { it.key }.map { (k, v) ->
            encodeClaimMessage(k, v.toString())
        }

    /** Injective `len || key || len || value` encoding of a single claim (see above). */
    private fun encodeClaimMessage(key: String, value: String): ByteArray {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        return bigEndianLength(keyBytes.size) + keyBytes +
            bigEndianLength(valueBytes.size) + valueBytes
    }

    /** Encode [size] as an unsigned 4-byte big-endian length prefix. */
    private fun bigEndianLength(size: Int): ByteArray = byteArrayOf(
        (size ushr 24).toByte(),
        (size ushr 16).toByte(),
        (size ushr 8).toByte(),
        size.toByte(),
    )

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
        /** Data Integrity cryptosuite identifier for base BBS-2023 proofs. */
        const val BASE_CRYPTOSUITE = "bbs-2023"

        /**
         * Cryptosuite identifier earlier releases stamped on derived (selective-disclosure)
         * proofs. Such proofs are explicitly rejected by [verify]: the underlying
         * HMAC-emulated [BbsCryptoSuite] cannot cryptographically verify them.
         */
        const val DERIVED_CRYPTOSUITE = "bbs-2023-derived"

        /** Size in bytes of a compressed BLS12-381 G2 public key. */
        const val BLS_G2_PUBLIC_KEY_SIZE = 96

        /** Multicodec varint prefix for `bls12_381-g2-pub` (0xeb), per the multicodec table. */
        private val MULTICODEC_BLS12_381_G2_PUB_PREFIX = byteArrayOf(0xEB.toByte(), 0x01)
    }
}
