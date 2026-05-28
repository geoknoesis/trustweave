package org.trustweave.credential.federation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustChainResolverTest {

    private val resolver = TrustChainResolver()

    // -------------------------------------------------------------------------
    // parseEntityStatement
    // -------------------------------------------------------------------------

    @Test
    fun `parseEntityStatement returns null for malformed input`() {
        assertNull(resolver.parseEntityStatement("not.a.jwt"))
        assertNull(resolver.parseEntityStatement(""))
        assertNull(resolver.parseEntityStatement("x"))
    }

    @Test
    fun `parseEntityStatement parses a known entity statement JWT payload`() {
        // A compact-serialized JWT with an unverified payload containing
        // the minimum required EntityStatement fields.
        // Header: {"alg":"none"} (base64url of '{"alg":"none"}')
        // Payload: JSON with iss, sub, iat, exp, jwks
        // Signature: empty (unsigned token — parsed but not verified here)
        val header = "eyJhbGciOiJub25lIn0"  // {"alg":"none"}
        val payloadJson = """
            {
              "iss": "https://leaf.example.com",
              "sub": "https://leaf.example.com",
              "iat": 1700000000,
              "exp": 9999999999,
              "jwks": {
                "keys": [
                  {
                    "kty": "EC",
                    "crv": "P-256",
                    "x": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    "y": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    "kid": "leaf-key-1"
                  }
                ]
              },
              "authority_hints": ["https://anchor.example.com"]
            }
        """.trimIndent()
        val encodedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.toByteArray())
        val jwt = "$header.$encodedPayload."

        val statement = resolver.parseEntityStatement(jwt)

        assertNotNull(statement)
        assertEquals("https://leaf.example.com", statement.iss)
        assertEquals("https://leaf.example.com", statement.sub)
        assertEquals(1700000000L, statement.iat)
        assertEquals(9999999999L, statement.exp)
        assertEquals(1, statement.jwks.keys.size)
        assertEquals("leaf-key-1", statement.jwks.keys.first().kid)
        assertEquals(listOf("https://anchor.example.com"), statement.authorityHints)
    }

    @Test
    fun `parseEntityStatement handles optional fields absent`() {
        val header = "eyJhbGciOiJub25lIn0"
        val payloadJson = """
            {
              "iss": "https://anchor.example.com",
              "sub": "https://leaf.example.com",
              "iat": 1700000000,
              "exp": 9999999999,
              "jwks": { "keys": [] }
            }
        """.trimIndent()
        val encodedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.toByteArray())
        val jwt = "$header.$encodedPayload."

        val statement = resolver.parseEntityStatement(jwt)

        assertNotNull(statement)
        assertNull(statement.authorityHints)
        assertNull(statement.metadata)
        assertNull(statement.constraints)
        assertNull(statement.trustMarks)
        assertNull(statement.metadataPolicy)
    }

    // -------------------------------------------------------------------------
    // verifyChain
    // -------------------------------------------------------------------------

    @Test
    fun `verifyChain returns false for empty chain`() {
        val chain = TrustChain(
            statements = emptyList(),
            trustAnchorId = "https://anchor.example.com",
            leafEntityId = "https://leaf.example.com",
        )
        assertFalse(resolver.verifyChain(chain))
    }

    @Test
    fun `verifyChain returns false when a statement is expired`() {
        val header = "eyJhbGciOiJub25lIn0"

        // Leaf statement — expired
        val expiredLeafPayload = """
            {
              "iss": "https://leaf.example.com",
              "sub": "https://leaf.example.com",
              "iat": 1000000000,
              "exp": 1000000001,
              "jwks": { "keys": [] }
            }
        """.trimIndent()
        val encodedExpiredLeaf = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(expiredLeafPayload.toByteArray())
        val expiredLeafJwt = "$header.$encodedExpiredLeaf."

        // Anchor statement
        val anchorPayload = """
            {
              "iss": "https://anchor.example.com",
              "sub": "https://leaf.example.com",
              "iat": 1000000000,
              "exp": 9999999999,
              "jwks": { "keys": [] }
            }
        """.trimIndent()
        val encodedAnchor = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(anchorPayload.toByteArray())
        val anchorJwt = "$header.$encodedAnchor."

        val chain = TrustChain(
            statements = listOf(expiredLeafJwt, anchorJwt),
            trustAnchorId = "https://anchor.example.com",
            leafEntityId = "https://leaf.example.com",
        )

        assertFalse(resolver.verifyChain(chain))
    }

    @Test
    fun `verifyChain returns false when chain contains unparseable statements`() {
        val chain = TrustChain(
            statements = listOf("valid.looking.jwt", "also.invalid.jwt"),
            trustAnchorId = "https://anchor.example.com",
            leafEntityId = "https://leaf.example.com",
        )
        assertFalse(resolver.verifyChain(chain))
    }

    // -------------------------------------------------------------------------
    // EntityStatement JSON serialization round-trip
    // -------------------------------------------------------------------------

    @Test
    fun `EntityStatement serializes and deserializes with correct snake_case field names`() {
        val statement = EntityStatement(
            iss = "https://anchor.example.com",
            sub = "https://leaf.example.com",
            iat = 1700000000L,
            exp = 1800000000L,
            jwks = FederationJwkSet(
                keys = listOf(
                    FederationJwk(kty = "EC", crv = "P-256", kid = "key-1", x = "abc", y = "def"),
                ),
            ),
            authorityHints = listOf("https://anchor.example.com"),
            constraints = PolicyConstraints(maxPathLength = 2),
            trustMarks = listOf(TrustMark(id = "https://tm.example.com", trustMark = "signed.tm.jwt")),
        )

        val serialized = Json { encodeDefaults = false }.encodeToString(
            EntityStatement.serializer(),
            statement,
        )
        val deserialized = Json { ignoreUnknownKeys = true }.decodeFromString(
            EntityStatement.serializer(),
            serialized,
        )

        assertEquals(statement, deserialized)
        assertTrue(serialized.contains("\"authority_hints\""))
        assertTrue(serialized.contains("\"max_path_length\""))
        assertTrue(serialized.contains("\"trust_marks\""))
        assertTrue(serialized.contains("\"trust_mark\""))
    }

    @Test
    fun `FederationJwkSet serializes all key fields`() {
        val jwkSet = FederationJwkSet(
            keys = listOf(
                FederationJwk(kty = "EC", use = "sig", kid = "k1", crv = "P-256", x = "x1", y = "y1", alg = "ES256"),
                FederationJwk(kty = "RSA", n = "modulus", e = "AQAB", kid = "k2"),
            ),
        )
        val json = Json { encodeDefaults = false }
        val serialized = json.encodeToString(FederationJwkSet.serializer(), jwkSet)
        val deserialized = json.decodeFromString(FederationJwkSet.serializer(), serialized)

        assertEquals(2, deserialized.keys.size)
        assertEquals("k1", deserialized.keys[0].kid)
        assertEquals("k2", deserialized.keys[1].kid)
    }

    @Test
    fun `EntityConfigurationEndpoint getUrl trims trailing slashes`() {
        assertEquals(
            "https://example.com/.well-known/openid-federation",
            EntityConfigurationEndpoint.getUrl("https://example.com"),
        )
        assertEquals(
            "https://example.com/.well-known/openid-federation",
            EntityConfigurationEndpoint.getUrl("https://example.com/"),
        )
        assertEquals(
            "https://example.com/.well-known/openid-federation",
            EntityConfigurationEndpoint.getUrl("https://example.com///"),
        )
    }

    @Test
    fun `TrustChainResolutionResult sealed classes are distinct`() {
        val chain = TrustChain(
            statements = listOf("a.b.c"),
            trustAnchorId = "https://anchor.example.com",
            leafEntityId = "https://leaf.example.com",
        )
        val success: TrustChainResolutionResult = TrustChainResolutionResult.Success(chain, 1700000000L)
        val failure: TrustChainResolutionResult = TrustChainResolutionResult.Failure("reason", "https://leaf.example.com")

        assertTrue(success is TrustChainResolutionResult.Success)
        assertTrue(failure is TrustChainResolutionResult.Failure)
        assertEquals("reason", (failure as TrustChainResolutionResult.Failure).reason)
        assertEquals(1700000000L, (success as TrustChainResolutionResult.Success).verifiedAt)
    }
}
