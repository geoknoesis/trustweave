package org.trustweave.revocation.token

/**
 * Represents a signed Token Status List JWT.
 *
 * @param jwt The compact JWT string with `typ = "statuslist+jwt"`
 * @param statusListId The internal database identifier for the status list
 * @param uri The publicly reachable URI at which this token will be served
 */
data class TokenStatusListToken(
    val jwt: String,
    val statusListId: String,
    val uri: String
)
