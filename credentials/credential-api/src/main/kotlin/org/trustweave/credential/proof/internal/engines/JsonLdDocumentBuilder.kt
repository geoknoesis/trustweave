package org.trustweave.credential.proof.internal.engines

import org.trustweave.credential.internal.CredentialConstants
import org.trustweave.credential.model.Evidence
import org.trustweave.credential.model.vc.CredentialStatus
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import kotlinx.serialization.json.*

/**
 * Builds JSON-LD document representations of verifiable credentials.
 *
 * Responsible for serialising [VerifiableCredential] and [IssuanceRequest] objects
 * into the `JsonObject` form consumed by the canonicalization pipeline.
 * Does **not** include the proof node — callers strip it before signing.
 */
internal object JsonLdDocumentBuilder {

    /**
     * Proof-options key under [org.trustweave.credential.proof.ProofOptions.additionalOptions]
     * carrying additional `@context` URLs (a `List<String>`) the credential declares.
     * Public alias: [org.trustweave.credential.jsonld.JsonLdContexts.CONTEXTS_PROOF_OPTION].
     */
    const val CONTEXTS_OPTION = org.trustweave.credential.jsonld.JsonLdContexts.CONTEXTS_PROOF_OPTION

    /**
     * Resolve the `@context` list for a credential being issued.
     *
     * Preserves the contexts declared by the request (via the [CONTEXTS_OPTION] proof
     * option) in order, prepends the W3C VC base context when the request does not declare
     * one itself, and appends [proofSuiteContext] only if it is absent. Declared contexts
     * are never discarded — dropping them would silently remove the term definitions of
     * `credentialSubject` claims from the signed canonical form.
     *
     * @param request The issuance request
     * @param proofSuiteContext The security-suite context required by the proof type
     * @return The ordered `@context` list for both the signed document and the credential
     */
    fun resolveContexts(request: IssuanceRequest, proofSuiteContext: String): List<String> {
        val declared = (request.proofOptions?.additionalOptions?.get(CONTEXTS_OPTION) as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()
        return buildList {
            if (declared.none {
                    it == CredentialConstants.VcContexts.VC_1_1 || it == CredentialConstants.VcContexts.VC_2_0
                }
            ) {
                add(CredentialConstants.VcContexts.VC_1_1)
            }
            declared.forEach { context -> if (context !in this) add(context) }
            if (proofSuiteContext !in this) add(proofSuiteContext)
        }
    }

    /**
     * Build a JSON-LD document from an [IssuanceRequest] for signing.
     *
     * @param request The issuance request
     * @param credentialIdValue The credential ID value to use in the document
     * @param contexts The full, ordered `@context` list (see [resolveContexts])
     * @return The JSON-LD document as a [JsonObject]
     */
    fun build(request: IssuanceRequest, credentialIdValue: String, contexts: List<String>): JsonObject {
        val issuerIri = when (val issuer = request.issuer) {
            is Issuer.IriIssuer -> issuer.id.value
            is Issuer.ObjectIssuer -> issuer.id.value
        }
        return buildJsonObject {
            put("@context", buildJsonArray {
                contexts.forEach { context -> add(context) }
            })
            put("id", credentialIdValue)
            put("type", buildJsonArray {
                request.type.forEach { type -> add(type.value) }
            })
            put("issuer", issuerIri)
            put("issuanceDate", request.issuedAt.toString())
            request.validFrom?.let { put("validFrom", it.toString()) }
            request.validUntil?.let { put("expirationDate", it.toString()) }
            put("credentialSubject", buildJsonObject {
                request.credentialSubject.id?.let { put("id", it.value) }
                request.credentialSubject.claims.entries.forEach { (key, value) ->
                    put(key, value)
                }
            })
            request.credentialStatus?.let { status -> putCredentialStatus(this, status) }
            request.credentialSchema?.let { schema ->
                put("credentialSchema", buildJsonObject {
                    put("id", schema.id.value)
                    put("type", schema.type)
                })
            }
            request.evidence?.let { evidenceList -> putEvidence(this, evidenceList) }
        }
    }

    /**
     * Build a JSON-LD document from an existing [VerifiableCredential], omitting the proof.
     *
     * Used during verification to reconstruct the canonical document for signature checking.
     *
     * @param credential The verifiable credential
     * @return The JSON-LD document as a [JsonObject] (without proof)
     */
    fun buildWithoutProof(credential: VerifiableCredential): JsonObject {
        val issuerIri = when (val issuer = credential.issuer) {
            is Issuer.IriIssuer -> issuer.id.value
            is Issuer.ObjectIssuer -> issuer.id.value
        }
        return buildJsonObject {
            put("@context", buildJsonArray {
                credential.context.forEach { ctx -> add(ctx) }
            })
            credential.id?.let { put("id", it.value) }
            put("type", buildJsonArray {
                credential.type.forEach { type -> add(type.value) }
            })
            put("issuer", issuerIri)
            credential.issuanceDate?.let { put("issuanceDate", it.toString()) }
            credential.validFrom?.let { put("validFrom", it.toString()) }
            credential.expirationDate?.let { put("expirationDate", it.toString()) }
            put("credentialSubject", buildJsonObject {
                credential.credentialSubject.id?.let { put("id", it.value) }
                credential.credentialSubject.claims.entries.forEach { (key, value) ->
                    put(key, value)
                }
            })
            credential.credentialStatus?.let { status -> putCredentialStatus(this, status) }
            credential.credentialSchema?.let { schema ->
                put("credentialSchema", buildJsonObject {
                    put("id", schema.id.value)
                    put("type", schema.type)
                })
            }
            credential.evidence?.let { evidenceList -> putEvidence(this, evidenceList) }
        }
    }

    private fun putCredentialStatus(builder: JsonObjectBuilder, status: CredentialStatus) {
        builder.put("credentialStatus", buildJsonObject {
            put("id", status.id.value)
            put("type", status.type)
            put("statusPurpose", status.statusPurpose.name.lowercase())
            status.statusListIndex?.let { put("statusListIndex", it) }
            status.statusListCredential?.let { put("statusListCredential", it.value) }
            status.formatData?.let { formatData ->
                put("formatData", buildJsonObject {
                    formatData.forEach { (key, value) -> put(key, value) }
                })
            }
        })
    }

    private fun putEvidence(builder: JsonObjectBuilder, evidenceList: List<Evidence>) {
        builder.put("evidence", buildJsonArray {
            evidenceList.forEach { evidence ->
                add(buildJsonObject {
                    evidence.id?.let { put("id", it.value) }
                    put("type", buildJsonArray {
                        evidence.type.forEach { type -> add(type) }
                    })
                    evidence.evidenceDocument?.let { put("evidenceDocument", it) }
                    evidence.verifier?.let { put("verifier", it.value) }
                    evidence.evidenceDate?.let { put("evidenceDate", JsonPrimitive(it)) }
                })
            }
        })
    }
}
