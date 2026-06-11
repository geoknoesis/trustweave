package org.trustweave.credential.mdl.engine

import com.upokecenter.cbor.CBORObject
import com.upokecenter.cbor.CBORType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.mdl.MdlNamespace
import org.trustweave.credential.mdl.MdocException
import org.trustweave.credential.mdl.model.*
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.*
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.credential.spi.status.CredentialStatusChecker
import org.trustweave.credential.spi.status.CredentialStatusCheckResult
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import java.io.ByteArrayInputStream
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

/**
 * ISO 18013-5 mDL/mDoc proof engine.
 *
 * Implements issuance and verification of ISO 18013-5 Mobile Driving Licence credentials
 * using CBOR/COSE encoding.
 *
 * ## Trust model (read this before deploying)
 *
 * The authoritative data of an mDoc credential is the CBOR `DeviceResponse` carried in
 * [CredentialProof.MdocProof]; the surrounding [VerifiableCredential] envelope (issuer,
 * dates, `credentialSubject`) is an UNSIGNED convenience view. Verification therefore:
 *
 * 1. **Issuer authentication** — the COSE_Sign1 `issuerAuth` signature over the MSO is
 *    verified using exactly one of two mechanisms, in this order:
 *    - **x5chain (preferred)**: when the COSE header carries an `x5chain` (header label 33),
 *      the signature is verified against the leaf certificate's public key and the chain is
 *      validated (PKIX, revocation checking disabled) against the IACA trust anchors supplied
 *      via `ProofEngineConfig.properties[`[OPTION_IACA_TRUST_ANCHORS]`]`
 *      (a `Collection<X509Certificate>`). If an x5chain is present but **no anchors are
 *      configured, verification fails closed**. NOTE: this is *not* full ISO 18013-5 IACA
 *      profile validation — certificate policy, key-usage, and DocSigner-specific constraints
 *      are NOT checked yet; only signature correctness, certificate validity periods, and
 *      PKIX path validation to a configured anchor.
 *    - **KMS lookup (explicit opt-in, tests/controlled deployments only)**: when no x5chain
 *      is present AND `ProofEngineConfig.properties[`[OPTION_ALLOW_KMS_ISSUER_KEY_LOOKUP]`]`
 *      is `true`, the issuer public key is fetched from the verifier's own KMS under
 *      `KeyId(credential.issuer.id)`. The issuer IRI is attacker-chosen data, so this is
 *      only sound when the verifier's KMS exclusively contains keys of trusted issuers
 *      under those exact ids. It is NEVER enabled by default.
 *    - Neither mechanism available → verification fails closed.
 *
 * 2. **Item integrity** — every presented `IssuerSignedItem` digest must match the MSO's
 *    `valueDigests`.
 *
 * 3. **Envelope reconciliation** — every claim in the unsigned envelope
 *    `credentialSubject.claims` must match (name AND value) a digest-verified issuer-signed
 *    item, otherwise verification fails. See [cborValueMatchesJson] for the CBOR-to-JSON
 *    value comparison rules and their limitations.
 *
 * 4. **Device (holder) authentication** — when the document carries `deviceSigned` data and
 *    the verification options request presentation/holder verification
 *    ([VerificationOptions.verifyPresentationProof] or
 *    [VerificationOptions.enforceHolderBinding]), the `sessionTranscript` additional option
 *    ([OPTION_SESSION_TRANSCRIPT]) is REQUIRED and the device signature is verified against
 *    the MSO's device key; a missing transcript fails verification. Only when the verifier
 *    explicitly disables both presentation flags is device authentication skipped
 *    (issuer-only verification of a stored credential).
 */
