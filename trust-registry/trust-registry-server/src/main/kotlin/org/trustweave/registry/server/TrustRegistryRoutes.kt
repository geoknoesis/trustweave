package org.trustweave.registry.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.registry.*
import java.security.MessageDigest

/**
 * Configures the trust registry HTTP routes.
 *
 * Read-only routes (lookups/queries) are always open. Mutating routes
 * (register/update/revoke) require `Authorization: Bearer <apiToken>`.
 * If [apiToken] is null, mutating routes are disabled entirely (503) —
 * the server fails closed rather than allowing unauthenticated writes.
 */
fun Routing.configureTrustRegistryRoutes(registry: TrustRegistry, apiToken: String? = null) {

    /**
     * Guards a mutating handler. Returns true if the call may proceed;
     * otherwise responds (503 when no token is configured, 401 on a
     * missing/invalid token) and returns false.
     */
    suspend fun ApplicationCall.authorizeMutation(): Boolean {
        if (apiToken.isNullOrBlank()) {
            respond(
                HttpStatusCode.ServiceUnavailable,
                buildJsonObject {
                    put("error", "mutations_disabled")
                    put("message", "Registry mutation endpoints are disabled: no API token is configured on this server")
                },
            )
            return false
        }
        val header = request.headers[HttpHeaders.Authorization]
        val provided = header?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
        if (provided.isNullOrEmpty() ||
            !MessageDigest.isEqual(provided.toByteArray(Charsets.UTF_8), apiToken.toByteArray(Charsets.UTF_8))
        ) {
            respond(HttpStatusCode.Unauthorized, buildJsonObject { put("error", "unauthorized") })
            return false
        }
        return true
    }

    route("/registry") {

        // Issuers
        route("/issuers") {
            get {
                val status = call.request.queryParameters["status"]
                    ?.let { runCatching { AccreditationStatus.valueOf(it) }.getOrNull() }
                val credentialType = call.request.queryParameters["credentialType"]
                val nameContains = call.request.queryParameters["nameContains"]
                call.respond(registry.listIssuers(RegistryFilter(status, credentialType, nameContains)))
            }
            post {
                if (!call.authorizeMutation()) return@post
                val reg = call.receive<IssuerRegistration>()
                call.respond(HttpStatusCode.Created, registry.registerIssuer(reg))
            }
            route("/{did}") {
                get {
                    val did = call.parameters["did"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    registry.getIssuer(did)
                        ?.let { call.respond(it) }
                        ?: call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                }
                put {
                    if (!call.authorizeMutation()) return@put
                    val did = call.parameters["did"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val update = call.receive<IssuerUpdate>()
                    runCatching { registry.updateIssuer(did, update) }
                        .onSuccess { call.respond(it) }
                        .onFailure { call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") }) }
                }
                post("/revoke") {
                    if (!call.authorizeMutation()) return@post
                    val did = call.parameters["did"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val revoked = registry.revokeIssuer(did)
                    if (revoked) call.respond(HttpStatusCode.OK, buildJsonObject { put("status", "revoked") })
                    else call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                }
            }
        }

        // Verifiers
        route("/verifiers") {
            get {
                val status = call.request.queryParameters["status"]
                    ?.let { runCatching { AccreditationStatus.valueOf(it) }.getOrNull() }
                val nameContains = call.request.queryParameters["nameContains"]
                call.respond(registry.listVerifiers(RegistryFilter(status = status, nameContains = nameContains)))
            }
            post {
                if (!call.authorizeMutation()) return@post
                val reg = call.receive<VerifierRegistration>()
                call.respond(HttpStatusCode.Created, registry.registerVerifier(reg))
            }
            route("/{did}") {
                get {
                    val did = call.parameters["did"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    registry.getVerifier(did)
                        ?.let { call.respond(it) }
                        ?: call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                }
                put {
                    if (!call.authorizeMutation()) return@put
                    val did = call.parameters["did"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val update = call.receive<VerifierUpdate>()
                    runCatching { registry.updateVerifier(did, update) }
                        .onSuccess { call.respond(it) }
                        .onFailure { call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") }) }
                }
                post("/revoke") {
                    if (!call.authorizeMutation()) return@post
                    val did = call.parameters["did"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val revoked = registry.revokeVerifier(did)
                    if (revoked) call.respond(HttpStatusCode.OK, buildJsonObject { put("status", "revoked") })
                    else call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                }
            }
        }

        // Accreditation status lookup
        get("/status/{did}") {
            val did = call.parameters["did"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val status = registry.getAccreditationStatus(did)
            call.respond(buildJsonObject { put("did", did); put("status", status.name) })
        }
    }
}
