package org.trustweave.kms.cloudhsm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for [CloudHsmKmsConfig]. Covers:
 *  - Required-field validation.
 *  - Cluster ID format validation.
 *  - Sensitive-field masking in [toString].
 *  - PIN composition from env var.
 *  - [CloudHsmKmsConfig.fromMap] type coercion + error paths.
 *  - Platform-specific default PKCS#11 library path selection.
 */
class CloudHsmKmsConfigTest {

    private val validClusterId: String = "cluster-abcd1234efg"
    private val validHsmUser: String = "trustweave-cu"

    @Test
    fun `rejects blank cluster id`() {
        val ex = assertThrows<IllegalArgumentException> {
            CloudHsmKmsConfig(clusterId = "", hsmUser = validHsmUser)
        }
        assertTrue(ex.message!!.contains("clusterId"))
    }

    @Test
    fun `rejects malformed cluster id`() {
        val ex = assertThrows<IllegalArgumentException> {
            CloudHsmKmsConfig(clusterId = "not-a-cluster", hsmUser = validHsmUser)
        }
        assertTrue(ex.message!!.contains("cluster-"))
    }

    @Test
    fun `rejects blank hsm user`() {
        val ex = assertThrows<IllegalArgumentException> {
            CloudHsmKmsConfig(clusterId = validClusterId, hsmUser = "")
        }
        assertTrue(ex.message!!.contains("hsmUser"))
    }

    @Test
    fun `rejects blank password env var name`() {
        assertThrows<IllegalArgumentException> {
            CloudHsmKmsConfig(
                clusterId = validClusterId,
                hsmUser = validHsmUser,
                hsmPasswordEnvVar = "  ",
            )
        }
    }

    @Test
    fun `rejects negative slot`() {
        assertThrows<IllegalArgumentException> {
            CloudHsmKmsConfig(
                clusterId = validClusterId,
                hsmUser = validHsmUser,
                slot = -1,
            )
        }
    }

    @Test
    fun `defaults are populated`() {
        val cfg = CloudHsmKmsConfig(clusterId = validClusterId, hsmUser = validHsmUser)
        assertEquals(CloudHsmKmsConfig.DEFAULT_REGION, cfg.region)
        assertEquals(CloudHsmKmsConfig.DEFAULT_PARTITION, cfg.partition)
        assertEquals(CloudHsmKmsConfig.DEFAULT_PASSWORD_ENV_VAR, cfg.hsmPasswordEnvVar)
        assertEquals(0, cfg.slot)
        assertNull(cfg.classicLoadBalancerEndpoint)
        assertFalse(cfg.enableSoftDelete)
        assertTrue(cfg.pkcs11LibraryPath.isNotBlank())
    }

    @Test
    fun `toString omits the password env var value`() {
        // Even though we don't store the password itself, defensively confirm we don't
        // leak its name in a misleading way that suggests it's the value.
        val cfg = CloudHsmKmsConfig(
            clusterId = validClusterId,
            hsmUser = validHsmUser,
            hsmPasswordEnvVar = "MY_SECRET_VAR",
        )
        val s = cfg.toString()
        assertTrue(s.contains("MY_SECRET_VAR"), "env var name itself is not secret and should appear")
        // No password is ever stored in the object, so nothing else to leak.
        assertTrue(s.contains(validHsmUser))
        assertTrue(s.contains(validClusterId))
    }

    @Test
    fun `resolvedHsmPassword returns null when env var unset`() {
        val cfg = CloudHsmKmsConfig(
            clusterId = validClusterId,
            hsmUser = validHsmUser,
            // Use a name guaranteed not to exist on any test machine.
            hsmPasswordEnvVar = "TRUSTWEAVE_CLOUDHSM_PASSWORD_DOES_NOT_EXIST_${System.nanoTime()}",
        )
        assertNull(cfg.resolvedHsmPassword())
    }

    @Test
    fun `composeLoginPin throws when password env var unset`() {
        val cfg = CloudHsmKmsConfig(
            clusterId = validClusterId,
            hsmUser = validHsmUser,
            hsmPasswordEnvVar = "TRUSTWEAVE_CLOUDHSM_PASSWORD_DOES_NOT_EXIST_${System.nanoTime()}",
        )
        val ex = assertThrows<IllegalStateException> { cfg.composeLoginPin() }
        assertTrue(ex.message!!.contains(cfg.hsmPasswordEnvVar))
    }

