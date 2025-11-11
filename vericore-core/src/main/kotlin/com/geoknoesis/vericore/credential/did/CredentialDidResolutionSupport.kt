package com.geoknoesis.vericore.did

import com.geoknoesis.vericore.credential.did.CredentialDidResolution

/**
 * Converts a [DidResolutionResult] into a [CredentialDidResolution] used by credential tooling.
 */
fun DidResolutionResult.toCredentialDidResolution(): CredentialDidResolution =
    CredentialDidResolution(
        document = document,
        raw = this,
        metadata = resolutionMetadata,
        isResolvable = document != null
    )

