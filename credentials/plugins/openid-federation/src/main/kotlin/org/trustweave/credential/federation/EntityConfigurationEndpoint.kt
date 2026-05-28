package org.trustweave.credential.federation

/**
 * Utilities for the OpenID Federation Entity Configuration well-known endpoint.
 *
 * Each federation entity MUST publish a self-signed Entity Statement (its
 * Entity Configuration) at:
 *
 *   `{entityId}/.well-known/openid-federation`
 *
 * The response is a signed JWT with `Content-Type: application/entity-statement+jwt`.
 *
 * Spec: https://openid.net/specs/openid-federation-1_0.html §4.3
 */
object EntityConfigurationEndpoint {

    /**
     * Well-known path appended to an entity identifier to form the
     * Entity Configuration URL.
     */
    const val WELL_KNOWN_PATH = "/.well-known/openid-federation"

    /**
     * Builds the Entity Configuration URL for the given [entityId].
     *
     * Trailing slashes on [entityId] are stripped before appending the path,
     * so both `https://example.com` and `https://example.com/` produce the
     * same canonical URL.
     *
     * @param entityId The entity identifier URI.
     * @return The full URL at which the entity's Entity Configuration JWT is published.
     */
    fun getUrl(entityId: String): String = entityId.trimEnd('/') + WELL_KNOWN_PATH
}
