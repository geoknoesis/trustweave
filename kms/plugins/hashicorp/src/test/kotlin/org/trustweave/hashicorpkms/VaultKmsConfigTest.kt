package org.trustweave.hashicorpkms

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class VaultKmsConfigTest {

    @Test
    fun `test builder creates valid config`() {
        val config = VaultKmsConfig.builder()
            .address("http://localhost:8200")
            .token("hvs.xxx")
            .transitPath("transit")
            .build()

        assertEquals("http://localhost:8200", config.address)
        assertEquals("hvs.xxx", config.token)
        assertEquals("transit", config.transitPath)
    }

    @Test
    fun `test builder with AppRole authentication`() {
        val config = VaultKmsConfig.builder()
            .address("http://localhost:8200")
            .appRolePath("approle")
            .roleId("role-id")
            .secretId("secret-id")
            .build()

        assertEquals("http://localhost:8200", config.address)
        assertEquals("approle", config.appRolePath)
        assertEquals("role-id", config.roleId)
        assertEquals("secret-id", config.secretId)
    }

    @Test
    fun `test builder without address throws exception`() {
        assertThrows<IllegalArgumentException> {
            VaultKmsConfig.builder()
                .token("hvs.xxx")
                .build()
        }
    }

    @Test
    fun `test from map creates valid config`() {
        val config = VaultKmsConfig.fromMap(mapOf(
            "address" to "http://vault.example.com:8200",
            "token" to "hvs.xxx",
            "transitPath" to "transit",
            "namespace" to "admin"
        ))

        assertEquals("http://vault.example.com:8200", config.address)
        assertEquals("hvs.xxx", config.token)
        assertEquals("transit", config.transitPath)
        assertEquals("admin", config.namespace)
    }

    @Test
    fun `test from map without address throws exception`() {
        assertThrows<IllegalArgumentException> {
            VaultKmsConfig.fromMap(emptyMap())
        }
    }

    @Test
    fun `test from map uses default transit path`() {
        val config = VaultKmsConfig.fromMap(mapOf(
            "address" to "http://localhost:8200"
        ))

        assertEquals("transit", config.transitPath)
    }

    @Test
    fun `test config with empty address throws exception`() {
        assertThrows<IllegalArgumentException> {
            VaultKmsConfig.builder()
                .address("")
                .build()
        }
    }
}

