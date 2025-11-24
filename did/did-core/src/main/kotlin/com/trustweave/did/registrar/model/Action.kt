package com.trustweave.did.registrar.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents an action required to complete a DID operation.
 * 
 * According to the DID Registration specification, when an operation's state is "action",
 * the `action` field contains details about what additional steps are required.
 * 
 * Common action types:
 * - `redirect`: Redirect user to a URL (e.g., for OAuth flow)
 * - `sign`: Request signature from external wallet
 * - `wait`: Wait for external process to complete
 * - `retry`: Retry the operation with different parameters
 * 
 * @see https://identity.foundation/did-registration/
 */
@Serializable
data class Action(
    /**
     * Type of action required.
     * 
     * Common values:
     * - `redirect`: Redirect to a URL
     * - `sign`: Request signature
     * - `wait`: Wait for external process
     * - `retry`: Retry operation
     * - Method-specific action types
     */
    val type: String,
    
    /**
     * URL to redirect to or endpoint to call.
     * Used for `redirect` and some `sign` action types.
     */
    val url: String? = null,
    
    /**
     * Additional data required for the action.
     * 
     * Structure depends on action type:
     * - For `redirect`: May contain OAuth parameters, state, etc.
     * - For `sign`: May contain signing request data
     * - For `wait`: May contain polling endpoint, timeout, etc.
     */
    val data: Map<String, JsonElement>? = null,
    
    /**
     * Description or instructions for the action.
     * Human-readable text explaining what needs to be done.
     */
    val description: String? = null
)

