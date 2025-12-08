package com.trustweave.credential

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.getFormatId

/**
 * Public extension functions for CredentialService.
 */

/**
 * Check if VerifiableCredential supports selective disclosure.
 */
fun VerifiableCredential.supportsSelectiveDisclosure(service: CredentialService): Boolean {
    val proofSuiteId = this.proof?.getFormatId() ?: return false
    return service.supportsCapability(proofSuiteId) { selectiveDisclosure }
}
