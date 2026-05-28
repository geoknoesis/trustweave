package org.trustweave.credential.vcapi

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
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
import org.trustweave.credential.vcapi.dto.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId

/** Internal Json used for VerifiableCredential serialization within route handlers. */
private val vcJson = Json {
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
 */
fun Routing.configureVcApiRoutes(service: CredentialService) {

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
            call.respond(
                HttpStatusCode.BadRequest,
                VcApiErrorResponse("INVALID_REQUEST", e.message ?: "Invalid request"),
            )
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
            val options = VerificationOptions(
                checkRevocation = body.options?.checkRevocation ?: true,
                checkExpiration = body.options?.checkExpiration ?: true,
                verifyChallenge = body.options?.challenge != null,
                expectedChallenge = body.options?.challenge,
                verifyDomain = body.options?.domain != null,
                expectedDomain = body.options?.domain,
            )
            val result = service.verify(credential, null, options)
            call.respond(HttpStatusCode.OK, result.toVerifyResponse())
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                VcApiErrorResponse("INVALID_REQUEST", e.message ?: "Invalid request"),
            )
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
            val proofOptions = body.options?.let {
                ProofOptions(
                    verificationMethod = it.verificationMethod,
                    additionalOptions = buildMap {
                        it.challenge?.let { c -> put("challenge", c) }
                        it.domain?.let { d -> put("domain", d) }
                    },
                )
            }
            val request = PresentationRequest(proofOptions = proofOptions)
            val vp = service.createPresentation(credentials, request)
            call.respond(HttpStatusCode.Created, ProvePresentationResponse(serializeVp(vp)))
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                VcApiErrorResponse("INVALID_REQUEST", e.message ?: "Invalid request"),
            )
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
            val options = VerificationOptions(
                verifyPresentationProof = true,
                verifyChallenge = body.options?.challenge != null,
                expectedChallenge = body.options?.challenge,
                verifyDomain = body.options?.domain != null,
                expectedDomain = body.options?.domain,
                checkRevocation = body.options?.checkRevocation ?: true,
                checkExpiration = body.options?.checkExpiration ?: true,
            )
            val result = service.verifyPresentation(vp, null, options)
            call.respond(HttpStatusCode.OK, result.toVerifyResponse())
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                VcApiErrorResponse("INVALID_REQUEST", e.message ?: "Invalid request"),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Mapping helpers
// ---------------------------------------------------------------------------

private fun buildIssuanceRequest(body: IssueCredentialRequest): IssuanceRequest {
    val cred = body.credential
    val opts = body.options

    val issuerStr = when (val iss = cred["issuer"]) {
        is JsonPrimitive -> iss.content
        is JsonObject -> iss["id"]?.jsonPrimitive?.content
            ?: error("issuer.id is required")
        else -> error("'issuer' field is required")
    }

    val subjectJson = cred["credentialSubject"]?.jsonObject
        ?: error("'credentialSubject' field is required")
    val subjectId = subjectJson["id"]?.jsonPrimitive?.content
        ?: error("'credentialSubject.id' field is required")
    val claims = subjectJson.filterKeys { it != "id" }

    val types = when (val t = cred["type"]) {
        is JsonArray -> t.map { CredentialType.fromString(it.jsonPrimitive.content) }
        is JsonPrimitive -> listOf(CredentialType.fromString(t.content))
        else -> listOf(CredentialType.fromString("VerifiableCredential"))
    }

    val format = when (opts?.format?.lowercase()) {
        "vc-jwt" -> ProofSuiteId.VC_JWT
        "sd-jwt-vc" -> ProofSuiteId.SD_JWT_VC
        else -> ProofSuiteId.VC_LD
    }

    val issuerKeyId = opts?.verificationMethod?.let { vm ->
        runCatching { VerificationMethodId.parse(vm) }.getOrNull()
    }

    val proofOptions = if (opts?.challenge != null || opts?.domain != null || opts?.verificationMethod != null) {
        ProofOptions(
            verificationMethod = opts.verificationMethod,
            additionalOptions = buildMap {
                opts.challenge?.let { put("challenge", it) }
                opts.domain?.let { put("domain", it) }
            },
        )
    } else null

    return IssuanceRequest(
        format = format,
        issuer = Issuer.fromDid(Did(issuerStr)),
        issuerKeyId = issuerKeyId,
        credentialSubject = CredentialSubject(
            id = Iri(subjectId),
            claims = claims,
        ),
        type = types,
        proofOptions = proofOptions,
    )
}

private fun parsePresentationCredentials(presentationJson: JsonObject): List<VerifiableCredential> {
    val vcs = presentationJson["verifiableCredential"] ?: return emptyList()
    return when (vcs) {
        is JsonArray -> vcs.mapNotNull { runCatching { deserializeVc(it.jsonObject) }.getOrNull() }
        is JsonObject -> listOf(deserializeVc(vcs))
        else -> emptyList()
    }
}

private fun serializeVc(vc: VerifiableCredential): JsonObject =
    vcJson.encodeToJsonElement(VerifiableCredential.serializer(), vc).jsonObject

private fun deserializeVc(json: JsonObject): VerifiableCredential =
    vcJson.decodeFromJsonElement(VerifiableCredential.serializer(), json)

private fun serializeVp(vp: VerifiablePresentation): JsonObject =
    vcJson.encodeToJsonElement(VerifiablePresentation.serializer(), vp).jsonObject

private fun deserializeVp(json: JsonObject): VerifiablePresentation =
    vcJson.decodeFromJsonElement(VerifiablePresentation.serializer(), json)

private fun VerificationResult.toVerifyResponse(): VerifyCredentialResponse = when (this) {
    is VerificationResult.Valid -> VerifyCredentialResponse(
        verified = true,
        checks = listOf("proof"),
        warnings = warnings,
    )
    is VerificationResult.Invalid -> VerifyCredentialResponse(
        verified = false,
        errors = allErrors,
        warnings = allWarnings,
    )
}
