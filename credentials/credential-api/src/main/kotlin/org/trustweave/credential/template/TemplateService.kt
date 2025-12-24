package org.trustweave.credential.template

import org.trustweave.credential.identifiers.SchemaId
import org.trustweave.credential.model.Claims
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.CredentialSubject
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing credential templates.
 *
 * Provides template creation, retrieval, and credential issuance from templates.
 *
 * **Example Usage**:
 * ```kotlin
 * val template = CredentialTemplate(
 *     id = "person-credential",
 *     name = "Person Credential",
 *     schemaId = SchemaId("https://example.com/schemas/person"),
 *     type = listOf(CredentialType.VerifiableCredential, CredentialType.Person),
 *     defaultValidity = Duration.ofDays(365),
 *     requiredFields = listOf("name", "email")
 * )
 *
 * val service = TemplateServices.default()
 * service.createTemplate(template)
 *
 * // Issue credential from template
 * val claims = mapOf(
 *     "name" to JsonPrimitive("John Doe"),
 *     "email" to JsonPrimitive("john@example.com")
 * )
 *
 * val request = service.createIssuanceRequest(
 *     templateId = "person-credential",
 *     format = ProofSuiteId.VC_LD,
 *     issuer = Issuer.fromDid(issuerDid),
 *     credentialSubject = CredentialSubject.fromDid(subjectDid, claims = claims)
 * )
 * ```
 */
interface TemplateService {
    /**
     * Create or update a credential template.
     *
     * @param template Template to create
     */
    suspend fun createTemplate(template: CredentialTemplate)

    /**
     * Get a template by ID.
     *
     * @param templateId Template ID
     * @return Template, or null if not found
     */
    suspend fun getTemplate(templateId: String): CredentialTemplate?

    /**
     * Create an IssuanceRequest from a template.
     *
     * @param templateId Template ID
     * @param format Proof suite ID
     * @param issuer VC Issuer (IRI-based)
     * @param credentialSubject VC CredentialSubject (IRI + claims)
     * @param issuedAt Optional issuance time (defaults to now)
     * @param validUntil Optional expiration time (uses template default if not provided)
     * @return Issuance request
     * @throws IllegalArgumentException if template not found or required fields missing
     */
    suspend fun createIssuanceRequest(
        templateId: String,
        format: ProofSuiteId,
        issuer: Issuer,
        credentialSubject: CredentialSubject,
        issuedAt: Instant = Clock.System.now(),
        validUntil: Instant? = null
    ): IssuanceRequest

    /**
     * List all templates.
     *
     * @return List of all templates
     */
    suspend fun listTemplates(): List<CredentialTemplate>

    /**
     * Delete a template.
     *
     * @param templateId Template ID
     * @return true if template was deleted, false if not found
     */
    suspend fun deleteTemplate(templateId: String): Boolean

    /**
     * Clear all templates.
     * Useful for testing.
     */
    suspend fun clear()
}

