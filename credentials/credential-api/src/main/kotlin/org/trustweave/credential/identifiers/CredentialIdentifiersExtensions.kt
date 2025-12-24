package org.trustweave.credential.identifiers

import org.trustweave.core.identifiers.Iri

/**
 * Extension functions for safe parsing of credential-related identifiers.
 * 
 * Provides ergonomic helpers for converting strings and IRIs to typed identifier wrappers.
 * All functions follow the `toXxxOrNull()` pattern for safe parsing without exceptions.
 */

/**
 * Safe parsing: Convert String to CredentialId, returns null if invalid.
 */
inline fun String.toCredentialIdOrNull(): CredentialId? = 
    try { CredentialId(this) } catch (e: IllegalArgumentException) { null }

/**
 * Safe parsing: Convert String to IssuerId, returns null if invalid.
 */
inline fun String.toIssuerIdOrNull(): IssuerId? = 
    try { IssuerId(this) } catch (e: IllegalArgumentException) { null }

/**
 * Safe parsing: Convert String to StatusListId, returns null if invalid.
 */
inline fun String.toStatusListIdOrNull(): StatusListId? = 
    try { StatusListId(this) } catch (e: IllegalArgumentException) { null }

/**
 * Safe parsing: Convert String to SchemaId, returns null if invalid.
 */
inline fun String.toSchemaIdOrNull(): SchemaId? = 
    try { SchemaId(this) } catch (e: IllegalArgumentException) { null }

/**
 * Type narrowing: Convert Iri to CredentialId.
 */
fun Iri.asCredentialIdOrNull(): CredentialId? = 
    try { CredentialId(value) } catch (e: IllegalArgumentException) { null }

/**
 * Type narrowing: Convert Iri to IssuerId.
 */
fun Iri.asIssuerIdOrNull(): IssuerId? = 
    try { IssuerId(value) } catch (e: IllegalArgumentException) { null }

/**
 * Require CredentialId: Convert Iri to CredentialId, throws if invalid.
 */
fun Iri.requireCredentialId(): CredentialId = 
    asCredentialIdOrNull() ?: throw IllegalArgumentException("IRI '$value' is not a valid CredentialId")

/**
 * Require IssuerId: Convert Iri to IssuerId, throws if invalid.
 */
fun Iri.requireIssuerId(): IssuerId = 
    asIssuerIdOrNull() ?: throw IllegalArgumentException("IRI '$value' is not a valid IssuerId")

/**
 * Collection extensions for credential identifiers.
 */

/**
 * Map string list to CredentialId list, filtering out invalid entries.
 */
fun List<String>.mapToCredentialIdOrNull(): List<CredentialId> = 
    mapNotNull { it.toCredentialIdOrNull() }

/**
 * Map string list to IssuerId list, filtering out invalid entries.
 */
fun List<String>.mapToIssuerIdOrNull(): List<IssuerId> = 
    mapNotNull { it.toIssuerIdOrNull() }

/**
 * Filter Iri list to extract only CredentialIds.
 */
fun List<Iri>.filterCredentialIds(): List<CredentialId> = 
    mapNotNull { it.asCredentialIdOrNull() }

/**
 * Filter Iri list to extract only IssuerIds.
 */
fun List<Iri>.filterIssuerIds(): List<IssuerId> = 
    mapNotNull { it.asIssuerIdOrNull() }

