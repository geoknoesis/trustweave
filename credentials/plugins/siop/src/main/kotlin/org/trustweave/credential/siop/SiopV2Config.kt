package org.trustweave.credential.siop

import org.trustweave.credential.siop.models.SiopClientIdScheme
import org.trustweave.credential.siop.models.SiopResponseMode

data class SiopV2Config(
    val defaultClientIdScheme: SiopClientIdScheme = SiopClientIdScheme.DID,
    val supportedResponseModes: List<SiopResponseMode> = listOf(SiopResponseMode.DIRECT_POST),
    val idTokenSigningAlgorithms: List<String> = listOf("EdDSA", "ES256"),
    val vpTokenSigningAlgorithms: List<String> = listOf("EdDSA", "ES256"),
    val requestUriTimeoutSeconds: Long = 300,
)
