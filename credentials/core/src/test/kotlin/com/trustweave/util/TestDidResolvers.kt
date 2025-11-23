package com.trustweave.util

import com.trustweave.did.resolution.DidResolver
import com.trustweave.did.DidResolutionResult
import com.trustweave.did.DidDocument

fun booleanDidResolver(predicate: (String) -> Boolean): DidResolver =
    DidResolver { did -> 
        if (predicate(did)) {
            DidResolutionResult(document = DidDocument(id = did))
        } else {
            null
        }
    }

fun resultDidResolver(provider: (String) -> DidResolutionResult?): DidResolver =
    DidResolver { did -> provider(did) }


