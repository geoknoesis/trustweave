package org.trustweave.registry.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.registry.AccreditationStatus
import org.trustweave.registry.IssuerRegistration
import org.trustweave.registry.IssuerUpdate
import org.trustweave.registry.ParticipantAlreadyRegisteredException
import org.trustweave.registry.RegistryFilter
import org.trustweave.registry.TrustRegistry
import org.trustweave.registry.VerifierRegistration
import org.trustweave.registry.VerifierUpdate
import java.security.MessageDigest

/**
 * Configures the trust registry HTTP routes.
 *
 * Read-only routes (lookups/queries) are always open. Mutating routes
 * (register/update/revoke) require `Authorization: Bearer <apiToken>`.
 * If [apiToken] is null, mutating routes are disabled entirely (503) —
 * the server fails closed rather than allowing unauthenticated writes.
 *
 * Registering an already-registered DID answers 409; revoke accepts an optional `reason` query
 * parameter that is stored on the record.
 *
 * @param hostAuthenticated true when a `HostAuthentication` gate has already admitted the call.
 *   The two mechanisms compose rather than stack: a call the gate admitted is authorized, and
 *   demanding [apiToken] as well would mean a host using mTLS or a gateway could never satisfy
 *   the route. It never opens anything on its own — the gate refused everything it did not admit
 *   before routing ran.
 */
fun Routing.configureTrustRegistryRoutes(
    registry: TrustRegistry,
    apiToken: String? = null,
    hostAuthenticated: Boolean = false,
) {
    /**
     * Guards a mutating handler. Returns true if the call may proceed;
     * otherwise responds (503 when no token is configured, 401 on a
     * missing/invalid token) and returns false.
     */
    suspend fun ApplicationCall.authorizeMutation(): Boolean {
        if (hostAuthenticated) return true
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

    /**
     * Runs a mutating registry call and maps its failure: cancellation is rethrown, an unknown
     * DID is 404, a duplicate registration is 409, anything else is a logged 500 — never a 404
     * that would hide a real fault.
     */
    suspend fun ApplicationCall.respondRegistryCall(
        success: HttpStatusCode = HttpStatusCode.OK,
        block: suspend () -> Any,
    ) {
        val result =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoSuchElementException) {
                respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                return
            } catch (e: ParticipantAlreadyRegisteredException) {
                respond(
                    HttpStatusCode.Conflict,
                    buildJsonObject {
                        put("error", "already_registered")
                        put("did", e.did)
                    },
                )
                return
            } catch (e: Exception) {
                application.log.error("Trust registry operation failed", e)
                respond(HttpStatusCode.InternalServerError, buildJsonObject { put("error", "internal_error") })
                return
            }
        respond(success, result)
    }

    route("/registry") {
        // Issuers
        route("/issuers") {
            get {
                val status =
                    call.request.queryParameters["status"]
                        ?.let { runCatching { AccreditationStatus.valueOf(it) }.getOrNull() }
                val credentialType = call.request.queryParameters["credentialType"]
                val nameContains = call.request.queryParameters["nameContains"]
                call.respond(registry.listIssuers(RegistryFilter(status, credentialType, nameContains)))
            }
            post {
                if (!call.authorizeMutation()) return@post
                val reg = call.receive<IssuerRegistration>()
                call.respondRegistryCall(HttpStatusCode.Created) { registry.registerIssuer(reg) }
            }
            route("/{did}") {
                get {
                    val did = call.parameters["did"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    registry
                        .getIssuer(did)
                        ?.let { call.respond(it) }
                        ?: call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                }
                put {
                    if (!call.authorizeMutation()) return@put
                    val did = call.parameters["did"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val update = call.receive<IssuerUpdate>()
                    call.respondRegistryCall { registry.updateIssuer(did, update) }
                }
                post("/revoke") {
                    if (!call.authorizeMutation()) return@post
                    val did = call.parameters["did"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val revoked = registry.revokeIssuer(did, call.request.queryParameters["reason"])
                    if (revoked) {
                        call.respond(HttpStatusCode.OK, buildJsonObject { put("status", "revoked") })
                    } else {
                        call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                    }
                }
            }
        }

        // Verifiers
        route("/verifiers") {
            get {
                val status =
                    call.request.queryParameters["status"]
                        ?.let { runCatching { AccreditationStatus.valueOf(it) }.getOrNull() }
                val nameContains = call.request.queryParameters["nameContains"]
                call.respond(registry.listVerifiers(RegistryFilter(status = status, nameContains = nameContains)))
            }
            post {
                if (!call.authorizeMutation()) return@post
                val reg = call.receive<VerifierRegistration>()
                call.respondRegistryCall(HttpStatusCode.Created) { registry.registerVerifier(reg) }
            }
            route("/{did}") {
                get {
                    val did = call.parameters["did"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                    registry
                        .getVerifier(did)
                        ?.let { call.respond(it) }
                        ?: call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                }
                put {
                    if (!call.authorizeMutation()) return@put
                    val did = call.parameters["did"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val update = call.receive<VerifierUpdate>()
                    call.respondRegistryCall { registry.updateVerifier(did, update) }
                }
                post("/revoke") {
                    if (!call.authorizeMutation()) return@post
                    val did = call.parameters["did"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val revoked = registry.revokeVerifier(did, call.request.queryParameters["reason"])
                    if (revoked) {
                        call.respond(HttpStatusCode.OK, buildJsonObject { put("status", "revoked") })
                    } else {
                        call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") })
                    }
                }
            }
        }

        // Accreditation status lookup
        get("/status/{did}") {
            val did = call.parameters["did"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val status = registry.getAccreditationStatus(did)
            call.respond(
                buildJsonObject {
                    put("did", did)
                    put("status", status.name)
                },
            )
        }
    }
}