class MdocProofEngine(
    private val kms: KeyManagementService,
    private val statusChecker: CredentialStatusChecker? = null,
    private val config: ProofEngineConfig = ProofEngineConfig(),
) : ProofEngine {

    companion object {
        /**
         * `ProofEngineConfig.properties` key (Boolean) explicitly opting in to resolving the
         * issuer's COSE verification key from the verifier's KMS under
         * `KeyId(credential.issuer.id)` when no `x5chain` is present.
         *
         * **Security**: the issuer id is attacker-controlled input. Enable this ONLY for
         * tests or controlled deployments where the verifier's KMS holds nothing but
         * trusted issuer keys under those exact ids. Disabled by default.
         */
        const val OPTION_ALLOW_KMS_ISSUER_KEY_LOOKUP = "allowKmsIssuerKeyLookup"

        /**
         * `ProofEngineConfig.properties` key holding the IACA trust anchors
         * (`Collection<X509Certificate>`) used to validate the COSE `x5chain`.
         */
        const val OPTION_IACA_TRUST_ANCHORS = "iacaTrustAnchors"

        /**
         * `VerificationOptions.additionalOptions` key holding the CBOR-encoded ISO 18013-5
         * SessionTranscript (`ByteArray`) required for device authentication.
         */
        const val OPTION_SESSION_TRANSCRIPT = "sessionTranscript"

        /** Issuer-signed item name under which the credential subject id is recorded. */
        private const val SUBJECT_ID_ELEMENT = "subject_id"

        // COSE constants (RFC 8152 / RFC 9360)
        private const val HEADER_ALG = 1
        private const val HEADER_X5CHAIN = 33
        private const val ALG_ES256 = -7
        private const val ALG_ES384 = -35
        private const val ALG_ES512 = -36
        private const val ALG_EDDSA = -8
    }

    override val format = ProofSuiteId.MDOC
    override val formatName = "ISO mDL/mDoc"
    override val formatVersion = "1.0"
    override val capabilities = ProofEngineCapabilities(
        selectiveDisclosure = true,
        zeroKnowledge = false,
        revocation = true,
        presentation = true,
        predicates = false
    )

    /**
     * Issue an ISO 18013-5 mDoc credential.
     *
     * The `proofOptions.additionalOptions` map may contain:
     * - `"namespace"` (String) — CBOR namespace for claims, defaults to ISO_18013_5_1
     * - `"docType"` (String) — mDoc docType, defaults to MDL_DOC_TYPE
     * - `"deviceKey"` (ByteArray) — CBOR-encoded COSE_Key for device binding
     * - `"algorithm"` (String) — signing algorithm, defaults to "P-256"
     * - `"validUntilSeconds"` (Long) — validity duration in seconds from now
     */
    override suspend fun issue(request: IssuanceRequest): VerifiableCredential {
        val opts = request.proofOptions?.additionalOptions ?: emptyMap()
        val namespace = opts["namespace"] as? String ?: MdlNamespace.ISO_18013_5_1
        val docType = opts["docType"] as? String
            ?: request.type.firstOrNull { it.value != "VerifiableCredential" }?.value
            ?: MdlNamespace.MDL_DOC_TYPE
        val deviceKeyBytes = opts["deviceKey"] as? ByteArray ?: ByteArray(0)
        val algorithmName = opts["algorithm"] as? String ?: "P-256"
        val algorithm = when (algorithmName.uppercase()) {
            "P-256", "ES256" -> Algorithm.P256
            "P-384", "ES384" -> Algorithm.P384
            "P-521", "ES512" -> Algorithm.P521
            "ED25519", "EDDSA" -> Algorithm.Ed25519
            else -> Algorithm.P256
        }

        val issuerKeyId = request.issuerKeyId?.keyId
            ?: throw MdocException("MISSING_ISSUER_KEY", "issuerKeyId is required for mDoc issuance")

        val now = Clock.System.now()
        val validFrom = request.validFrom ?: now
        val validUntil = request.validUntil ?: run {
            val seconds = opts["validUntilSeconds"] as? Long ?: (365L * 24 * 3600)
            Instant.fromEpochSeconds(now.epochSeconds + seconds)
        }

        // Build IssuerSignedItems from credential subject claims
        val claims = mutableMapOf<String, Any>()
        request.credentialSubject.id?.value?.let { claims[SUBJECT_ID_ELEMENT] = it }
        request.credentialSubject.claims.forEach { (key, jsonElement) ->
            claims[key] = jsonElementToAny(jsonElement)
        }

        val items = mutableListOf<IssuerSignedItem>()
        claims.entries.forEachIndexed { index, (key, value) ->
            items.add(
                IssuerSignedItem(
                    digestId = index,
                    random = MdocCbor.generateSalt(),
                    elementIdentifier = key,
                    elementValue = value
                )
            )
        }

        // Build valueDigests: namespace → digestId → SHA-256(encodedItem)
        val digests = items.associate { item ->
            item.digestId to MdocCbor.digestItem(MdocCbor.encodeIssuerSignedItem(item))
        }

        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = "SHA-256",
            valueDigests = mapOf(namespace to digests),
            deviceKeyInfo = DeviceKeyInfo(deviceKey = deviceKeyBytes),
            docType = docType,
            validityInfo = ValidityInfo(
                signed = now,
                validFrom = validFrom,
                validUntil = validUntil
            )
        )

        val msoBytes = MdocCbor.encodeMso(mso)
        val issuerAuthBytes = MdocCoseSign.sign(msoBytes, issuerKeyId, kms, algorithm)

        val mobileDoc = MobileDocument(
            docType = docType,
            issuerSigned = IssuerSigned(
                nameSpaces = mapOf(namespace to items),
                issuerAuth = issuerAuthBytes
            )
        )

        val deviceResponseBytes = MdocCbor.encodeMobileDocument(mobileDoc)

        return VerifiableCredential(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = request.type.ifEmpty { listOf(CredentialType.fromString("VerifiableCredential")) },
            issuer = request.issuer,
            issuanceDate = now,
            validFrom = validFrom,
            validUntil = validUntil,
            credentialSubject = request.credentialSubject,
            proof = CredentialProof.MdocProof(
                deviceResponse = deviceResponseBytes,
                docType = docType
            )
        )
    }

    /**
     * Verify an ISO 18013-5 mDoc credential.
     *
     * Performs, in order: issuer authentication (x5chain to configured IACA anchors, or the
     * explicitly opted-in KMS lookup — see the class KDoc for the trust model), docType and
     * temporal validity checks against the signed MSO, per-item digest verification,
     * reconciliation of the unsigned envelope `credentialSubject` against the verified
     * issuer-signed items, device (holder) authentication when requested by the options,
     * and the credential status check.
     *
     * Device authentication requires the CBOR SessionTranscript under
     * `options.additionalOptions[`[OPTION_SESSION_TRANSCRIPT]`]`; when the options request
     * presentation/holder verification and the document carries device-signed data, a
     * missing transcript FAILS verification (no silent skip).
     */
    override suspend fun verify(
        credential: VerifiableCredential,
        options: VerificationOptions
    ): VerificationResult {
        val mdocProof = credential.proof as? CredentialProof.MdocProof
            ?: return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Credential does not carry an MdocProof",
                errors = listOf("Expected MdocProof, got ${credential.proof?.javaClass?.simpleName}")
            )

        return try {
            val doc = MdocCbor.decodeMobileDocument(mdocProof.deviceResponse)
            val issuerAuthBytes = doc.issuerSigned.issuerAuth

            // Issuer authentication: x5chain (against configured IACA anchors) or the
            // explicitly opted-in KMS lookup. Fails closed when neither is available.
            val msoBytes = verifyIssuerAuth(issuerAuthBytes, credential.issuer.id.value)
            val mso = MdocCbor.decodeMso(msoBytes)

            // Check document type matches
            if (mso.docType != doc.docType) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "MSO docType '${mso.docType}' does not match document docType '${doc.docType}'"
                )
            }

            // Verify temporal validity
            val now = Clock.System.now()
            if (now < mso.validityInfo.validFrom) {
                return VerificationResult.Invalid.NotYetValid(
                    credential = credential,
                    validFrom = mso.validityInfo.validFrom
                )
            }
            if (now > mso.validityInfo.validUntil) {
                return VerificationResult.Invalid.Expired(
                    credential = credential,
                    expiredAt = mso.validityInfo.validUntil
                )
            }

            // Verify item digests against MSO valueDigests, collecting the verified items
            // for envelope reconciliation below.
            val verifiedItems = mutableListOf<IssuerSignedItem>()
            doc.issuerSigned.nameSpaces.forEach { (namespace, items) ->
                val namespaceDigests = mso.valueDigests[namespace]
                    ?: throw MdocException("UNKNOWN_NAMESPACE", "Namespace $namespace not found in MSO")
                items.forEach { item ->
                    val expectedDigest = namespaceDigests[item.digestId]
                        ?: throw MdocException.digestMismatch(namespace, item.digestId)
                    val actualDigest = MdocCbor.digestItem(MdocCbor.encodeIssuerSignedItem(item))
                    if (!expectedDigest.contentEquals(actualDigest)) {
                        throw MdocException.digestMismatch(namespace, item.digestId)
                    }
                    verifiedItems.add(item)
                }
            }

            // Envelope reconciliation: the unsigned credentialSubject must agree with the
            // digest-verified issuer-signed items (PEX matching and relying parties read
            // the envelope, so unbacked envelope claims are an attack vector).
            reconcileEnvelopeClaims(credential, verifiedItems)?.let { return it }

            // Device authentication (ISO 18013-5 §9.1.3).
            verifyDeviceAuthentication(credential, doc, mso, options)?.let { return it }

            // Revocation / suspension check
            val effectiveChecker = statusChecker
                ?: (config.properties["statusChecker"] as? CredentialStatusChecker)
            if (effectiveChecker != null && credential.credentialStatus != null) {
                when (val status = effectiveChecker.checkStatus(credential)) {
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
                issuerIri = credential.issuer.id,
                subjectIri = credential.credentialSubject.id,
                issuedAt = mso.validityInfo.signed,
                expiresAt = mso.validityInfo.validUntil
            )
        } catch (e: MdocException) {
            VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = e.message ?: "mDoc verification failed",
                errors = listOf(e.message ?: "Unknown mDoc error")
            )
        } catch (e: Exception) {
            VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "mDoc verification failed: ${e.message}",
                errors = listOf(e.message ?: "Unknown error")
            )
        }
    }

    /**
     * Create a VerifiablePresentation wrapping the mDoc credentials.
     *
     * When [PresentationRequest.disclosedClaims] is provided, BOTH the CBOR
     * `IssuerSignedItem`s and the unsigned envelope `credentialSubject.claims` are filtered
     * to the disclosed claims, so withheld claims do not leak through the envelope.
     *
     * For mDoc/OID4VP flows the presentation wraps the credentials without re-signing;
     * device authentication is handled separately in the DeviceResponse flow.
     */
    override suspend fun createPresentation(
        credentials: List<VerifiableCredential>,
        request: PresentationRequest
    ): VerifiablePresentation {
        val disclosedClaims = request.disclosedClaims

        val filteredCredentials = if (disclosedClaims == null) {
            credentials
        } else {
            credentials.map { vc ->
                val mdocProof = vc.proof as? CredentialProof.MdocProof ?: return@map vc
                val doc = MdocCbor.decodeMobileDocument(mdocProof.deviceResponse)
                val filteredNameSpaces = doc.issuerSigned.nameSpaces.mapValues { (_, items) ->
                    items.filter { it.elementIdentifier in disclosedClaims }
                }
                val filteredDoc = doc.copy(
                    issuerSigned = doc.issuerSigned.copy(nameSpaces = filteredNameSpaces)
                )
                vc.copy(
                    // Selective disclosure must also filter the unsigned envelope: keeping
                    // the full credentialSubject would leak every withheld claim to the
                    // verifier (and fail envelope reconciliation on verification).
                    credentialSubject = vc.credentialSubject.copy(
                        claims = vc.credentialSubject.claims.filterKeys { it in disclosedClaims }
                    ),
                    proof = CredentialProof.MdocProof(
                        deviceResponse = MdocCbor.encodeMobileDocument(filteredDoc),
                        docType = mdocProof.docType
                    )
                )
            }
        }

        val holderId = credentials.firstOrNull()?.credentialSubject?.id ?: Iri("urn:unknown")

        return VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = holderId,
            verifiableCredential = filteredCredentials
        )
    }

    // ---------------------------------------------------------------------------
    // Issuer authentication (x5chain / opt-in KMS lookup)
    // ---------------------------------------------------------------------------

    /**
     * Verify the issuerAuth COSE_Sign1 and return the MSO payload bytes.
     *
     * Trust resolution order (see class KDoc):
     * 1. x5chain present → leaf signature verification + PKIX validation to a configured
     *    IACA anchor; no anchors configured → fail closed.
     * 2. No x5chain → KMS lookup under the (attacker-chosen) issuer IRI, only when
     *    explicitly enabled via [OPTION_ALLOW_KMS_ISSUER_KEY_LOOKUP].
     */
    private suspend fun verifyIssuerAuth(issuerAuthBytes: ByteArray, issuerId: String): ByteArray {
        val x5chain = extractX5Chain(issuerAuthBytes)
        if (x5chain != null) {
            val anchors = iacaTrustAnchors()
            if (anchors.isEmpty()) {
                throw MdocException(
                    "IACA_ANCHORS_REQUIRED",
                    "issuerAuth carries an x5chain certificate chain but no IACA trust anchors " +
                        "are configured; configure IACA trust anchors via " +
                        "ProofEngineConfig.properties[\"$OPTION_IACA_TRUST_ANCHORS\"] " +
                        "(Collection<X509Certificate>) to verify certificate-based mdoc credentials"
                )
            }
            val certs = parseCertificates(x5chain)
            val leaf = certs.first()
            try {
                leaf.checkValidity()
            } catch (e: Exception) {
                throw MdocException(
                    "ISSUER_CERT_EXPIRED",
                    "Issuer (DocSigner) certificate from x5chain is not currently valid: ${e.message}"
                )
            }
            val payload = verifyCoseSign1Signature(issuerAuthBytes, leaf.publicKey)
            validateChainToAnchor(certs, anchors)
            return payload
        }

        if (config.properties[OPTION_ALLOW_KMS_ISSUER_KEY_LOOKUP] != true) {
            throw MdocException(
                "ISSUER_TRUST_NOT_CONFIGURED",
                "Cannot establish issuer trust: issuerAuth carries no x5chain certificate chain " +
                    "and KMS issuer-key lookup is disabled. Either present credentials with an " +
                    "x5chain (and configure IACA trust anchors via " +
                    "ProofEngineConfig.properties[\"$OPTION_IACA_TRUST_ANCHORS\"]), or — for tests " +
                    "and controlled deployments only — explicitly opt in to resolving the issuer " +
                    "key from the verifier's KMS via " +
                    "ProofEngineConfig.properties[\"$OPTION_ALLOW_KMS_ISSUER_KEY_LOOKUP\"] = true"
            )
        }
        // Explicitly opted-in: the verifier's KMS is trusted to hold ONLY trusted issuer
        // keys under exactly these ids (the issuer id is attacker-chosen input).
        return MdocCoseSign.verify(issuerAuthBytes, kms, KeyId(issuerId))
    }

    /** IACA trust anchors from engine config, empty when not configured. */
    private fun iacaTrustAnchors(): List<X509Certificate> =
        (config.properties[OPTION_IACA_TRUST_ANCHORS] as? Collection<*>)
            ?.filterIsInstance<X509Certificate>()
            ?: emptyList()

    /**
     * Extract the x5chain (COSE header label 33, RFC 9360) from the protected or
     * unprotected COSE_Sign1 header. Returns null when absent; throws when present but
     * malformed (fail closed rather than falling back to another trust path).
     */
    private fun extractX5Chain(coseSign1Bytes: ByteArray): List<ByteArray>? {
        val coseSign1 = CBORObject.DecodeFromBytes(coseSign1Bytes)
        if (coseSign1.type != CBORType.Array || coseSign1.size() != 4) {
            throw MdocException.invalidCoseSign1("Expected 4-element array")
        }
        val label = CBORObject.FromObject(HEADER_X5CHAIN)
        val protectedBytes = coseSign1[0].GetByteString()
        val protectedEntry = if (protectedBytes.isNotEmpty()) {
            CBORObject.DecodeFromBytes(protectedBytes).takeIf { it.type == CBORType.Map }?.get(label)
        } else {
            null
        }
        val unprotectedEntry = coseSign1[1].takeIf { it.type == CBORType.Map }?.get(label)
        val entry = protectedEntry ?: unprotectedEntry ?: return null
        return when (entry.type) {
            CBORType.ByteString -> listOf(entry.GetByteString())
            CBORType.Array -> {
                if (entry.size() == 0) {
                    throw MdocException("INVALID_X5CHAIN", "x5chain header is an empty array")
                }
                (0 until entry.size()).map { i ->
                    entry[i].takeIf { it.type == CBORType.ByteString }?.GetByteString()
                        ?: throw MdocException(
                            "INVALID_X5CHAIN",
                            "x5chain element $i is not a certificate byte string"
                        )
                }
            }
            else -> throw MdocException("INVALID_X5CHAIN", "x5chain header has unexpected CBOR type ${entry.type}")
        }
    }

    private fun parseCertificates(derCerts: List<ByteArray>): List<X509Certificate> {
        val cf = CertificateFactory.getInstance("X.509")
        return derCerts.mapIndexed { i, der ->
            try {
                cf.generateCertificate(ByteArrayInputStream(der)) as X509Certificate
            } catch (e: Exception) {
                throw MdocException("INVALID_X5CHAIN", "x5chain element $i is not a valid X.509 certificate: ${e.message}")
            }
        }
    }

    /**
     * Validate the x5chain to one of the configured IACA trust anchors using PKIX path
     * validation (revocation checking disabled — CRL/OCSP distribution is deployment
     * specific and out of scope for this engine).
     */
    private fun validateChainToAnchor(chain: List<X509Certificate>, anchors: List<X509Certificate>) {
        // A leaf that IS a configured anchor is trusted directly (its validity was checked).
        val pathCerts = chain.filter { cert -> anchors.none { it == cert } }
        if (pathCerts.isEmpty()) return
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val certPath = cf.generateCertPath(pathCerts)
            val params = PKIXParameters(anchors.map { TrustAnchor(it, null) }.toSet()).apply {
                isRevocationEnabled = false
            }
            CertPathValidator.getInstance("PKIX").validate(certPath, params)
        } catch (e: Exception) {
            throw MdocException(
                "UNTRUSTED_ISSUER_CERT",
                "x5chain does not validate to a configured IACA trust anchor: ${e.message}"
            )
        }
    }

    /**
     * Verify a COSE_Sign1 signature against [publicKey] and return the payload bytes.
     *
     * Self-contained equivalent of [MdocCoseSign.verify] for the x5chain path, where the
     * key comes from the leaf certificate instead of the KMS.
     */
    private fun verifyCoseSign1Signature(coseSign1Bytes: ByteArray, publicKey: PublicKey): ByteArray {
        val coseSign1 = CBORObject.DecodeFromBytes(coseSign1Bytes)
        if (coseSign1.size() != 4) throw MdocException.invalidCoseSign1("Expected 4-element array")

        val protectedHeaderBytes = coseSign1[0].GetByteString()
        val payloadBytes = coseSign1[2].GetByteString()
        val signature = coseSign1[3].GetByteString()

        val protectedHeaderMap = CBORObject.DecodeFromBytes(protectedHeaderBytes)
        val coseAlg = protectedHeaderMap[CBORObject.FromObject(HEADER_ALG)]?.AsInt32()
            ?: throw MdocException.invalidCoseSign1("Missing 'alg' in protected header")

        // Sig_structure = ["Signature1", protected-bstr, external-aad, payload]
        val sigStructure = CBORObject.NewArray()
        sigStructure.Add(CBORObject.FromObject("Signature1"))
        sigStructure.Add(CBORObject.FromObject(protectedHeaderBytes))
        sigStructure.Add(CBORObject.FromObject(ByteArray(0)))
        sigStructure.Add(CBORObject.FromObject(payloadBytes))
        val toBeSigned = sigStructure.EncodeToBytes()

        val (jcaAlgorithm, isEcdsa) = when (coseAlg) {
            ALG_ES256 -> "SHA256withECDSA" to true
            ALG_ES384 -> "SHA384withECDSA" to true
            ALG_ES512 -> "SHA512withECDSA" to true
            ALG_EDDSA -> "Ed25519" to false
            else -> throw MdocException.invalidCoseSign1("Unsupported COSE algorithm: $coseAlg")
        }

        val valid = try {
            val verifier = Signature.getInstance(jcaAlgorithm)
            verifier.initVerify(publicKey)
            verifier.update(toBeSigned)
            verifier.verify(if (isEcdsa) rawToDerSignature(signature) else signature)
        } catch (e: Exception) {
            false
        }
        if (!valid) {
            throw MdocException.invalidCoseSign1("Signature verification against x5chain leaf certificate failed")
        }
        return payloadBytes
    }

    /** Convert raw (r||s) ECDSA signature (COSE format) to DER encoding (JCA format). */
    private fun rawToDerSignature(rawSig: ByteArray): ByteArray {
        val half = rawSig.size / 2
        val r = rawSig.copyOfRange(0, half)
        val s = rawSig.copyOfRange(half, rawSig.size)

        fun encodeMpi(bytes: ByteArray): ByteArray {
            var b = bytes.dropWhile { it == 0.toByte() }.toByteArray()
            if (b.isEmpty()) b = byteArrayOf(0)
            if (b[0] < 0) b = byteArrayOf(0) + b
            return byteArrayOf(0x02, b.size.toByte()) + b
        }

        val rDer = encodeMpi(r)
        val sDer = encodeMpi(s)
        val seq = rDer + sDer
        return byteArrayOf(0x30, seq.size.toByte()) + seq
    }

    // ---------------------------------------------------------------------------
    // Envelope reconciliation (the signed CBOR is authoritative; the unsigned
    // envelope is only a convenience view and must agree with it)
    // ---------------------------------------------------------------------------

    /**
     * Reconciles every unsigned envelope `credentialSubject` claim against the
     * digest-verified issuer-signed items: an envelope claim whose name has no verified
     * item, or whose value does not match any verified item of that name, is rejected as
     * tampered. The envelope subject id is checked against the issuer-signed
     * [SUBJECT_ID_ELEMENT] item when that item is presented (it may legitimately be
     * filtered out of a selective-disclosure presentation while the envelope keeps the id).
     */
    private fun reconcileEnvelopeClaims(
        credential: VerifiableCredential,
        verifiedItems: List<IssuerSignedItem>,
    ): VerificationResult.Invalid.InvalidProof? {
        val verifiedByName: Map<String, List<Any>> =
            verifiedItems.groupBy({ it.elementIdentifier }, { it.elementValue })

        val signedSubjectIds = verifiedByName[SUBJECT_ID_ELEMENT]?.filterIsInstance<String>()
        val envelopeSubjectId = credential.credentialSubject.id?.value
        if (envelopeSubjectId != null && signedSubjectIds != null && envelopeSubjectId !in signedSubjectIds) {
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Envelope credentialSubject.id does not match the issuer-signed " +
                    "'$SUBJECT_ID_ELEMENT' item (possible envelope tampering)",
                errors = listOf(
                    "Envelope subject '$envelopeSubjectId' != signed subject_id '$signedSubjectIds'"
                ),
            )
        }

        for ((name, envelopeValue) in credential.credentialSubject.claims) {
            val signedValues = verifiedByName[name]
                ?: return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "Envelope claim '$name' is not backed by a verified issuer-signed item",
                    errors = listOf("Unbacked envelope claim: $name"),
                )
            if (signedValues.none { cborValueMatchesJson(it, envelopeValue) }) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "Envelope claim '$name' does not match the verified issuer-signed " +
                        "value (possible envelope tampering)",
                    errors = listOf(
                        "Envelope claim '$name' value '$envelopeValue' does not match any verified item"
                    ),
                )
            }
        }
        return null
    }

    /**
     * Compares a verified CBOR-decoded issuer-signed element value against a JSON envelope
     * claim value by normalizing the envelope value with EXACTLY the same conversion used
     * at issuance ([jsonElementToAny]) and comparing the results:
     *
     * - JSON primitives normalize to Boolean / Long / Double / String (in that order of
     *   preference, matching issuance); the signed CBOR value decodes to the same Kotlin
     *   types ([MdocCbor.fromCborValue]), so strings, booleans, integers and floats compare
     *   by value with no cross-type matches (Long never equals Double, Boolean never
     *   equals String, etc.).
     * - Complex JSON values (objects/arrays) normalize to their `JsonElement.toString()`
     *   text, as at issuance; the comparison therefore relies on kotlinx-serialization's
     *   stable element ordering — a semantically equal but reordered envelope object is
     *   rejected (fail closed).
     * - CBOR byte strings never match: binary values cannot be faithfully represented in
     *   the JSON envelope, and this engine's issuance never puts them there.
     *
     * Limitation: because issuance normalizes BEFORE signing, boolean/number-looking JSON
     * strings are indistinguishable from their typed counterparts (`"42"` and `42` both
     * issue — and hence verify — as integer 42). This mirrors the issuance behavior and is
     * inherent to the signed representation, not the comparison.
     */
    private fun cborValueMatchesJson(signed: Any, envelope: JsonElement): Boolean {
        val normalized = jsonElementToAny(envelope)
        return when (signed) {
            is ByteArray -> false
            is Long -> (normalized as? Long) == signed
            is Int -> (normalized as? Long) == signed.toLong()
            is Double -> (normalized as? Double) == signed
            is Float -> (normalized as? Double) == signed.toDouble()
            is Boolean, is String -> normalized == signed
            else -> false
        }
    }

    // ---------------------------------------------------------------------------
    // Device (holder) authentication
    // ---------------------------------------------------------------------------

    /**
     * Device authentication per ISO 18013-5 §9.1.3.
     *
     * Fail-closed rules:
     * - Session transcript provided → device-signed data with a COSE_Sign1 device signature
     *   must be present and must verify (COSE_Mac0-only device auth is not supported).
     * - Options request presentation/holder verification ([VerificationOptions
     *   .verifyPresentationProof] or [VerificationOptions.enforceHolderBinding]) and the
     *   document carries device-signed data → the session transcript is REQUIRED; its
     *   absence fails verification.
     * - Only when both presentation flags are disabled and no transcript is supplied is
     *   device auth skipped (documented issuer-only verification of a stored credential).
     */
    private fun verifyDeviceAuthentication(
        credential: VerifiableCredential,
        doc: MobileDocument,
        mso: MobileSecurityObject,
        options: VerificationOptions,
    ): VerificationResult.Invalid.InvalidProof? {
        val sessionTranscript = options.additionalOptions[OPTION_SESSION_TRANSCRIPT] as? ByteArray
        val presentationVerificationRequested =
            options.verifyPresentationProof || options.enforceHolderBinding
        val deviceSigned = doc.deviceSigned

        if (deviceSigned == null) {
            // No device-signed data: nothing to verify for a plain (issuer-only) credential.
            // But a verifier that supplied a session transcript is in a presentation flow
            // and expects device auth — a stripped deviceSigned must not pass silently.
            if (sessionTranscript != null && presentationVerificationRequested) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "Device authentication required (session transcript provided) but the " +
                        "document carries no deviceSigned data",
                    errors = listOf("Missing deviceSigned/deviceAuth in mdoc document"),
                )
            }
            return null
        }

        if (sessionTranscript == null) {
            if (presentationVerificationRequested) {
                return VerificationResult.Invalid.InvalidProof(
                    credential = credential,
                    reason = "Device authentication required but no session transcript was provided; " +
                        "pass the CBOR SessionTranscript via VerificationOptions.additionalOptions" +
                        "[\"$OPTION_SESSION_TRANSCRIPT\"], or explicitly disable presentation " +
                        "verification (verifyPresentationProof=false, enforceHolderBinding=false) " +
                        "for issuer-only verification",
                    errors = listOf("Missing '$OPTION_SESSION_TRANSCRIPT' verification option"),
                )
            }
            // Documented skip: issuer-only verification explicitly requested.
            return null
        }

        val deviceSignature = deviceSigned.deviceAuth.deviceSignature
            ?: return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Device authentication required but the document carries no COSE_Sign1 " +
                    "device signature (COSE_Mac0 device auth is not supported)",
                errors = listOf("Missing deviceAuth.deviceSignature"),
            )

        val deviceAuthValid = MdocCoseSign.verifyDeviceAuth(
            coseSign1Bytes = deviceSignature,
            sessionTranscript = sessionTranscript,
            docType = doc.docType,
            deviceNameSpacesBytes = deviceSigned.nameSpaces,
            deviceKeyBytes = mso.deviceKeyInfo.deviceKey,
        )
        if (!deviceAuthValid) {
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Device authentication signature verification failed",
                errors = listOf("COSE_Sign1 device signature invalid"),
            )
        }
        return null
    }

    private fun jsonElementToAny(element: JsonElement): Any =
        when (element) {
            is JsonPrimitive -> when {
                element.booleanOrNull != null -> element.booleanOrNull!!
                element.longOrNull != null -> element.longOrNull!!
                element.doubleOrNull != null -> element.doubleOrNull!!
                else -> element.content
            }
            else -> element.toString()
        }
}
