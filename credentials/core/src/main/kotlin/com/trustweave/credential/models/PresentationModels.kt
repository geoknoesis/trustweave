package com.trustweave.credential.models

import kotlinx.serialization.Serializable

/**
 * Verifiable Presentation models.
 * 
 * W3C VC Data Model 1.1 compliant presentation types.
 */

/**
 * Verifiable Presentation as defined by W3C VC Data Model 1.1.
 * 
 * @param id Optional unique identifier for the presentation
 * @param type List of presentation types (must include "VerifiablePresentation")
 * @param verifiableCredential List of verifiable credentials in the presentation
 * @param holder DID of the presentation holder
 * @param proof Optional cryptographic proof for the presentation
 * @param challenge Optional challenge string (for authentication)
 * @param domain Optional domain string (for authentication)
 */
@Serializable
data class VerifiablePresentation(
    val id: String? = null,
    val type: List<String>,
    val verifiableCredential: List<VerifiableCredential>,
    val holder: String, // DID
    val proof: Proof? = null,
    val challenge: String? = null,
    val domain: String? = null
)

