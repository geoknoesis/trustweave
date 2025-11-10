package io.geoknoesis.vericore.did

import io.geoknoesis.vericore.credential.did.CredentialDidResolution

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

