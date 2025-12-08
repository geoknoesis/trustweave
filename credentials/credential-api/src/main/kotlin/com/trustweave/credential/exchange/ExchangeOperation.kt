package com.trustweave.credential.exchange

/**
 * Supported exchange operations.
 * 
 * Represents the **capabilities** that a protocol can support.
 * This is protocol-agnostic and describes what operations a protocol
 * is capable of performing, not the intent of specific messages.
 * 
 * **Relationship to Protocol-Specific Concepts:**
 * - ExchangeOperation = "What operations does this protocol support?" (protocol capability, protocol-agnostic)
 * - Protocol-specific goal codes = Message intent (e.g., DIDComm goal codes in message bodies)
 * 
 * **Example:**
 * ```kotlin
 * val capabilities = ExchangeProtocolCapabilities(
 *     supportedOperations = setOf(
 *         ExchangeOperation.OFFER_CREDENTIAL,
 *         ExchangeOperation.REQUEST_CREDENTIAL,
 *         ExchangeOperation.ISSUE_CREDENTIAL
 *     )
 * )
 * ```
 */
enum class ExchangeOperation {
    /** Issuer offers a credential to holder */
    OFFER_CREDENTIAL,

    /** Holder requests a credential from issuer */
    REQUEST_CREDENTIAL,

    /** Issuer issues a credential to holder */
    ISSUE_CREDENTIAL,

    /** Verifier requests a proof from prover */
    REQUEST_PROOF,

    /** Prover presents a proof to verifier */
    PRESENT_PROOF
}

