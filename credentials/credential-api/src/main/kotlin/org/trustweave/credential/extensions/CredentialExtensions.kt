package org.trustweave.credential.extensions

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.vc.CredentialSubject
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Extension functions for common VerifiableCredential and VerifiablePresentation operations.
 * 
 * Provides convenient, ergonomic methods for common credential and presentation manipulations.
 * These extensions improve code readability and reduce boilerplate.
 */

/**
 * Check if a credential is expired at the current time.
 * 
 * A credential is considered expired if it has an expiration date and the current time
 * is after that expiration date.
 * 
 * **Example:**
 * ```kotlin
 * if (credential.isExpired()) {
 *     println("Credential expired on ${credential.expirationDate}")
 * }
 * ```
 * 
 * @return True if the credential has an expiration date and it has passed, false otherwise
 */
fun VerifiableCredential.isExpired(): Boolean {
    return expirationDate?.let { Clock.System.now() > it } ?: false
}

/**
 * Check if a credential is expired at a specific time.
 * 
 * **Example:**
 * ```kotlin
 * val checkTime = Clock.System.now().plus(30.days)
 * if (credential.isExpiredAt(checkTime)) {
 *     println("Credential will be expired at $checkTime")
 * }
 * ```
 * 
 * @param instant The instant to check expiration against
 * @return True if the credential has an expiration date and it is before the given instant
 */
fun VerifiableCredential.isExpiredAt(instant: Instant): Boolean {
    return expirationDate?.let { instant > it } ?: false
}

/**
 * Check if a credential is valid (not expired) at the current time.
 * 
 * A credential is valid if it has no expiration date or if the current time is before
 * the expiration date.
 * 
 * **Example:**
 * ```kotlin
 * if (credential.isValid()) {
 *     println("Credential is still valid")
 * }
 * ```
 * 
 * @return True if the credential is not expired, false otherwise
 */
fun VerifiableCredential.isValid(): Boolean = !isExpired()

/**
 * Check if a credential is valid (not expired) at a specific time.
 * 
 * @param instant The instant to check validity against
 * @return True if the credential is not expired at the given instant
 */
fun VerifiableCredential.isValidAt(instant: Instant): Boolean = !isExpiredAt(instant)

/**
 * Get all credential types as strings.
 * 
 * Convenience method to extract type strings from the credential's type list.
 * 
 * **Example:**
 * ```kotlin
 * val types = credential.typeStrings()
 * // Result: ["VerifiableCredential", "UniversityDegreeCredential"]
 * ```
 * 
 * @return List of credential type strings
 */
fun VerifiableCredential.typeStrings(): List<String> {
    return type.map { it.value }
}

/**
 * Check if a credential has a specific type.
 * 
 * **Example:**
 * ```kotlin
 * if (credential.hasType("UniversityDegreeCredential")) {
 *     println("This is a university degree credential")
 * }
 * ```
 * 
 * @param typeString The type string to check for
 * @return True if the credential has the specified type
 */
fun VerifiableCredential.hasType(typeString: String): Boolean {
    return type.any { it.value == typeString }
}

/**
 * Get a claim value from the credential subject.
 * 
 * Returns the claim value for the given key, or null if the claim doesn't exist.
 * 
 * **Example:**
 * ```kotlin
 * val name = credential.getClaim("name")?.content
 * val age = credential.getClaim("age")?.longOrNull
 * ```
 * 
 * @param key The claim key
 * @return The claim value as JsonElement, or null if not found
 */
fun VerifiableCredential.getClaim(key: String) = credentialSubject.claims[key]

/**
 * Check if a credential has a specific claim.
 * 
 * **Example:**
 * ```kotlin
 * if (credential.hasClaim("email")) {
 *     println("Credential contains email claim")
 * }
 * ```
 * 
 * @param key The claim key to check for
 * @return True if the credential subject contains the claim
 */
fun VerifiableCredential.hasClaim(key: String): Boolean {
    return credentialSubject.claims.containsKey(key)
}

/**
 * Get the number of credentials in a presentation.
 * 
 * **Example:**
 * ```kotlin
 * val count = presentation.credentialCount()
 * println("Presentation contains $count credentials")
 * ```
 * 
 * @return The number of credentials in the presentation
 */
fun VerifiablePresentation.credentialCount(): Int {
    return verifiableCredential.size
}

/**
 * Check if a presentation is empty (contains no credentials).
 * 
 * **Example:**
 * ```kotlin
 * if (presentation.isEmpty()) {
 *     throw IllegalArgumentException("Presentation cannot be empty")
 * }
 * ```
 * 
 * @return True if the presentation has no credentials
 */
fun VerifiablePresentation.isEmpty(): Boolean {
    return verifiableCredential.isEmpty()
}

/**
 * Check if a presentation is not empty (contains at least one credential).
 * 
 * @return True if the presentation has at least one credential
 */
fun VerifiablePresentation.isNotEmpty(): Boolean {
    return verifiableCredential.isNotEmpty()
}

/**
 * Get all credential types from all credentials in a presentation.
 * 
 * Returns a flat list of all type strings from all credentials in the presentation.
 * 
 * **Example:**
 * ```kotlin
 * val allTypes = presentation.allCredentialTypes()
 * // Result: ["VerifiableCredential", "UniversityDegreeCredential", "DriverLicenseCredential"]
 * ```
 * 
 * @return List of all credential type strings across all credentials
 */
fun VerifiablePresentation.allCredentialTypes(): List<String> {
    return verifiableCredential.flatMap { it.typeStrings() }.distinct()
}

/**
 * Filter credentials in a presentation by type.
 * 
 * Returns a list of credentials that have the specified type.
 * 
 * **Example:**
 * ```kotlin
 * val degreeCredentials = presentation.credentialsByType("UniversityDegreeCredential")
 * ```
 * 
 * @param typeString The type string to filter by
 * @return List of credentials matching the specified type
 */
fun VerifiablePresentation.credentialsByType(typeString: String): List<VerifiableCredential> {
    return verifiableCredential.filter { it.hasType(typeString) }
}



