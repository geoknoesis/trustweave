package org.trustweave.credential.mdl.engine

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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

/**
 * ISO 18013-5 mDL/mDoc proof engine.
 *
 * Implements issuance and verification of ISO 18013-5 Mobile Driving Licence credentials.
 * Uses CBOR/COSE encoding and Bouncy Castle for cryptographic operations.
 */
class MdocProofEngine(
    private val kms: KeyManagementService,
    private val statusChecker: CredentialStatusChecker? = null,
    private val config: ProofEngineConfig = ProofEngineConfig(),
) : ProofEngine {

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
        request.credentialSubject.id?.value?.let { claims["subject_id"] = it }
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
     * Verifies COSE_Sign1 on the MSO and checks that all presented claim digests
     * match the values recorded in the MSO's valueDigests.
     *
     * The `options.verificationKeyId` or `options.additionalOptions["verifierKeyId"]` must
     * identify the KMS key holding the issuer's public key.
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

            val verifierKeyId = KeyId(credential.issuer.id.value)

            // Verify COSE_Sign1 — returns MSO payload bytes
            val msoBytes = MdocCoseSign.verify(issuerAuthBytes, kms, verifierKeyId)
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

            // Verify item digests against MSO valueDigests
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
                }
            }

            // Device authentication verification (ISO 18013-5 §9.1.3)
            val deviceSigned = doc.deviceSigned
            if (deviceSigned != null && deviceSigned.deviceAuth.deviceSignature != null) {
                val sessionTranscript = options.additionalOptions["sessionTranscript"] as? ByteArray
                if (sessionTranscript != null) {
                    val deviceAuthValid = MdocCoseSign.verifyDeviceAuth(
                        coseSign1Bytes = deviceSigned.deviceAuth.deviceSignature,
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
                }
                // If no sessionTranscript is provided, device auth is skipped (issuer-only verification)
            }

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
     * For mDoc/OID4VP flows, the presentation wraps the credentials without re-signing;
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

    private fun jsonElementToAny(element: kotlinx.serialization.json.JsonElement): Any =
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
