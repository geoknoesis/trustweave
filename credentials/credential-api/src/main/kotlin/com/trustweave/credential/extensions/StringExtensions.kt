package com.trustweave.credential

import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.core.identifiers.Iri

/**
 * String extension functions for ergonomic API usage.
 * 
 * These extensions allow natural string-to-type conversions,
 * making the API more discoverable and easier to use.
 */

/**
 * Convert string to CredentialType.
 * 
 * **Example:**
 * ```kotlin
 * val type = "PersonCredential".toCredentialType()
 * ```
 */
fun String.toCredentialType(): CredentialType = CredentialType.fromString(this)

/**
 * Convert list of strings to CredentialTypes.
 * 
 * **Example:**
 * ```kotlin
 * val types = listOf("PersonCredential", "EducationCredential").toCredentialTypes()
 * ```
 */
fun List<String>.toCredentialTypes(): List<CredentialType> = map { it.toCredentialType() }

/**
 * Convert string to Issuer.
 * 
 * **Example:**
 * ```kotlin
 * val issuer = "did:key:issuer".asIssuer()
 * ```
 */
fun String.asIssuer(): Issuer = Issuer.from(this)

/**
 * Convert string to Iri and then to Issuer.
 */
fun String.asIssuerFromIri(): Issuer = Issuer.from(Iri(this))

