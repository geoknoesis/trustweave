package org.trustweave.did.dsl

import org.trustweave.did.DidMethod
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidCreationOptionsBuilder
import org.trustweave.did.model.DidDocument
import org.trustweave.did.didCreationOptions

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

