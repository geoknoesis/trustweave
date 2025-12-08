package com.trustweave.credential.model.vc

import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.model.StatusPurpose
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * VC Credential Status - status information for revocation/suspension.
 * 
 * Per W3C VC Data Model, used to check revocation or suspension status.
 * 
 * **Examples:**
 * ```kotlin
 * val status = CredentialStatus(
 *     id = StatusListId("https://example.edu/status/24"),
 *     type = "StatusList2021Entry",
 *     statusPurpose = StatusPurpose.REVOCATION,
 *     statusListIndex = "94567",
 *     statusListCredential = StatusListId("https://example.edu/statuses/3")
 * )
 * ```
 */
@Serializable
data class CredentialStatus(
    val id: StatusListId,
    val type: String,  // e.g., "StatusList2021Entry", "RevocationList2020"
    val statusPurpose: StatusPurpose = StatusPurpose.REVOCATION,
    val statusListIndex: String? = null,
    val statusListCredential: StatusListId? = null,
    val formatData: Map<String, JsonElement> = emptyMap()  // Format-specific additional data
)

