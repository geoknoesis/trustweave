package com.trustweave.util

import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.DidDocument

fun booleanDidResolver(predicate: (String) -> Boolean): DidResolver =
    DidResolver { did ->
        if (predicate(did.value)) {
            DidResolutionResult.Success(document = DidDocument(id = did.value))
        } else {
            null
        }
    }

fun resultDidResolver(provider: (String) -> DidResolutionResult?): DidResolver =
    DidResolver { did -> provider(did.value) }
