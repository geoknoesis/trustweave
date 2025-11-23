package com.trustweave.util

import com.trustweave.credential.did.CredentialDidResolution
import com.trustweave.credential.did.CredentialDidResolver
import com.trustweave.did.DidResolutionResult
import com.trustweave.did.toCredentialDidResolution

fun booleanDidResolver(predicate: (String) -> Boolean): CredentialDidResolver =
    CredentialDidResolver { did -> CredentialDidResolution(isResolvable = predicate(did)) }

fun resultDidResolver(provider: (String) -> DidResolutionResult?): CredentialDidResolver =
    CredentialDidResolver { did -> provider(did)?.toCredentialDidResolution() }
