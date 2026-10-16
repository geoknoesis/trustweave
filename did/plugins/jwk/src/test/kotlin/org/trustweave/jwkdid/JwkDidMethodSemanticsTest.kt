package org.trustweave.jwkdid

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JwkDidMethodSemanticsTest {
    private val method = JwkDidMethod(InMemoryKeyManagementService())

    private fun didFor(jwkJson: String) = Did("did:jwk:" + Base64.getUrlEncoder().withoutPadding().encodeToString(jwkJson.toByteArray()))

    private val p256 =
        """"kty":"EC","crv":"P-256","x":"f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU","y":"x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0""""

    private suspend fun resolved(extra: String): DidResolutionResult =
        JwkDidMethod(InMemoryKeyManagementService()).resolveDid(didFor("{$p256$extra}"))

    @Test
    fun `use enc yields key agreement only`() =
        runTest {
            val doc = assertIs<DidResolutionResult.Success>(resolved(""","use":"enc"""")).document
            assertTrue(doc.authentication.isEmpty())
            assertTrue(doc.assertionMethod.isEmpty())
            assertEquals(1, doc.keyAgreement.size)
        }

    @Test
    fun `use sig yields no key agreement`() =
        runTest {
            val doc = assertIs<DidResolutionResult.Success>(resolved(""","use":"sig"""")).document
            assertTrue(doc.keyAgreement.isEmpty())
            assertEquals(1, doc.authentication.size)
        }

    @Test
    fun `key_ops decides the relationships when use is absent`() =
        runTest {
            val agree = assertIs<DidResolutionResult.Success>(resolved(""","key_ops":["deriveKey"]""")).document
            assertTrue(agree.authentication.isEmpty())
            assertEquals(1, agree.keyAgreement.size)
            val sign = assertIs<DidResolutionResult.Success>(resolved(""","key_ops":["verify"]""")).document
            assertTrue(sign.keyAgreement.isEmpty())
            assertEquals(1, sign.authentication.size)
        }

    @Test
    fun `use and key_ops that contradict each other are refused`() =
        runTest {
            assertIs<DidResolutionResult.Failure>(resolved(""","use":"sig","key_ops":["encrypt"]"""))
        }

    @Test
    fun `an EC key that is not on its curve is refused`() =
        runTest {
            val x = "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU"
            val offCurve = """{"kty":"EC","crv":"P-256","x":"$x","y":"y_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0"}"""
            assertIs<DidResolutionResult.Failure>(method.resolveDid(didFor(offCurve)))
        }

    @Test
    fun `an EC key with a wrong-length coordinate is refused`() =
        runTest {
            assertIs<DidResolutionResult.Failure>(method.resolveDid(didFor("""{"kty":"EC","crv":"P-256","x":"AQ","y":"AQ"}""")))
        }

    @Test
    fun `an Ed25519 key that is not a curve point is refused`() =
        runTest {
            val bad = """{"kty":"OKP","crv":"Ed25519","x":"AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}"""
            assertIs<DidResolutionResult.Failure>(method.resolveDid(didFor(bad)))
        }

    @Test
    fun `a self-certifying DID cannot have its keys substituted by an update`() =
        runTest {
            val m = JwkDidMethod(InMemoryKeyManagementService())
            val first = m.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            val second = m.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            assertThrows<Exception> {
                m.updateDid(first.id) { it.copy(verificationMethod = second.verificationMethod) }
            }
            assertIs<DidResolutionResult.Success>(m.resolveDid(first.id))
            assertEquals(
                first.verificationMethod,
                (m.resolveDid(first.id) as DidResolutionResult.Success).document.verificationMethod,
            )
        }

    @Test
    fun `an update may not change the document id`() =
        runTest {
            val m = JwkDidMethod(InMemoryKeyManagementService())
            val first = m.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            val second = m.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            assertThrows<Exception> { m.updateDid(first.id) { second } }
        }
}
