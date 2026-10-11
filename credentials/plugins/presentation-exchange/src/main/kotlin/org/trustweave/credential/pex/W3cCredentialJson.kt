package org.trustweave.credential.pex

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential

/**
 * Plain W3C VC JSON for embedding credentials in a `vp_token` presentation, shared by the OID4VP
 * and SIOP plugins so the credentials a `presentation_submission` points at are the same shape the
 * [PresentationDefinitionMatcher] matched on.
 */
object W3cCredentialJson {
    /**
     * Serializes a [VerifiableCredential] into plain W3C VC JSON for embedding in a vp_token.
     *
     * This intentionally mirrors `PresentationDefinitionMatcher.vcToDocument` so that the
     * credentials referenced by `presentation_submission` descriptor paths actually satisfy
     * the JSONPath field paths they were matched on:
     * - `issuer` is a plain IRI string (or a `{"id": ..., "name": ...}` object), never a
     *   polymorphic object with a class-discriminator field;
     * - `credentialSubject` carries the flattened claims next to `id` — no internal
     *   `"claims"` wrapper;
     * - `proof` is a plain W3C proof object (see [w3cProofOf]) or omitted for envelope
     *   proof formats.
     */
    fun toJson(credential: VerifiableCredential): JsonObject =
        buildJsonObject {
            put("@context", JsonArray(credential.context.map { JsonPrimitive(it) }))
            credential.id?.let { put("id", it.value) }
            put("type", JsonArray(credential.type.map { JsonPrimitive(it.value) }))
            when (val issuer = credential.issuer) {
                is Issuer.IriIssuer -> put("issuer", issuer.id.value)
                is Issuer.ObjectIssuer ->
                    put(
                        "issuer",
                        buildJsonObject {
                            put("id", issuer.id.value)
                            issuer.name?.let { put("name", it) }
                            issuer.additionalProperties.forEach { (key, value) -> put(key, value) }
                        },
                    )
            }
            credential.issuanceDate?.let { put("issuanceDate", it.toString()) }
            credential.validFrom?.let { put("validFrom", it.toString()) }
            credential.expirationDate?.let { put("expirationDate", it.toString()) }
            credential.validUntil?.let { put("validUntil", it.toString()) }
            credential.name?.let { put("name", it) }
            credential.description?.let { put("description", it) }
            credential.credentialStatus?.let { status ->
                put(
                    "credentialStatus",
                    buildJsonObject {
                        put("id", status.id.value)
                        put("type", status.type)
                    },
                )
            }
            credential.credentialSchema?.let { schema ->
                put(
                    "credentialSchema",
                    buildJsonObject {
                        put("id", schema.id.value)
                        put("type", schema.type)
                    },
                )
            }
            put(
                "credentialSubject",
                buildJsonObject {
                    credential.credentialSubject.id?.let { put("id", it.value) }
                    credential.credentialSubject.claims.forEach { (key, value) -> put(key, value) }
                },
            )
            w3cProofOf(credential.proof)?.let { put("proof", it) }
        }

    /**
     * Maps an embedded data-integrity proof to a plain W3C proof object
     * (`type`/`created`/`verificationMethod`/`proofPurpose`/`proofValue` plus any
     * additional properties such as `jws` for JsonWebSignature2020).
     *
     * Envelope proof formats (VC-JWT, SD-JWT VC, mdoc, JAdES) return `null`: there the
     * proof *is* the credential envelope and has no in-document proof object representation.
     */
    private fun w3cProofOf(proof: CredentialProof?): JsonObject? =
        when (proof) {
            is CredentialProof.LinkedDataProof ->
                buildJsonObject {
                    put("type", proof.type)
                    put("created", proof.created.toString())
                    put("verificationMethod", proof.verificationMethod)
                    put("proofPurpose", proof.proofPurpose)
                    if (proof.proofValue.isNotBlank()) put("proofValue", proof.proofValue)
                    proof.additionalProperties.forEach { (key, value) -> put(key, value) }
                }
            else -> null
        }
}
