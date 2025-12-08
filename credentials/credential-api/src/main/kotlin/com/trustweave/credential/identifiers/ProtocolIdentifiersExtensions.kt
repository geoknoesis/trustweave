package com.trustweave.credential.identifiers

/**
 * Extension functions for safe parsing of protocol-related identifiers.
 * 
 * Provides ergonomic helpers for converting strings to typed identifier wrappers.
 * All functions follow the `toXxxOrNull()` pattern for safe parsing without exceptions.
 * 
 * **Example Usage:**
 * ```kotlin
 * // Safe parsing from user input or external data
 * val protocolName = userInput.toExchangeProtocolNameOrNull()
 *     ?: throw IllegalArgumentException("Invalid protocol name: $userInput")
 * 
 * // Collection operations
 * val validOfferIds = offerIdStrings.mapToOfferIdOrNull()
 * ```
 */

// ============================================================================
// ExchangeProtocolName Extensions
// ============================================================================

/**
 * Safe parsing: Convert String to ExchangeProtocolName, returns null if invalid.
 * 
 * Protocol names must be lowercase alphanumeric with hyphens.
 * 
 * **Example:**
 * ```kotlin
 * val protocol = "didcomm".toExchangeProtocolNameOrNull()
 * // Use ExchangeProtocolName.DidComm constant for known protocols
 * val knownProtocol = ExchangeProtocolName.DidComm
 * ```
 */
inline fun String.toExchangeProtocolNameOrNull(): ExchangeProtocolName? = 
    try { ExchangeProtocolName(this) } catch (e: IllegalArgumentException) { null }

/**
 * Require ExchangeProtocolName: Convert String to ExchangeProtocolName, throws if invalid.
 * 
 * Use this when you expect the string to always be valid.
 */
fun String.requireExchangeProtocolName(): ExchangeProtocolName = 
    toExchangeProtocolNameOrNull() ?: throw IllegalArgumentException(
        "String '$this' is not a valid protocol name. Must be lowercase alphanumeric with hyphens."
    )

// ============================================================================
// OfferId Extensions
// ============================================================================

/**
 * Safe parsing: Convert String to OfferId, returns null if invalid.
 */
inline fun String.toOfferIdOrNull(): OfferId? = 
    try { OfferId(this) } catch (e: IllegalArgumentException) { null }

/**
 * Require OfferId: Convert String to OfferId, throws if invalid.
 */
fun String.requireOfferId(): OfferId = 
    toOfferIdOrNull() ?: throw IllegalArgumentException("String '$this' is not a valid OfferId")

// ============================================================================
// RequestId Extensions
// ============================================================================

/**
 * Safe parsing: Convert String to RequestId, returns null if invalid.
 */
inline fun String.toRequestIdOrNull(): RequestId? = 
    try { RequestId(this) } catch (e: IllegalArgumentException) { null }

/**
 * Require RequestId: Convert String to RequestId, throws if invalid.
 */
fun String.requireRequestId(): RequestId = 
    toRequestIdOrNull() ?: throw IllegalArgumentException("String '$this' is not a valid RequestId")

// ============================================================================
// IssueId Extensions
// ============================================================================

/**
 * Safe parsing: Convert String to IssueId, returns null if invalid.
 */
inline fun String.toIssueIdOrNull(): IssueId? = 
    try { IssueId(this) } catch (e: IllegalArgumentException) { null }

/**
 * Require IssueId: Convert String to IssueId, throws if invalid.
 */
fun String.requireIssueId(): IssueId = 
    toIssueIdOrNull() ?: throw IllegalArgumentException("String '$this' is not a valid IssueId")

// ============================================================================
// PresentationId Extensions
// ============================================================================

/**
 * Safe parsing: Convert String to PresentationId, returns null if invalid.
 */
inline fun String.toPresentationIdOrNull(): PresentationId? = 
    try { PresentationId(this) } catch (e: IllegalArgumentException) { null }

/**
 * Require PresentationId: Convert String to PresentationId, throws if invalid.
 */
fun String.requirePresentationId(): PresentationId = 
    toPresentationIdOrNull() ?: throw IllegalArgumentException("String '$this' is not a valid PresentationId")

// ============================================================================
// Collection Extensions
// ============================================================================

/**
 * Map string list to ExchangeProtocolName list, filtering out invalid entries.
 * 
 * **Example:**
 * ```kotlin
 * val protocols = listOf("didcomm", "oidc4vci", "invalid-protocol!")
 *     .mapToExchangeProtocolNameOrNull()
 * // Result: [ExchangeProtocolName("didcomm"), ExchangeProtocolName("oidc4vci")]
 * ```
 */
fun List<String>.mapToExchangeProtocolNameOrNull(): List<ExchangeProtocolName> = 
    mapNotNull { it.toExchangeProtocolNameOrNull() }

/**
 * Map string list to OfferId list, filtering out invalid entries.
 */
fun List<String>.mapToOfferIdOrNull(): List<OfferId> = 
    mapNotNull { it.toOfferIdOrNull() }

/**
 * Map string list to RequestId list, filtering out invalid entries.
 */
fun List<String>.mapToRequestIdOrNull(): List<RequestId> = 
    mapNotNull { it.toRequestIdOrNull() }

/**
 * Map string list to IssueId list, filtering out invalid entries.
 */
fun List<String>.mapToIssueIdOrNull(): List<IssueId> = 
    mapNotNull { it.toIssueIdOrNull() }

/**
 * Map string list to PresentationId list, filtering out invalid entries.
 */
fun List<String>.mapToPresentationIdOrNull(): List<PresentationId> = 
    mapNotNull { it.toPresentationIdOrNull() }

