package org.trustweave.credential.model

import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.identifiers.IssuerId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Evidence supporting a credential claim.
 *
 * @param id Optional identifier for the evidence
 * @param type Type of evidence (e.g., "DocumentVerification", "IdentityDocument")
 * @param evidenceDocument Optional document reference
 * @param verifier Optional verifier of the evidence
 * @param evidenceDate Optional date when evidence was created
 */
@Serializable
data class Evidence(
    val id: CredentialId? = null,
    val type: List<String>,
    val evidenceDocument: JsonElement? = null,
    val verifier: IssuerId? = null,
    val evidenceDate: String? = null
)

