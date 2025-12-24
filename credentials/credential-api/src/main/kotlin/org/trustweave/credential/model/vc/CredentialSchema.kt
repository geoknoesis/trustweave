package org.trustweave.credential.model.vc

import org.trustweave.credential.identifiers.SchemaId
import kotlinx.serialization.Serializable

/**
 * VC Credential Schema - schema reference for validation.
 * 
 * Per W3C VC Data Model, used to reference a schema that can be used to validate
 * the structure and contents of the credential.
 * 
 * **Examples:**
 * ```kotlin
 * val schema = CredentialSchema(
 *     id = SchemaId("https://example.org/examples/degree.json"),
 *     type = "JsonSchemaValidator2018"
 * )
 * ```
 */
@Serializable
data class CredentialSchema(
    val id: SchemaId,
    val type: String  // e.g., "JsonSchemaValidator2018", "ShaclValidator2020"
)

