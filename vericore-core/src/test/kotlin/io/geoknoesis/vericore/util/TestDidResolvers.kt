package io.geoknoesis.vericore.util

import io.geoknoesis.vericore.credential.did.CredentialDidResolution
import io.geoknoesis.vericore.credential.did.CredentialDidResolver
import io.geoknoesis.vericore.did.DidResolutionResult
import io.geoknoesis.vericore.did.toCredentialDidResolution

fun booleanDidResolver(predicate: (String) -> Boolean): CredentialDidResolver =
    CredentialDidResolver { did -> CredentialDidResolution(isResolvable = predicate(did)) }

fun resultDidResolver(provider: (String) -> DidResolutionResult?): CredentialDidResolver =
    CredentialDidResolver { did -> provider(did)?.toCredentialDidResolution() }


