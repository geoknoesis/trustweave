package org.trustweave.credential.oidc4vci.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.trustweave.core.identifiers.Iri
import org.trustweave.core.serialization.SerializationModule
import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId

/**
 * An [Oidc4VciCredentialBuilder] that signs through a TrustWeave [CredentialService] (and so through
 * its KMS), the same path the VC API server uses.
 *
 * Supports `ldp_vc` (the credential is the JSON document carrying its Data Integrity proof) by
 * default, because that is the proof suite `credential-api` ships. Map further OID4VCI formats to
 * the proof suites the [service] has an engine for with [formats], e.g. `"jwt_vc_json" to
 * ProofSuiteId.VC_JWT` (the credential is then the compact JWT) once a VC-JWT engine is registered.
 * A format the service cannot sign fails at issuance, so list only what it supports.
 *
 * @param issuerKeyId verification method to sign with (the credential service requires one).
 */
class CredentialServiceCredentialBuilder
    @JvmOverloads
    constructor(
        private val service: CredentialService,
        private val issuerKeyId: VerificationMethodId,
        private val formats: Map<String, ProofSuiteId> =
            mapOf("ldp_vc" to ProofSuiteId.VC_LD),
    ) : Oidc4VciCredentialBuilder {
        override val supportedFormats: Set<String> = formats.keys

        override suspend fun build(request: Oidc4VciCredentialRequest): String {
            val suite = formats[request.format] ?: throw UnsupportedCredentialFormatException("Unsupported format '${request.format}'")
            val types = (listOf("VerifiableCredential") + request.credentialTypes).distinct().map { CredentialType.fromString(it) }
            val issuance =
                IssuanceRequest(
                    format = suite,
                    issuer = Issuer.fromDid(Did(request.issuerDid)),
                    issuerKeyId = issuerKeyId,
                    credentialSubject = CredentialSubject(id = Iri(request.subjectDid), claims = request.claims),
                    type = types,
                )
            val credential =
                when (val result = service.issue(issuance)) {
                    is IssuanceResult.Success -> result.credential
                    is IssuanceResult.Failure -> throw IllegalStateException(
                        "Credential signing failed: ${result.errors.joinToString("; ")}",
                    )
                }
            return encode(credential, suite)
        }

        private fun encode(
            credential: VerifiableCredential,
            suite: ProofSuiteId,
        ): String =
            when (suite) {
                ProofSuiteId.VC_JWT ->
                    (credential.proof as? CredentialProof.JwtProof)?.jwt
                        ?: throw IllegalStateException("The credential service returned no JWT proof for jwt_vc_json")
                else ->
                    json
                        .encodeToJsonElement(VerifiableCredential.serializer(), credential)
                        .jsonObject
                        .toString()
            }

        private companion object {
            val json =
                Json {
                    serializersModule = SerializationModule.default
                    ignoreUnknownKeys = true
                }
        }
    }
