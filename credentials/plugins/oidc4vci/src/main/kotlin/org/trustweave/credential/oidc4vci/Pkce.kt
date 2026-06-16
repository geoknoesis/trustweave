package org.trustweave.credential.oidc4vci

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * RFC 7636 PKCE (S256) helper for the OID4VCI authorization-code flow.
 *
 * The wallet generates a [generateCodeVerifier], sends [codeChallengeS256] of it as
 * `code_challenge` (with `code_challenge_method=S256`) on the authorization request, and supplies
 * the same verifier to [Oidc4VciService.createCredentialRequest] for the token exchange. This binds
 * the authorization code to the client, preventing replay of an intercepted code (OAuth 2.1).
 */
public object Pkce {
    private val random = SecureRandom()
    private val b64Url = Base64.getUrlEncoder().withoutPadding()

    /** A high-entropy `code_verifier` (RFC 7636 §4.1): 32 random bytes as a 43-char base64url string. */
    public fun generateCodeVerifier(): String =
        b64Url.encodeToString(ByteArray(32).also(random::nextBytes))

    /** The S256 `code_challenge` (RFC 7636 §4.2): `base64url(SHA-256(ASCII(codeVerifier)))`. */
    public fun codeChallengeS256(codeVerifier: String): String =
        b64Url.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII)),
        )
}
