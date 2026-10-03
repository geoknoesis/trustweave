package org.trustweave.did.registrar.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.did.registration.model.DriverConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretRedactionTest {
    private val jwk = JsonObject(mapOf("kty" to JsonPrimitive("OKP"), "d" to JsonPrimitive("SUPER-SECRET-D")))

    @Test
    fun `KeyMaterial toString never prints private key material`() {
        val text =
            KeyMaterial(
                id = "key-1",
                type = "Ed25519",
                privateKeyJwk = jwk,
                privateKeyMultibase = "zSECRETMULTIBASE",
                additionalProperties = mapOf("pw" to "HUNTER2"),
            ).toString()
        assertFalse("SUPER-SECRET-D" in text, text)
        assertFalse("zSECRETMULTIBASE" in text, text)
        assertFalse("HUNTER2" in text, text)
        assertTrue("key-1" in text && "Ed25519" in text, text)
    }

    @Test
    fun `Secret toString and options containing it never print secrets`() {
        val secret =
            Secret(
                keys = listOf(KeyMaterial(privateKeyMultibase = "zSECRETMULTIBASE")),
                recoveryKey = "RECOVERY-SECRET",
                updateKey = "UPDATE-SECRET",
                methodSpecificSecrets = mapOf("k" to "METHOD-SECRET"),
            )
        listOf(
            secret.toString(),
            UpdateDidOptions(secret = secret).toString(),
            DeactivateDidOptions(secret = secret).toString(),
        ).forEach { text ->
            listOf("zSECRETMULTIBASE", "RECOVERY-SECRET", "UPDATE-SECRET", "METHOD-SECRET").forEach {
                assertFalse(it in text, "$it leaked in $text")
            }
        }
    }

    @Test
    fun `DriverConfig toString redacts the api key and config values`() {
        val text = DriverConfig(type = "universal-resolver", apiKey = "API-KEY-123", config = mapOf("token" to "TOK-456")).toString()
        assertFalse("API-KEY-123" in text, text)
        assertFalse("TOK-456" in text, text)
        assertTrue("universal-resolver" in text, text)
    }
}
