package org.trustweave.credential.vcapi

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.internal.SecurityConstants
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.proof.ProofOptions
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.credential.vcapi.dto.ChallengeResponse
import org.trustweave.credential.vcapi.dto.IssueCredentialRequest
import org.trustweave.credential.vcapi.dto.IssueCredentialResponse
import org.trustweave.credential.vcapi.dto.ProvePresentationRequest
import org.trustweave.credential.vcapi.dto.ProvePresentationResponse
import org.trustweave.credential.vcapi.dto.VcApiErrorResponse
import org.trustweave.credential.vcapi.dto.VerifyCredentialRequest
import org.trustweave.credential.vcapi.dto.VerifyCredentialResponse
import org.trustweave.credential.vcapi.dto.VerifyPresentationRequest
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId

/** Internal Json used for VerifiableCredential serialization within route handlers. */
private val vcJson =
    Json {
        serializersModule = SerializationModule.default
        ignoreUnknownKeys = true
        prettyPrint = true
    }

/**
 * Configures W3C VC API routes on the given [Routing] scope.
 *
 * - `POST /credentials/issue`
 * - `POST /credentials/verify`
 * - `POST /presentations/prove`
 * - `POST /presentations/verify`
 * - `POST /presentations/challenge` (issues a one-time challenge for `/presentations/verify`)
 *
 * `/presentations/verify` enforces holder binding and a one-time, server-issued challenge by default;
 * [verificationPolicy] relaxes either.
 *
 * **`verified: true` means what was checked, not that the issuer is trusted.** Without a
 * [trustEvaluator] the verify endpoints check the proof, expiry and (optionally) revocation, and
 * nothing about whether the issuer is one the caller should believe: any DID can sign a credential
 * that verifies. The response says so explicitly, with a `trust:not-evaluated` entry in `checks` and
 * a warning, so a client cannot mistake a signature-only result for a trust decision. Pass a
 * [trustEvaluator] to have the issuer judged as well (`trust:evaluated`).
 */
@JvmOverloads
fun Routing.configureVcApiRoutes(
    service: CredentialService,
    trustEvaluator: TrustEvaluator? = null,
    verificationPolicy: VcApiVerificationPolicy = VcApiVerificationPolicy(),
) {
    /**
     * POST /credentials/issue
     *
     * Issues a new Verifiable Credential.
     */
    post("/credentials/issue") {
        try {
            val body = call.receive<IssueCredentialRequest>()
            val request = buildIssuanceRequest(body)
            when (val result = service.issue(request)) {
                is IssuanceResult.Success -> {
                    val vcJson = serializeVc(result.credential)
                    call.respond(HttpStatusCode.Created, IssueCredentialResponse(vcJson))
                }
                is IssuanceResult.Failure -> {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        VcApiErrorResponse("ISSUANCE_FAILED", result.errors.joinToString("; ")),
                    )
                }
            }
        } catch (e: Exception) {
            call.respondRequestFailure(e)
        }
    }

    /**
     * POST /credentials/verify
     *
     * Verifies a Verifiable Credential.
     */
    post("/credentials/verify") {
        try {
            val body = call.receive<VerifyCredentialRequest>()
            val credential = deserializeVc(body.verifiableCredential)
            val options =
                VerificationOptions(
                    checkRevocation = body.options?.checkRevocation ?: true,
                    checkExpiration = body.options?.checkExpiration ?: true,
                    verifyChallenge = body.options?.challenge != null,
                    expectedChallenge = body.options?.challenge,
                    verifyDomain = body.options?.domain != null,
                    expectedDomain = body.options?.domain,
                )
            val result = service.verify(credential, trustEvaluator, options)
            call.respond(HttpStatusCode.OK, result.toVerifyResponse(trustEvaluator != null))
        } catch (e: Exception) {
            call.respondRequestFailure(e)
        }
    }

    /**
     * POST /presentations/prove
     *
     * Assembles and signs a Verifiable Presentation.
     */
    post("/presentations/prove") {
        try {
            val body = call.receive<ProvePresentationRequest>()
            val credentials = parsePresentationCredentials(body.presentation)
            val proofOptions =
                body.options?.let {
                    ProofOptions(
                        verificationMethod = it.verificationMethod,
                        additionalOptions =
                            buildMap {
                                it.challenge?.let { c -> put("challenge", c) }
                                it.domain?.let { d -> put("domain", d) }
                            },
                    )
                }
            val request = PresentationRequest(proofOptions = proofOptions)
            val vp = service.createPresentation(credentials, request)
            call.respond(HttpStatusCode.Created, ProvePresentationResponse(serializeVp(vp)))
        } catch (e: Exception) {
            call.respondRequestFailure(e)
        }
    }

    /**
     * POST /presentations/verify
     *
     * Verifies a Verifiable Presentation.
     */
    post("/presentations/verify") {
        try {
            val body = call.receive<VerifyPresentationRequest>()
            val vp = deserializeVp(body.verifiablePresentation)
            val challenge = body.options?.challenge
            val store = verificationPolicy.challengeStore
            if (store != null) {
                if (challenge == null && verificationPolicy.requireChallenge) {
                    call.respond(
                        HttpStatusCode.OK,
                        rejected("A challenge issued by POST /presentations/challenge is required"),
                    )
                    return@post
                }
                // Spent before verification so two concurrent requests cannot both win; an unknown,
                // expired or already-used challenge never reaches the verifier.
                if (challenge != null && !store.consume(challenge)) {
                    call.respond(
                        HttpStatusCode.OK,
                        rejected("The challenge is unknown, expired or has already been used"),
                    )
                    return@post
                }
            }
            val options =
                VerificationOptions(
                    verifyPresentationProof = true,
                    verifyChallenge = challenge != null,
                    expectedChallenge = challenge,
                    verifyDomain = body.options?.domain != null,
                    expectedDomain = body.options?.domain,
                    checkRevocation = body.options?.checkRevocation ?: true,
                    checkExpiration = body.options?.checkExpiration ?: true,
                    enforceHolderBinding = verificationPolicy.enforceHolderBinding,
                )
            val result = service.verifyPresentation(vp, trustEvaluator, options)
            call.respond(HttpStatusCode.OK, result.toVerifyResponse(trustEvaluator != null))
        } catch (e: Exception) {
            call.respondRequestFailure(e)
        }
    }

    /**
     * POST /presentations/challenge
     *
     * Issues a single-use challenge. The holder signs it into the presentation and the verifier passes
     * it back as `options.challenge` to `/presentations/verify`, which consumes it.
     */
    post("/presentations/challenge") {
        val store = verificationPolicy.challengeStore
        if (store == null) {
            call.respond(
                HttpStatusCode.NotFound,
                VcApiErrorResponse("NOT_FOUND", "Challenge issuing is not enabled"),
            )
            return@post
        }
        try {
            val issued = store.issue()
            call.respond(HttpStatusCode.Created, ChallengeResponse(issued.challenge, issued.expiresAt.toString()))
        } catch (e: IllegalStateException) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                VcApiErrorResponse("UNAVAILABLE", "Too many outstanding challenges; retry later"),
            )
        }
    }
}