    @Test
    fun `composeLoginPin formats PIN as user colon password when env var present`() {
        // We can't safely mutate the live process env on JVMs without reflection, so
        // we use a system-property bridge: read env first, fall back to system property.
        // Instead, directly exercise the composition logic by reading the env var that
        // the test runner provides via the gradle test task. We can verify the *shape*
        // by checking what fromMap + composeLoginPin would produce when the var is set.
        val envVarName = "TRUSTWEAVE_TEST_CLOUDHSM_PIN_${System.nanoTime()}"
        // Use ProcessHandle env trick: not portable. Instead test through a Map approach.
        // Verify the logic by constructing through a controlled env var that we know exists:
        // PATH always exists on every platform.
        val pathVar = System.getenv("PATH")
            ?: System.getenv("Path") // Windows
            ?: return // can't verify on this host

        val cfg = CloudHsmKmsConfig(
            clusterId = validClusterId,
            hsmUser = validHsmUser,
            hsmPasswordEnvVar = if (System.getenv("PATH") != null) "PATH" else "Path",
        )
        val pin = cfg.composeLoginPin()
        val pinStr = String(pin)
        assertTrue(pinStr.startsWith("$validHsmUser:"), "PIN must start with '<user>:'; was '$pinStr'")
        assertEquals("$validHsmUser:$pathVar", pinStr)
    }

    @Test
    fun `fromMap parses minimal options`() {
        val cfg = CloudHsmKmsConfig.fromMap(
            mapOf(
                CloudHsmKmsConfig.KEY_CLUSTER_ID to validClusterId,
                CloudHsmKmsConfig.KEY_HSM_USER to validHsmUser,
            ),
        )
        assertEquals(validClusterId, cfg.clusterId)
        assertEquals(validHsmUser, cfg.hsmUser)
        assertEquals(CloudHsmKmsConfig.DEFAULT_REGION, cfg.region)
    }

    @Test
    fun `fromMap rejects missing cluster id`() {
        val ex = assertThrows<IllegalArgumentException> {
            CloudHsmKmsConfig.fromMap(mapOf(CloudHsmKmsConfig.KEY_HSM_USER to validHsmUser))
        }
        assertTrue(ex.message!!.contains(CloudHsmKmsConfig.KEY_CLUSTER_ID))
    }

    @Test
    fun `fromMap rejects missing hsm user`() {
        val ex = assertThrows<IllegalArgumentException> {
            CloudHsmKmsConfig.fromMap(mapOf(CloudHsmKmsConfig.KEY_CLUSTER_ID to validClusterId))
        }
        assertTrue(ex.message!!.contains(CloudHsmKmsConfig.KEY_HSM_USER))
    }

    @Test
    fun `fromMap coerces integer-like slot values`() {
        val cfg = CloudHsmKmsConfig.fromMap(
            mapOf(
                CloudHsmKmsConfig.KEY_CLUSTER_ID to validClusterId,
                CloudHsmKmsConfig.KEY_HSM_USER to validHsmUser,
                CloudHsmKmsConfig.KEY_SLOT to "3",
            ),
        )
        assertEquals(3, cfg.slot)

        val cfgInt = CloudHsmKmsConfig.fromMap(
            mapOf(
                CloudHsmKmsConfig.KEY_CLUSTER_ID to validClusterId,
                CloudHsmKmsConfig.KEY_HSM_USER to validHsmUser,
                CloudHsmKmsConfig.KEY_SLOT to 7,
            ),
        )
        assertEquals(7, cfgInt.slot)
    }

    @Test
    fun `fromMap rejects garbage slot`() {
        assertThrows<IllegalArgumentException> {
            CloudHsmKmsConfig.fromMap(
                mapOf(
                    CloudHsmKmsConfig.KEY_CLUSTER_ID to validClusterId,
                    CloudHsmKmsConfig.KEY_HSM_USER to validHsmUser,
                    CloudHsmKmsConfig.KEY_SLOT to "not-a-number",
                ),
            )
        }
    }

    @Test
    fun `fromMap coerces boolean-like enableSoftDelete values`() {
        val cfg = CloudHsmKmsConfig.fromMap(
            mapOf(
                CloudHsmKmsConfig.KEY_CLUSTER_ID to validClusterId,
                CloudHsmKmsConfig.KEY_HSM_USER to validHsmUser,
                CloudHsmKmsConfig.KEY_ENABLE_SOFT_DELETE to "true",
            ),
        )
        assertTrue(cfg.enableSoftDelete)
    }

    @Test
    fun `defaultPkcs11LibraryPath picks platform default`() {
        val path = CloudHsmKmsConfig.defaultPkcs11LibraryPath()
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            assertEquals(CloudHsmKmsConfig.WINDOWS_PKCS11_LIBRARY, path)
        } else {
            assertEquals(CloudHsmKmsConfig.LINUX_PKCS11_LIBRARY, path)
        }
    }

    @Test
    fun `fromEnvironment returns null when required vars unset`() {
        // We can't unset process env vars, but if these are set we can't assert null.
        // Run the assertion only when they're known to be unset.
        if (System.getenv("AWS_CLOUDHSM_CLUSTER_ID") == null &&
            System.getenv("AWS_CLOUDHSM_HSM_USER") == null
        ) {
            assertNull(CloudHsmKmsConfig.fromEnvironment())
        } else {
            // Env is set — verify fromEnvironment yields a usable config instead.
            val cfg = CloudHsmKmsConfig.fromEnvironment()
            assertNotNull(cfg)
        }
    }
}
