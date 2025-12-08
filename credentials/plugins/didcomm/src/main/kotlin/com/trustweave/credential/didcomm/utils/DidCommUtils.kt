package com.trustweave.credential.didcomm.utils

import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.VerificationMethod

/**
 * Utility functions for DIDComm operations.
 */
object DidCommUtils {
    /**
     * Finds a key agreement key from a DID document.
     *
     * @param document The DID document
     * @param preferredKeyId Optional preferred key ID
     * @return The key agreement verification method, or null if not found
     */
    fun findKeyAgreementKey(
        document: DidDocument,
        preferredKeyId: String? = null
    ): VerificationMethod? {
        // If preferred key ID is provided, try to find it first
        if (preferredKeyId != null) {
            val preferred = document.verificationMethod.find { vm ->
                vm.id.toString() == preferredKeyId || vm.id.toString().endsWith("#$preferredKeyId")
            }
            if (preferred != null && document.keyAgreement.any { it.toString() == preferred.id.toString() }) {
                return preferred
            }
        }

        // Find first key agreement key
        val keyAgreementIds = document.keyAgreement.map { it.toString() }
        if (keyAgreementIds.isEmpty()) {
            // Fallback to authentication keys if no key agreement keys
            val authIds = document.authentication.map { it.toString() }
            if (authIds.isNotEmpty()) {
                return document.verificationMethod.find { vm ->
                    authIds.contains(vm.id.toString()) || authIds.any { id -> vm.id.toString().endsWith(id) }
                }
            }
            return null
        }

        // Find verification method for key agreement
        return document.verificationMethod.find { vm ->
            keyAgreementIds.contains(vm.id.toString()) ||
            keyAgreementIds.any { id -> vm.id.toString().endsWith(id) }
        } ?: document.verificationMethod.firstOrNull()
    }

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

    /**
     * Checks if a DID document has DIDComm messaging service.
     *
     * @param document The DID document
     * @return True if the document has a DIDComm messaging service
     */
    fun hasDidCommService(document: DidDocument): Boolean {
        return document.service.any { service ->
            service.type == "DIDCommMessaging" ||
            service.type.contains("DIDComm", ignoreCase = true)
        }
    }

    /**
     * Gets the DIDComm service endpoint from a DID document.
     *
     * @param document The DID document
     * @return The service endpoint URL, or null if not found
     */
    fun getDidCommServiceEndpoint(document: DidDocument): String? {
        val service = document.service.find { service ->
            service.type == "DIDCommMessaging" ||
            service.type.contains("DIDComm", ignoreCase = true)
        }

        return when (val endpoint = service?.serviceEndpoint) {
            is String -> endpoint
            is Map<*, *> -> {
                // Handle service endpoint object
                (endpoint["uri"] as? String) ?: (endpoint["url"] as? String)
            }
            is List<*> -> {
                // Handle service endpoint array
                (endpoint.firstOrNull() as? String)
            }
            else -> null
        }
    }
}

