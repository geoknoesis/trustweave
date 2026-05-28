package org.trustweave.credential.didcomm.utils

import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.ServiceEndpoint
import org.trustweave.did.model.VerificationMethod

/**
 * Finds a key agreement key from a DID document.
 *
 * @param preferredKeyId Optional preferred key ID
 * @return The key agreement verification method, or null if not found
 */
fun DidDocument.findKeyAgreementKey(
    preferredKeyId: String? = null
): VerificationMethod? {
    // If preferred key ID is provided, try to find it first
    if (preferredKeyId != null) {
        val preferred = verificationMethod.find { vm ->
            vm.id.toString() == preferredKeyId || vm.id.toString().endsWith("#$preferredKeyId")
        }
        if (preferred != null && keyAgreement.any { it.toString() == preferred.id.toString() }) {
            return preferred
        }
    }

    // Find first key agreement key
    val keyAgreementIds = keyAgreement.map { it.toString() }
    if (keyAgreementIds.isEmpty()) {
        // Fallback to authentication keys if no key agreement keys
        val authIds = authentication.map { it.toString() }
        if (authIds.isNotEmpty()) {
            return verificationMethod.find { vm ->
                authIds.contains(vm.id.toString()) || authIds.any { id -> vm.id.toString().endsWith(id) }
            }
        }
        return null
    }

    // Find verification method for key agreement
    return verificationMethod.find { vm ->
        keyAgreementIds.contains(vm.id.toString()) ||
        keyAgreementIds.any { id -> vm.id.toString().endsWith(id) }
    } ?: verificationMethod.firstOrNull()
}

/**
 * Checks if a DID document has DIDComm messaging service.
 *
 * @return True if the document has a DIDComm messaging service
 */
val DidDocument.hasDidCommService: Boolean
    get() = service.any { service ->
        service.type.any { t ->
            t == "DIDCommMessaging" || t.contains("DIDComm", ignoreCase = true)
        }
    }

/**
 * Gets the DIDComm service endpoint from a DID document.
 *
 * @return The service endpoint URL, or null if not found
 */
fun DidDocument.getDidCommServiceEndpoint(): String? {
    val service = service.find { service ->
        service.type.any { t ->
            t == "DIDCommMessaging" || t.contains("DIDComm", ignoreCase = true)
        }
    }

    return when (val endpoint = service?.serviceEndpoint) {
        is ServiceEndpoint.Url -> endpoint.url
        is ServiceEndpoint.ObjectEndpoint -> {
            // Handle service endpoint object
            (endpoint.data["uri"] as? String) ?: (endpoint.data["url"] as? String)
        }
        is ServiceEndpoint.ArrayEndpoint -> {
            // Handle service endpoint array: first URL string entry
            (endpoint.endpoints.firstOrNull() as? ServiceEndpoint.Url)?.url
        }
        null -> null
    }
}

/**
 * Utility functions for DIDComm operations.
 */
object DidCommUtils {
    /**
     * Extracts key ID from a verification method reference.
     *
     * @param vmId Verification method ID (e.g., "did:key:abc#key-1" or "#key-1")
     * @param did The DID (for resolving relative references)
     * @return The key ID (fragment part)
     */
    fun extractKeyId(vmId: String, did: String): String {
        return if (vmId.contains("#")) {
            vmId.substringAfter("#")
        } else if (vmId.startsWith(did)) {
            vmId.substringAfter("$did#")
        } else {
            vmId
        }
    }

    /**
     * Resolves a verification method reference to a full ID.
     *
     * @param vmId Verification method ID (may be relative or absolute)
     * @param did The DID (for resolving relative references)
     * @return The full verification method ID
     */
    fun resolveVerificationMethodId(vmId: String, did: String): String {
        return if (vmId.startsWith("did:")) {
            vmId // Already absolute
        } else if (vmId.startsWith("#")) {
            "$did${vmId}" // Relative with #
        } else {
            "$did#$vmId" // Relative without #
        }
    }
}

