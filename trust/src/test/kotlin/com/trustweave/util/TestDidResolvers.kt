package com.trustweave.util

import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.model.DidDocument

fun booleanDidResolver(predicate: (String) -> Boolean): DidResolver =
    DidResolver { did ->
        if (predicate(did.value)) {
            DidResolutionResult.Success(document = DidDocument(id = did))
        } else {
            DidResolutionResult.Failure.NotFound(did = did)
        }
    }

fun resultDidResolver(provider: (String) -> DidResolutionResult?): DidResolver =
    DidResolver { did -> provider(did.value) ?: DidResolutionResult.Failure.NotFound(did = did) }
