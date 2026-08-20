package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata

/**
 * Result of the DID resolution function per DID Resolution 1.0 §4.
 *
 * The three outputs of `resolve` — `didDocument`, `didDocumentMetadata` and
 * `didResolutionMetadata` — are modelled as a sealed hierarchy so that the spec's invariants
 * hold by construction:
 *
 * - [Success] is the only variant carrying a document.
 * - [Deactivated] carries no document and forces `deactivated = true` (§4.4).
 * - [Failure] carries no document, empty document metadata, and a non-null RFC 9457 error (§4).
 *
 * **Example Usage:**
 * ```kotlin
 * when (val result = resolver.resolve(did)) {
 *     is DidResolutionResult.Success -> println(result.document.id)
 *     is DidResolutionResult.Deactivated -> println("deactivated: ${result.did}")
 *     is DidResolutionResult.Failure -> println(result.error?.type)
 * }
 * ```
 */
sealed class DidResolutionResult {

    /**
     * Resolution succeeded.
     *
     * @param document the resolved DID document; its `id` equals the DID that was resolved
     * @param documentMetadata §4.3 document metadata
     * @param resolutionMetadata §4.2 resolution metadata
     */
    data class Success(
        val document: DidDocument,
        val documentMetadata: DidDocumentMetadata = DidDocumentMetadata(),
        val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata()
    ) : DidResolutionResult()

    /**
     * The DID exists but has been deactivated (§4.4).
     *
     * Per the spec the resolver returns no document in this case; the caller learns of the
     * deactivation from [documentMetadata]. This is not an error — the §12.1 binding maps it
     * to HTTP 410, not to a 4xx error response.
     */
    data class Deactivated(
        val did: Did,
        val documentMetadata: DidDocumentMetadata = DidDocumentMetadata(deactivated = true),
        val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata()
    ) : DidResolutionResult() {
        init {
            require(documentMetadata.deactivated) {
                "Deactivated result requires documentMetadata.deactivated = true (§4.4)"
            }
        }
    }

    /**
     * Resolution failed. Every variant carries a non-null [DidResolutionMetadata.error],
     * enforced by each variant's own `init` block (§4).
     *
     * The check lives on each subtype rather than once here on [Failure] itself: [Failure] has
     * no property of its own for `resolutionMetadata` — each subtype declares it independently
     * as a `data class` constructor property — and a base-class `init` block runs *before* a
     * derived `data class`'s own constructor properties are assigned, so it would observe an
     * uninitialized value if it tried to read an override from here instead.
     */
    sealed class Failure : DidResolutionResult() {

        /** §4.4: the DID does not exist. */
        data class NotFound(
            val did: Did,
            val reason: String? = null,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.notFound(reason ?: "DID not found: ${did.value}")
            )
        ) : Failure() {
            init {
                require(resolutionMetadata.error != null) {
                    "Failure.NotFound requires a non-null resolutionMetadata.error (DID Resolution 1.0 §4)"
                }
            }
        }

        /** §4.4 step 1: the input does not conform to the DID syntax. */
        data class InvalidFormat(
            val did: String,
            val reason: String,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.invalidDid(reason)
            )
        ) : Failure() {
            init {
                require(resolutionMetadata.error != null) {
                    "Failure.InvalidFormat requires a non-null resolutionMetadata.error (DID Resolution 1.0 §4)"
                }
            }
        }

        /** §4.4 step 2: the DID method is not supported by this resolver. */
        data class MethodNotRegistered(
            val method: String,
            val availableMethods: List<String> = emptyList(),
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.methodNotSupported("DID method '$method' is not registered")
            )
        ) : Failure() {
            init {
                require(resolutionMetadata.error != null) {
                    "Failure.MethodNotRegistered requires a non-null resolutionMetadata.error (DID Resolution 1.0 §4)"
                }
            }
        }

        /** §4.4 final step: an unexpected error during resolution. */
        data class ResolutionError(
            val did: Did,
            val reason: String,
            val cause: Throwable? = null,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.internalError(reason)
            )
        ) : Failure() {
            init {
                require(resolutionMetadata.error != null) {
                    "Failure.ResolutionError requires a non-null resolutionMetadata.error (DID Resolution 1.0 §4)"
                }
            }
        }

        /**
         * §4.4 steps 3 and 4: a resolution option is unsupported or invalid, or the requested
         * representation is not supported.
         *
         * @param errorType [DidErrorType.FEATURE_NOT_SUPPORTED] (default), [DidErrorType.INVALID_OPTIONS],
         *   or [DidErrorType.REPRESENTATION_NOT_SUPPORTED]
         */
        data class OptionsError(
            val did: Did?,
            val reason: String,
            val errorType: String = DidErrorType.FEATURE_NOT_SUPPORTED,
            val resolutionMetadata: DidResolutionMetadata = DidResolutionMetadata(
                error = DidResolutionError.of(errorType, reason)
            )
        ) : Failure() {
            init {
                require(resolutionMetadata.error != null) {
                    "Failure.OptionsError requires a non-null resolutionMetadata.error (DID Resolution 1.0 §4)"
                }
            }
        }
    }
}
