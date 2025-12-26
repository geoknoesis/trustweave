package org.trustweave.did.util

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.validation.DidValidator

/**
 * Utility functions for DID operations.
 *
 * This module provides helper functions for common DID-related operations
 * that don't belong to a specific class but are useful across the codebase.
 */

/**
 * Normalizes a key ID by extracting the fragment identifier.
 *
 * Key IDs can be provided in multiple formats:
 * - Full DID URL: `did:key:z6Mk...#key-1` → returns `key-1`
 * - Fragment only: `#key-1` → returns `key-1`
 * - Plain key ID: `key-1` → returns `key-1`
 *
 * **Example:**
 * ```kotlin
 * normalizeKeyId("did:key:z6Mk...#key-1") // returns "key-1"
 * normalizeKeyId("#key-1")                // returns "key-1"
 * normalizeKeyId("key-1")                  // returns "key-1"
 * normalizeKeyId("did:key:z6Mk...")       // returns "did:key:z6Mk..."
 * ```
 *
 * @param keyId The key ID to normalize (may include DID URL or fragment)
 * @return The normalized key ID (fragment part if present, otherwise the original keyId)
 */
fun normalizeKeyId(keyId: String): String {
    if (keyId.isEmpty()) return keyId

    val fragmentIndex = keyId.indexOf('#')
    return if (fragmentIndex >= 0 && fragmentIndex < keyId.length - 1) {
        // Has # and content after it
        keyId.substring(fragmentIndex + 1)
    } else {
        // No # or # is at the end (edge case)
        keyId
    }
}

/**
 * Validates a DID string format.
 *
 * **Example:**
 * ```kotlin
 * validateDid("did:key:123")  // returns true
 * validateDid("invalid")       // returns false
 * ```
 *
 * @param did The DID string to validate
 * @return true if the DID format is valid, false otherwise
 */
fun validateDid(did: String): Boolean {
    return DidValidator.validateFormat(did).isValid()
}

/**
 * Safely parses a DID string, returning null if invalid.
 *
 * **Example:**
 * ```kotlin
 * parseDidOrNull("did:key:123")  // returns Did("did:key:123")
 * parseDidOrNull("invalid")      // returns null
 * ```
 *
 * @param didString The DID string to parse
 * @return Did instance if valid, null otherwise
 */
fun parseDidOrNull(didString: String): Did? {
    return try {
        Did(didString)
    } catch (e: IllegalArgumentException) {
        null
    }
}

/**
 * Extracts all verification method IDs from a DID document.
 *
 * This includes both embedded verification methods and those referenced
 * in relationship arrays (authentication, assertionMethod, etc.).
 *
 * **Example:**
 * ```kotlin
 * val allVmIds = extractAllVerificationMethodIds(document)
 * ```
 *
 * @param document The DID document
 * @return Set of all unique verification method IDs in the document
 */
fun extractAllVerificationMethodIds(document: DidDocument): Set<VerificationMethodId> {
    val vmIds = mutableSetOf<VerificationMethodId>()
    
    // Add embedded verification methods
    document.verificationMethod.forEach { vm ->
        vmIds.add(vm.id)
    }
    
    // Add referenced verification methods
    vmIds.addAll(document.authentication)
    vmIds.addAll(document.assertionMethod)
    vmIds.addAll(document.keyAgreement)
    vmIds.addAll(document.capabilityInvocation)
    vmIds.addAll(document.capabilityDelegation)
    
    return vmIds
}

/**
 * Finds a verification method by ID in a DID document.
 *
 * **Example:**
 * ```kotlin
 * val vm = findVerificationMethod(document, vmId)
 * ```
 *
 * @param document The DID document
 * @param vmId The verification method ID to find
 * @return The verification method if found, null otherwise
 */
fun findVerificationMethod(
    document: DidDocument,
    vmId: VerificationMethodId
): VerificationMethod? {
    return document.verificationMethod.firstOrNull { it.id == vmId }
}

/**
 * Checks if a DID document has a specific verification method.
 *
 * **Example:**
 * ```kotlin
 * if (hasVerificationMethod(document, vmId)) {
 *     // Use the verification method
 * }
 * ```
 *
 * @param document The DID document
 * @param vmId The verification method ID to check
 * @return true if the document contains the verification method, false otherwise
 */
fun hasVerificationMethod(
    document: DidDocument,
    vmId: VerificationMethodId
): Boolean {
    return document.verificationMethod.any { it.id == vmId }
}

/**
 * Gets all service endpoints of a specific type from a DID document.
 *
 * **Example:**
 * ```kotlin
 * val messagingServices = getServicesByType(document, "DIDCommMessaging")
 * ```
 *
 * @param document The DID document
 * @param serviceType The service type to filter by
 * @return List of services matching the type
 */
fun getServicesByType(
    document: DidDocument,
    serviceType: String
): List<org.trustweave.did.model.DidService> {
    return document.service.filter { it.type == serviceType }
}

/**
 * Checks if a DID document has services of a specific type.
 *
 * **Example:**
 * ```kotlin
 * if (hasServiceType(document, "LinkedDomains")) {
 *     // Handle LinkedDomains service
 * }
 * ```
 *
 * @param document The DID document
 * @param serviceType The service type to check
 * @return true if the document has services of the specified type, false otherwise
 */
fun hasServiceType(
    document: DidDocument,
    serviceType: String
): Boolean {
    return document.service.any { it.type == serviceType }
}

