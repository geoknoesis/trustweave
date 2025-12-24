package org.trustweave.credential.template

import org.trustweave.credential.identifiers.SchemaId
import org.trustweave.credential.model.CredentialType
import java.time.Duration

/**
 * Credential template for reusable credential structures.
 *
 * Templates define the structure, schema, and default values for credentials,
 * making it easier to issue multiple credentials of the same type.
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
 * ```
 */
data class CredentialTemplate(
    /**
     * Unique template identifier.
     */
    val id: String,
    
    /**
     * Human-readable template name.
     */
    val name: String,
    
    /**
     * Schema ID for validation.
     */
    val schemaId: SchemaId,
    
    /**
     * List of credential types.
     */
    val type: List<CredentialType>,
    
    /**
     * Default issuer DID (can be overridden when issuing).
     */
    val defaultIssuer: String? = null,
    
    /**
     * Default validity period (can be overridden when issuing).
     */
    val defaultValidity: Duration? = null,
    
    /**
     * List of required fields in claims.
     */
    val requiredFields: List<String> = emptyList(),
    
    /**
     * List of optional fields in claims.
     */
    val optionalFields: List<String> = emptyList()
)

