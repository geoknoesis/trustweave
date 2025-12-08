package com.trustweave.did.dsl

import com.trustweave.did.DidMethod
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidCreationOptionsBuilder
import com.trustweave.did.model.DidDocument
import com.trustweave.did.didCreationOptions

/**
 * Extension functions for [DidMethod] to provide fluent DSL.
 *
 * These extensions allow creating DIDs using a builder DSL pattern.
 */

/**
 * Creates a DID using a builder DSL.
 *
 * **Example Usage:**
 * ```kotlin
 * val method = KeyDidMethod(kms)
 * val document = method.createDidWith {
 *     algorithm = KeyAlgorithm.ED25519
 *     forAuthentication()
 *     forAssertion()
 * }
 * ```
 *
 * @param block Builder lambda for configuring DID creation options
 * @return The created DID document
 */
suspend fun DidMethod.createDidWith(
    block: DidCreationOptionsBuilder.() -> Unit
): DidDocument {
    val options = didCreationOptions(block)
    return createDid(options)
}

