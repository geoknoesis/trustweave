package org.trustweave.credential.model.vc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * VC Terms of Use - terms of use for the credential.
 * 
 * Per W3C VC Data Model, used to specify terms of use for the credential.
 * 
 * **Examples:**
 * ```kotlin
 * val termsOfUse = TermsOfUse(
 *     id = Iri("https://example.com/policies/credential/4"),
 *     type = "IssuerPolicy"
 * )
 * ```
 */
@Serializable
data class TermsOfUse(
    val id: String? = null,  // IRI reference to terms
    val type: String? = null,  // e.g., "IssuerPolicy"
    val additionalProperties: Map<String, JsonElement> = emptyMap()
)

