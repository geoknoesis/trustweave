package com.trustweave.credential.template

import com.trustweave.credential.models.VerifiableCredential
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Credential template for reusable credential structures.
 * 
 * Templates define the structure, schema, and default values for credentials,
 * making it easier to issue multiple credentials of the same type.
 * 
 * @param id Unique template identifier
 * @param name Human-readable template name
 * @param schemaId Schema ID for validation
 * @param type List of credential types
 * @param defaultIssuer Default issuer DID (can be overridden)
 * @param defaultValidityDays Default validity period in days
 * @param requiredFields List of required fields in credentialSubject
 * @param optionalFields List of optional fields in credentialSubject
 */
@Serializable
data class CredentialTemplate(
    val id: String,
    val name: String,
    val schemaId: String,
    val type: List<String>,
    val defaultIssuer: String? = null,
    val defaultValidityDays: Int? = null,
    val requiredFields: List<String> = emptyList(),
    val optionalFields: List<String> = emptyList()
)

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
 *     schemaId = "https://example.com/schemas/person",
 *     type = listOf("VerifiableCredential", "PersonCredential"),
 *     defaultValidityDays = 365,
 *     requiredFields = listOf("name", "email")
 * )
 * 
 * val service = CredentialTemplateService()
 * service.createTemplate(template)
 * 
 * // Issue credential from template
 * val subject = buildJsonObject {
 *     put("name", "John Doe")
 *     put("email", "john@example.com")
 * }
 * 
 * val credential = service.issueFromTemplate("person-credential", subject)
 * ```
 */
class CredentialTemplateService {
    private val templates = ConcurrentHashMap<String, CredentialTemplate>()
    
    /**
     * Create or update a credential template.
     * 
     * @param template Template to create
     * @return Created template
     */
    suspend fun createTemplate(template: CredentialTemplate): CredentialTemplate {
        templates[template.id] = template
        return template
    }
    
    /**
     * Get a template by ID.
     * 
     * @param templateId Template ID
     * @return Template, or null if not found
     */
    fun getTemplate(templateId: String): CredentialTemplate? {
        return templates[templateId]
    }
    
    /**
     * Issue a credential from a template.
     * 
     * @param templateId Template ID
     * @param subject Credential subject data
     * @param options Additional options (issuer, validity, etc.)
     * @return Issued credential (without proof - must be issued separately)
     * @throws IllegalArgumentException if template not found
     */
    suspend fun issueFromTemplate(
        templateId: String,
        subject: JsonObject,
        options: Map<String, Any?> = emptyMap()
    ): VerifiableCredential {
        val template = templates[templateId]
            ?: throw IllegalArgumentException("Template not found: $templateId")
        
        // Validate required fields
        for (field in template.requiredFields) {
            if (!subject.containsKey(field)) {
                throw IllegalArgumentException("Required field '$field' is missing in subject")
            }
        }
        
        // Calculate expiration date if default validity is set
        val expirationDate = if (template.defaultValidityDays != null) {
            val now = java.time.Instant.now()
            val expiration = now.plusSeconds(template.defaultValidityDays!! * 24L * 60L * 60L)
            java.time.format.DateTimeFormatter.ISO_INSTANT.format(expiration)
        } else {
            null
        }
        
        // Get issuer from options or template default
        val issuer = options["issuer"] as? String 
            ?: template.defaultIssuer
            ?: throw IllegalArgumentException("Issuer must be provided in options or template")
        
        // Build credential
        return VerifiableCredential(
            id = options["id"] as? String,
            type = template.type,
            issuer = issuer,
            credentialSubject = subject,
            issuanceDate = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now()),
            expirationDate = expirationDate,
            credentialSchema = com.trustweave.credential.models.CredentialSchema(
                id = template.schemaId,
                type = "JsonSchemaValidator2018",
                schemaFormat = com.trustweave.core.SchemaFormat.JSON_SCHEMA
            )
        )
    }
    
    /**
     * List all templates.
     * 
     * @return List of all templates
     */
    fun listTemplates(): List<CredentialTemplate> {
        return templates.values.toList()
    }
    
    /**
     * Delete a template.
     * 
     * @param templateId Template ID
     * @return true if template was deleted, false if not found
     */
    fun deleteTemplate(templateId: String): Boolean {
        return templates.remove(templateId) != null
    }
    
    /**
     * Clear all templates.
     * Useful for testing.
     */
    fun clear() {
        templates.clear()
    }
}

