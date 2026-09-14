package org.trustweave.credential.requests

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.SubjectBuilder
import org.trustweave.credential.model.vc.subject
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import kotlin.time.Duration
import java.time.Duration as JavaDuration

/**
 * Sleek builder DSL for creating IssuanceRequest.
 *
 * **Examples:**
 * ```kotlin
 * // Simple request
 * val request = issuanceRequest(ProofSuiteId.VC_LD) {
 *     issuer(issuerDid)
 *     subject(subjectDid) {
 *         "name" to "John Doe"
 *         "email" to "john@example.com"
 *     }
 *     type("PersonCredential")
 * }
 *
 * // With expiration
 * val request = issuanceRequest(ProofSuiteId.VC_LD) {
 *     issuer(issuerDid)
 *     subject(subjectDid) {
 *         "name" to "John Doe"
 *         "age" to 30
 *     }
 *     type("PersonCredential")
 *     expiresIn(365.days)
 * }
 * ```
 */
fun issuanceRequest(
    format: ProofSuiteId,
    block: IssuanceRequestBuilder.() -> Unit = {},
): IssuanceRequest {
    val builder = IssuanceRequestBuilder(format)
    builder.block()
    return builder.build()
}

/**
 * Builder for IssuanceRequest.
 */
class IssuanceRequestBuilder(
    private val format: ProofSuiteId,
) {
    private var issuer: Issuer? = null
    private var issuerKeyId: VerificationMethodId? = null
    private var credentialSubject: CredentialSubject? = null
    private var types: MutableList<CredentialType> = mutableListOf()
    private var id: org.trustweave.credential.identifiers.CredentialId? = null
    private var issuedAt: Instant = Clock.System.now()
    private var validFrom: Instant? = null
    private var validUntil: Instant? = null

    /**
     * An expiry expressed as a duration, resolved against [issuedAt] at [build] time.
     *
     * Held rather than applied immediately so that the order of calls inside the builder block
     * does not change the result: `expiresIn(30.days)` before `issuedAt(t)` used to measure from
     * "now" and after it from `t`, which is not a distinction a caller writing a DSL block should
     * have to think about.
     */
    private var expiresAfter: Duration? = null

    /**
     * Set issuer from DID.
     */
    fun issuer(did: Did) {
        issuer = Issuer.fromDid(did)
    }

    /**
     * Set issuer from IRI string.
     */
    fun issuer(iri: String) {
        issuer = Issuer.from(iri)
    }

    /**
     * Set issuer from Issuer object.
     */
    fun issuer(issuer: Issuer) {
        this.issuer = issuer
    }

    /**
     * Set issuer key ID for signing.
     */
    fun issuerKeyId(keyId: VerificationMethodId) {
        this.issuerKeyId = keyId
    }

    /**
     * Set issuer key ID from string.
     */
    fun issuerKeyId(keyId: String) {
        this.issuerKeyId = VerificationMethodId.parse(keyId)
    }

    /**
     * Build subject with properties.
     */
    fun subject(
        did: Did,
        block: org.trustweave.credential.model.vc.SubjectBuilder.() -> Unit = {},
    ) {
        credentialSubject =
            org.trustweave.credential.model.vc
                .subject(did, block)
    }

    /**
     * Build subject with IRI.
     */
    fun subject(
        iri: String,
        block: org.trustweave.credential.model.vc.SubjectBuilder.() -> Unit = {},
    ) {
        credentialSubject =
            org.trustweave.credential.model.vc
                .subject(iri, block)
    }

    /**
     * Set subject directly.
     */
    fun subject(subject: CredentialSubject) {
        this.credentialSubject = subject
    }

    /**
     * Add credential type.
     */
    fun type(type: String) {
        val credentialType = CredentialType.fromString(type)
        if (!types.contains(credentialType)) {
            types.add(credentialType)
        }
    }

    /**
     * Add multiple credential types.
     */
    fun types(vararg types: String) {
        types.forEach { type(it) }
    }

    /**
     * Set credential ID.
     */
    fun id(id: String) {
        this.id =
            org.trustweave.credential.identifiers
                .CredentialId(id)
    }

    /**
     * Set issued at time.
     */
    fun issuedAt(instant: Instant) {
        this.issuedAt = instant
    }

    /**
     * Set valid from time.
     */
    fun validFrom(instant: Instant) {
        this.validFrom = instant
    }

    /**
     * Set valid until time.
     */
    fun validUntil(instant: Instant) {
        this.validUntil = instant
    }

    /**
     * Set the expiry as a duration after [issuedAt], whenever that ends up being set.
     *
     * Resolved at [build] time, so calling this before or after `issuedAt` gives the same answer.
     * An explicit [validUntil] wins: a caller who states the instant means the instant.
     */
    fun expiresIn(duration: JavaDuration) {
        this.expiresAfter = Duration.parse(duration.toString())
    }

    /**
     * Build the IssuanceRequest.
     */
    fun build(): IssuanceRequest {
        val finalIssuer = issuer ?: throw IllegalArgumentException("Issuer is required")
        val finalSubject = credentialSubject ?: throw IllegalArgumentException("Subject is required")
        val finalValidUntil = validUntil ?: expiresAfter?.let { issuedAt.plus(it) }

        // Ensure VerifiableCredential type is included
        val finalTypes =
            if (types.isEmpty()) {
                listOf(CredentialType.VerifiableCredential)
            } else if (!types.any { it.value == "VerifiableCredential" }) {
                listOf(CredentialType.VerifiableCredential) + types
            } else {
                types.toList()
            }

        return IssuanceRequest(
            format = format,
            issuer = finalIssuer,
            issuerKeyId = issuerKeyId,
            credentialSubject = finalSubject,
            type = finalTypes,
            id = id,
            issuedAt = issuedAt,
            validFrom = validFrom,
            validUntil = finalValidUntil,
        )
    }
}
