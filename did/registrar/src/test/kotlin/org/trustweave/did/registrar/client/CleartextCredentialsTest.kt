package org.trustweave.did.registrar.client

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.did.registrar.method.HttpDidMethod
import org.trustweave.did.registration.model.DidRegistrationSpec
import org.trustweave.did.registration.model.DriverConfig
import kotlin.test.assertTrue

class CleartextCredentialsTest {
    @Test
    fun `registrar refuses an API key over cleartext http to a public host`() {
        val e =
            assertThrows<IllegalArgumentException> {
                DefaultUniversalRegistrar(baseUrl = "http://registrar.example.org", apiKey = "k")
            }
        assertTrue(e.message!!.contains("https"), e.message)
        DefaultUniversalRegistrar(baseUrl = "https://registrar.example.org", apiKey = "k")
        DefaultUniversalRegistrar(baseUrl = "http://localhost:9000", apiKey = "k")
        DefaultUniversalRegistrar(baseUrl = "http://registrar.example.org")
    }

    @Test
    fun `HttpDidMethod refuses http base URLs when the driver carries an API key`() {
        fun spec(
            resolver: String,
            registrar: String? = null,
        ) = DidRegistrationSpec(
            name = "ex",
            driver =
                DriverConfig(
                    type = "universal-resolver",
                    baseUrl = resolver,
                    registrarUrl = registrar,
                    apiKey = "k",
                ),
        )
        assertThrows<IllegalArgumentException> { HttpDidMethod(spec("http://resolver.example.org")) }
        assertThrows<IllegalArgumentException> { HttpDidMethod(spec("https://resolver.example.org", "http://registrar.example.org")) }
        HttpDidMethod(spec("http://localhost:8080"))
    }
}
