package org.trustweave.registry.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.registry.*

fun Routing.configureTrustRegistryRoutes(registry: TrustRegistry) {

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
                    val did = call.parameters["did"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val update = call.receive<IssuerUpdate>()
                    runCatching { registry.updateIssuer(did, update) }
                        .onSuccess { call.respond(it) }
                        .onFailure { call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") }) }
                }
                post("/revoke") {
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
                    val did = call.parameters["did"] ?: return@put call.respond(HttpStatusCode.BadRequest)
                    val update = call.receive<VerifierUpdate>()
                    runCatching { registry.updateVerifier(did, update) }
                        .onSuccess { call.respond(it) }
                        .onFailure { call.respond(HttpStatusCode.NotFound, buildJsonObject { put("error", "not_found") }) }
                }
                post("/revoke") {
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