private fun rejected(error: String) = VerifyCredentialResponse(verified = false, errors = listOf(error))

// ---------------------------------------------------------------------------
// Mapping helpers
// ---------------------------------------------------------------------------

private fun buildIssuanceRequest(body: IssueCredentialRequest): IssuanceRequest {
    val cred = body.credential
    val opts = body.options

    val issuerStr =
        when (val iss = cred["issuer"]) {
            is JsonPrimitive -> iss.content
            is JsonObject ->
                iss["id"]?.jsonPrimitive?.content
                    ?: error("issuer.id is required")
            else -> error("'issuer' field is required")
        }

    val subjectJson =
        cred["credentialSubject"]?.jsonObject
            ?: error("'credentialSubject' field is required")
    val subjectId =
        subjectJson["id"]?.jsonPrimitive?.content
            ?: error("'credentialSubject.id' field is required")
    val claims = subjectJson.filterKeys { it != "id" }

    val types =
        when (val t = cred["type"]) {
            is JsonArray -> t.map { CredentialType.fromString(it.jsonPrimitive.content) }
            is JsonPrimitive -> listOf(CredentialType.fromString(t.content))
            else -> listOf(CredentialType.fromString("VerifiableCredential"))
        }

    val format =
        when (opts?.format?.lowercase()) {
            "vc-jwt" -> ProofSuiteId.VC_JWT
            "sd-jwt-vc" -> ProofSuiteId.SD_JWT_VC
            else -> ProofSuiteId.VC_LD
        }

    val issuerKeyId =
        opts?.verificationMethod?.let { vm ->
            runCatching { VerificationMethodId.parse(vm) }.getOrNull()
        }

    val proofOptions =
        if (opts?.challenge != null || opts?.domain != null || opts?.verificationMethod != null) {
            ProofOptions(
                verificationMethod = opts.verificationMethod,
                additionalOptions =
                    buildMap {
                        opts.challenge?.let { put("challenge", it) }
                        opts.domain?.let { put("domain", it) }
                    },
            )
        } else {
            null
        }

    return IssuanceRequest(
        format = format,
        issuer = Issuer.fromDid(Did(issuerStr)),
        issuerKeyId = issuerKeyId,
        credentialSubject =
            CredentialSubject(
                id = Iri(subjectId),
                claims = claims,
            ),
        type = types,
        proofOptions = proofOptions,
    )
}

