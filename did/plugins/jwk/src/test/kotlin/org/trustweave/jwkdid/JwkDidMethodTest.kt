package org.trustweave.jwkdid

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.KeyPurpose
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.inmemory.InMemoryKeyManagementService
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwkDidMethodTest {
    private val kms = InMemoryKeyManagementService()
    private val method = JwkDidMethod(kms)

    @Test
    fun `createDid produces did-jwk identifier`() =
        runTest {
            val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            assertTrue(doc.id.value.startsWith("did:jwk:"))
        }

    @Test
    fun `created DID has exactly one verification method`() =
        runTest {
            val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            assertEquals(1, doc.verificationMethod.size)
            assertTrue(
                doc.verificationMethod[0]
                    .id.value
                    .endsWith("#0"),
            )
        }

    @Test
    fun `verification method contains JWK`() =
        runTest {
            val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            val vm = doc.verificationMethod[0]
            assertNotNull(vm.publicKeyJwk)
            assertEquals("OKP", vm.publicKeyJwk!!["kty"])
            assertEquals("Ed25519", vm.publicKeyJwk!!["crv"])
        }

    @Test
    fun `resolveDid returns Success for valid did-jwk`() =
        runTest {
            val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            val result = method.resolveDid(doc.id)
            assertIs<DidResolutionResult.Success>(result)
            assertEquals(doc.id.value, result.document.id.value)
        }

    @Test
    fun `resolveDid reconstructs document from identifier without stored state`() =
        runTest {
            val kms2 = InMemoryKeyManagementService()
            val method2 = JwkDidMethod(kms2)
            val doc = method.createDid(DidCreationOptions(algorithm = KeyAlgorithm.ED25519))
            val result = method2.resolveDid(doc.id)
            assertIs<DidResolutionResult.Success>(result)
            assertEquals(1, result.document.verificationMethod.size)
            assertNotNull(result.document.verificationMethod[0].publicKeyJwk)
        }

    @Test
    fun `createDid with assertion purpose includes assertionMethod`() =
        runTest {
            val doc =
                method.createDid(
                    DidCreationOptions(
                        algorithm = KeyAlgorithm.ED25519,
                        purposes = listOf(KeyPurpose.AUTHENTICATION, KeyPurpose.ASSERTION),
                    ),
                )
            assertNotNull(doc.assertionMethod)
            assertTrue(doc.assertionMethod!!.isNotEmpty())
        }

    @Test
    fun `resolveDid returns error for invalid did`() =
        runTest {
            // A syntactically valid DID but with a method-specific-id that is not a valid base64url JWK.
            val result =
                method.resolveDid(
                    org.trustweave.did.identifiers
                        .Did("did:jwk:notavalidjwk"),
                )
            assertIs<DidResolutionResult.Failure>(result)
        }

    private fun didFor(jwkJson: String) =
        org.trustweave.did.identifiers.Did(
            "did:jwk:" +
                java.util.Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(jwkJson.toByteArray()),
        )

    @Test
    fun `a JWK with private members is refused rather than published in a document`() =
        runTest {
            val result =
                JwkDidMethod(InMemoryKeyManagementService()).resolveDid(
                    didFor(
                        """{"kty":"OKP","crv":"Ed25519","x":"11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo","d":"nWGxne_9WmC6hEr0kuwsxERJxWl7MmkZcDusAxyuf2A"}""",
                    ),
                )
            assertTrue(result is DidResolutionResult.Failure, "got $result")
            assertTrue(result.toString().contains("private"), result.toString())
        }

    @Test
    fun `an unknown key type is an error, not silently Ed25519`() =
        runTest {
            val result = JwkDidMethod(InMemoryKeyManagementService()).resolveDid(didFor("""{"kty":"oct","alg":"HS256"}"""))
            assertTrue(result is DidResolutionResult.Failure, "got $result")
            val missingKty = JwkDidMethod(InMemoryKeyManagementService()).resolveDid(didFor("""{"crv":"Ed25519","x":"AA"}"""))
            assertTrue(missingKty is DidResolutionResult.Failure, "got $missingKty")
        }

    @Test
    fun `malformed base64url and non-object JSON are invalid DIDs`() =
        runTest {
            val m = JwkDidMethod(InMemoryKeyManagementService())
            assertTrue(
                m.resolveDid(
                    org.trustweave.did.identifiers
                        .Did("did:jwk:a"),
                ) is DidResolutionResult.Failure,
            )
            assertTrue(m.resolveDid(didFor("[1,2,3]")) is DidResolutionResult.Failure)
        }

    @Test
    fun `a public EC P-256 JWK resolves to one verification method with that key`() =
        runTest {
            val result =
                JwkDidMethod(InMemoryKeyManagementService()).resolveDid(
                    didFor(
                        """{"kty":"EC","crv":"P-256","x":"f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU","y":"x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0"}""",
                    ),
                )
            assertIs<DidResolutionResult.Success>(result)
            assertEquals(
                "P-256",
                result.document.verificationMethod
                    .single()
                    .publicKeyJwk!!["crv"],
            )
        }
}
