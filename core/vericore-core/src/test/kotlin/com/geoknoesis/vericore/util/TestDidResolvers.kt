package com.geoknoesis.vericore.util

import com.geoknoesis.vericore.credential.did.CredentialDidResolution
import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.did.DidResolutionResult
import com.geoknoesis.vericore.did.toCredentialDidResolution

fun booleanDidResolver(predicate: (String) -> Boolean): CredentialDidResolver =
    CredentialDidResolver { did -> CredentialDidResolution(isResolvable = predicate(did)) }

fun resultDidResolver(provider: (String) -> DidResolutionResult?): CredentialDidResolver =
    CredentialDidResolver { did -> provider(did)?.toCredentialDidResolution() }


