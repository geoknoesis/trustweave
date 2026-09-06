package org.trustweave.credential.federation

import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustChainSignatureTest {
    private val processor = EntityStatementJwtProcessor()
    private val leaf = ECKeyGenerator(Curve.P_256).keyID("leaf").generate()
    private val parent = ECKeyGenerator(Curve.P_256).keyID("parent").generate()
    private val anchor = ECKeyGenerator(Curve.P_256).keyID("anchor").generate()
    private val now = System.currentTimeMillis() / 1000

    private fun keys(key: ECKey) =
        FederationJwkSet(
            listOf(
                FederationJwk(
                    kty = "EC",
                    crv = "P-256",
                    x = key.x.toString(),
                    y = key.y.toString(),
                    kid = key.keyID,
                ),
            ),
        )

    private fun statement(
        issuer: String,
        subject: String,
        key: ECKey,
    ) = EntityStatement(issuer, subject, now - 10, now + 600, keys(key))

    private fun sign(
        statement: EntityStatement,
        key: ECKey,
    ) = processor.sign(statement, key.toJSONString())

    private fun chain() =
        TrustChain(
            listOf(
                sign(statement("https://leaf", "https://leaf", leaf), leaf),
                sign(statement("https://parent", "https://leaf", leaf), parent),
                sign(statement("https://anchor", "https://parent", parent), anchor),
                sign(statement("https://anchor", "https://anchor", anchor), anchor),
            ),
            "https://anchor",
            "https://leaf",
        )

    private fun resolver() = TrustChainResolver(trustedAnchorKeys = mapOf("https://anchor" to keys(anchor)))

    @Test
    fun `HTTP resolution assembles and verifies a genuine three entity chain`() =
        kotlinx.coroutines.runBlocking<Unit> {
            okhttp3.mockwebserver.MockWebServer().use { server ->
                server.start()
                val leafId = server.url("/leaf").toString()
                val parentId = server.url("/parent").toString()
                val anchorId = server.url("/anchor").toString()
                val leafConfig = statement(leafId, leafId, leaf).copy(authorityHints = listOf(parentId))
                val parentConfig =
                    statement(parentId, parentId, parent).copy(
                        authorityHints = listOf(anchorId),
                        metadata =
                            EntityMetadata(
                                federationEntity =
                                    FederationEntityMetadata(
                                        federationFetchEndpoint = server.url("/parent-fetch").toString(),
                                    ),
                            ),
                    )
                val anchorConfig =
                    statement(anchorId, anchorId, anchor).copy(
                        metadata =
                            EntityMetadata(
                                federationEntity =
                                    FederationEntityMetadata(
                                        federationFetchEndpoint = server.url("/anchor-fetch").toString(),
                                    ),
                            ),
                    )
                val responses =
                    mapOf(
                        "/leaf/.well-known/openid-federation" to sign(leafConfig, leaf),
                        "/parent/.well-known/openid-federation" to sign(parentConfig, parent),
                        "/anchor/.well-known/openid-federation" to sign(anchorConfig, anchor),
                        "/parent-fetch" to sign(statement(parentId, leafId, leaf), parent),
                        "/anchor-fetch" to sign(statement(anchorId, parentId, parent), anchor),
                    )
                server.dispatcher =
                    object : okhttp3.mockwebserver.Dispatcher() {
                        override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse {
                            val body = responses[request.requestUrl?.encodedPath]
                            return if (body == null) {
                                okhttp3.mockwebserver.MockResponse().setResponseCode(404)
                            } else {
                                okhttp3.mockwebserver.MockResponse().setBody(body)
                            }
                        }
                    }
                // A plain client is confined to this loopback test server.
                val resolver =
                    TrustChainResolver(
                        httpClient = okhttp3.OkHttpClient(),
                        trustedAnchorKeys = mapOf(anchorId to keys(anchor)),
                    )
                val result = resolver.resolve(leafId, setOf(anchorId))
                assertTrue(result is TrustChainResolutionResult.Success)
                kotlin.test.assertEquals(leafId, result.chain.leafEntityId)
                kotlin.test.assertEquals(4, result.chain.statements.size)
                assertTrue(resolver.verifyChain(result.chain))
            }
        }

    @Test
    fun `genuine chain verifies from endorsed leaf keys to pinned anchor`() {
        assertTrue(resolver().verifyChain(chain()))
    }

    @Test
    fun `unsigned and wrong-key leaf configurations fail`() {
        val chain = chain()
        val unsigned = PlainJWT(SignedJWT.parse(chain.statements.first()).jwtClaimsSet).serialize()
        assertFalse(resolver().verifyChain(chain.copy(statements = listOf(unsigned) + chain.statements.drop(1))))
        val forged = sign(statement("https://leaf", "https://leaf", leaf), anchor)
        assertFalse(resolver().verifyChain(chain.copy(statements = listOf(forged) + chain.statements.drop(1))))
    }

    @Test
    fun `presented anchor keys cannot replace configured trust`() {
        assertFalse(TrustChainResolver().verifyChain(chain()))
        val wrong = TrustChainResolver(trustedAnchorKeys = mapOf("https://anchor" to keys(leaf)))
        assertFalse(wrong.verifyChain(chain()))
        assertFalse(resolver().verifyChain(chain().copy(trustAnchorId = "https://other")))
    }

    @Test
    fun `issuer links and signed temporal claims are enforced`() {
        val chain = chain()
        val wrongLink = chain.statements.toMutableList()
        wrongLink[2] = sign(statement("https://anchor", "https://unrelated", parent), anchor)
        assertFalse(resolver().verifyChain(chain.copy(statements = wrongLink)))
        for (claims in listOf(
            statement("https://leaf", "https://leaf", leaf).copy(iat = now - 200, exp = now - 100),
            statement("https://leaf", "https://leaf", leaf).copy(iat = now + 100),
        )) {
            assertFalse(resolver().verifyChain(chain.copy(statements = listOf(sign(claims, leaf)) + chain.statements.drop(1))))
        }
    }
}
