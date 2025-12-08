package com.trustweave.credential.requests

import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.subject
import com.trustweave.credential.model.vc.SubjectBuilder
import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import java.time.Duration as JavaDuration
import kotlin.time.Duration
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

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
    block: IssuanceRequestBuilder.() -> Unit = {}
): IssuanceRequest {
    val builder = IssuanceRequestBuilder(format)
    builder.block()
    return builder.build()
}

/**
 * Builder for IssuanceRequest.
 */
class IssuanceRequestBuilder(
    private val format: ProofSuiteId
) {
    private var issuer: Issuer? = null
    private var issuerKeyId: VerificationMethodId? = null
    private var credentialSubject: CredentialSubject? = null
    private var types: MutableList<CredentialType> = mutableListOf()
    private var id: com.trustweave.credential.identifiers.CredentialId? = null
    private var issuedAt: Instant = Clock.System.now()
    private var validFrom: Instant? = null
    private var validUntil: Instant? = null
    
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
        block: com.trustweave.credential.model.vc.SubjectBuilder.() -> Unit = {}
    ) {
        credentialSubject = com.trustweave.credential.model.vc.subject(did, block)
    }
    
    /**
     * Build subject with IRI.
     */
    fun subject(
        iri: String,
        block: com.trustweave.credential.model.vc.SubjectBuilder.() -> Unit = {}
    ) {
        credentialSubject = com.trustweave.credential.model.vc.subject(iri, block)
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
        this.id = com.trustweave.credential.identifiers.CredentialId(id)
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
     * Set expiration duration from now.
     */
    fun expiresIn(duration: JavaDuration) {
        val kotlinDuration = Duration.parse(duration.toString())
        this.validUntil = issuedAt.plus(kotlinDuration)
    }
    
    /**
     * Build the IssuanceRequest.
     */
    fun build(): IssuanceRequest {
        val finalIssuer = issuer ?: throw IllegalArgumentException("Issuer is required")
        val finalSubject = credentialSubject ?: throw IllegalArgumentException("Subject is required")
        
        // Ensure VerifiableCredential type is included
        val finalTypes = if (types.isEmpty()) {
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
            validUntil = validUntil
        )
    }
}