private fun parsePresentationCredentials(presentationJson: JsonObject): List<VerifiableCredential> {
    requireWithinSizeLimit(
        presentationJson,
        SecurityConstants.MAX_PRESENTATION_SIZE_BYTES,
        "Presentation",
    )
    val vcs = presentationJson["verifiableCredential"] ?: return emptyList()
    return when (vcs) {
        // A credential that cannot be parsed fails the request. Dropping it would sign a presentation
        // that silently lacks something the caller asked to present.
        is JsonArray ->
            vcs.mapIndexed { index, element ->
                try {
                    deserializeVc(element.jsonObject)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ClientFault) {
                    throw e
                } catch (e: Exception) {
                    throw ClientFault("verifiableCredential[$index] is not a valid credential")
                }
            }
        is JsonObject -> listOf(deserializeVc(vcs))
        else -> emptyList()
    }
}

private fun serializeVc(vc: VerifiableCredential): JsonObject = vcJson.encodeToJsonElement(VerifiableCredential.serializer(), vc).jsonObject

/**
 * Rejects a document larger than [limit] bytes before it is decoded.
 *
 * These routes decode straight through the kotlinx serializer, so the cap applied at
 * `JsonObject.toCredential()` never runs on this path — and this is the path where untrusted
 * documents actually arrive, over the network, from callers this server does not authenticate.
 */
private fun requireWithinSizeLimit(
    json: JsonObject,
    limit: Int,
    what: String,
) {
    val sizeBytes = json.toString().toByteArray(Charsets.UTF_8).size
    if (sizeBytes > limit) throw ClientFault("$what exceeds the maximum of $limit bytes: $sizeBytes bytes")
}

private fun deserializeVc(json: JsonObject): VerifiableCredential {
    requireWithinSizeLimit(json, SecurityConstants.MAX_CREDENTIAL_SIZE_BYTES, "Credential")
    return vcJson.decodeFromJsonElement(VerifiableCredential.serializer(), json)
}

private fun serializeVp(vp: VerifiablePresentation): JsonObject =
    vcJson.encodeToJsonElement(VerifiablePresentation.serializer(), vp).jsonObject

private fun deserializeVp(json: JsonObject): VerifiablePresentation {
    requireWithinSizeLimit(json, SecurityConstants.MAX_PRESENTATION_SIZE_BYTES, "Presentation")
    return vcJson.decodeFromJsonElement(VerifiablePresentation.serializer(), json)
}

/** A request fault whose message is safe, and useful, to show the caller. Anything else is not. */
private class ClientFault(
    message: String,
) : Exception(message)

/**
 * Answers a failed request. Only a [ClientFault] message reaches the caller; every other exception
 * is logged here and answered with a fixed message, because its text (parser positions, class
 * names, resolver or KMS details) describes the server rather than the request.
 */
private suspend fun ApplicationCall.respondRequestFailure(e: Exception) {
    if (e is CancellationException) throw e
    val message =
        if (e is ClientFault) {
            e.message ?: GENERIC_FAILURE
        } else {
            application.log.warn("VC API request rejected: ${e::class.simpleName}: ${e.message}", e)
            GENERIC_FAILURE
        }
    respond(HttpStatusCode.BadRequest, VcApiErrorResponse("INVALID_REQUEST", message))
}

private const val GENERIC_FAILURE = "The request could not be processed"

private const val TRUST_NOT_EVALUATED = "trust:not-evaluated"
private const val TRUST_EVALUATED = "trust:evaluated"
private const val TRUST_NOT_EVALUATED_WARNING =
    "Issuer trust was not evaluated: verified reflects the proof and enabled checks only, not whether the issuer is trusted"

private fun VerificationResult.toVerifyResponse(trustEvaluated: Boolean): VerifyCredentialResponse {
    val trustCheck = if (trustEvaluated) TRUST_EVALUATED else TRUST_NOT_EVALUATED
    val trustWarning = if (trustEvaluated) emptyList() else listOf(TRUST_NOT_EVALUATED_WARNING)
    return when (this) {
        is VerificationResult.Valid ->
            VerifyCredentialResponse(
                verified = true,
                checks = listOf("proof", trustCheck),
                warnings = warnings + trustWarning,
            )
        is VerificationResult.Invalid ->
            VerifyCredentialResponse(
                verified = false,
                checks = listOf(trustCheck),
                errors = allErrors,
                warnings = allWarnings + trustWarning,
            )
    }
}
