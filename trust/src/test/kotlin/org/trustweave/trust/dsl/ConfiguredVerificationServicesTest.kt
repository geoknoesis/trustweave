package org.trustweave.trust.dsl

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.results.getOrThrow
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.registerSchema
import org.trustweave.trust.dsl.credential.schema
import org.trustweave.trust.types.getOrThrowDid
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConfiguredVerificationServicesTest {
    @Test
    fun `default signer shares configured verification services`() = runBlocking { verifyConfiguration(false) }

    @Test
    fun `custom signer shares configured verification services`() = runBlocking { verifyConfiguration(true) }

    private suspend fun verifyConfiguration(customSigner: Boolean) {
        val kms = InMemoryKeyManagementService()
        val sdk =
            TrustWeave.build {
                keys {
                    custom(kms)
                    if (customSigner) {
                        signer { data, id ->
                            val signed = kms.sign(KeyId(id), data)
                            check(signed is SignResult.Success)
                            signed.signature
                        }
                    }
                }
                did { method("key") { algorithm("Ed25519") } }
                revocation { provider("inMemory") }
            }
        try {
            val issuer = sdk.createDid().getOrThrowDid()
            val schemaId = "https://example.org/schema/configuration"
            sdk.registerSchema {
                id(schemaId)
                jsonSchema {
                    "type" to "object"
                    "properties" { "course" { "type" to "string" } }
                }
            }
            val credential =
                sdk
                    .issue {
                        withTestClaimContexts()
                        credential {
                            type("TrainingCredential")
                            issuer(issuer)
                            subject(issuer.value) { "course" to "training" }
                        }
                        signedBy(issuer)
                        withRevocation()
                    }.getOrThrow()
            assertTrue(sdk.schema(schemaId).validate(credential).valid)
            val malformed =
                credential.copy(
                    credentialSubject =
                        credential.credentialSubject.copy(
                            claims = mapOf("course" to JsonPrimitive(123)),
                        ),
                )
            assertFalse(sdk.schema(schemaId).validate(malformed).valid)
            assertIs<VerificationResult.Valid>(
                sdk.verify {
                    credential(credential)
                    validateSchema(schemaId)
                },
            )
            val list = checkNotNull(credential.credentialStatus?.statusListCredential)
            assertTrue(
                sdk.revoke {
                    credential(checkNotNull(credential.id).value)
                    statusList(list.value)
                },
            )
            assertFalse(sdk.verify(credential) is VerificationResult.Valid)
        } finally {
            sdk.close()
        }
    }
}
