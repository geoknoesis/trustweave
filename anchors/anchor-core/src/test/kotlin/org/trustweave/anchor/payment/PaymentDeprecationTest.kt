package org.trustweave.anchor.payment

import org.junit.jupiter.api.Test
import org.slf4j.helpers.MarkerIgnoringBase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaymentDeprecationTest {

    private class CapturingLogger : MarkerIgnoringBase() {
        val warnings = mutableListOf<String>()
        override fun getName(): String = "capturing"

        override fun isWarnEnabled(): Boolean = true
        override fun warn(msg: String?) { warnings += (msg ?: "") }
        override fun warn(format: String?, arg: Any?) { warnings += (format ?: "") }
        override fun warn(format: String?, vararg args: Any?) { warnings += (format ?: "") }
        override fun warn(format: String?, arg1: Any?, arg2: Any?) { warnings += (format ?: "") }
        override fun warn(msg: String?, t: Throwable?) { warnings += (msg ?: "") }

        // unused overrides — implement as no-ops
        override fun isTraceEnabled(): Boolean = false
        override fun trace(msg: String?) {}
        override fun trace(format: String?, arg: Any?) {}
        override fun trace(format: String?, arg1: Any?, arg2: Any?) {}
        override fun trace(format: String?, vararg arguments: Any?) {}
        override fun trace(msg: String?, t: Throwable?) {}

        override fun isDebugEnabled(): Boolean = false
        override fun debug(msg: String?) {}
        override fun debug(format: String?, arg: Any?) {}
        override fun debug(format: String?, arg1: Any?, arg2: Any?) {}
        override fun debug(format: String?, vararg arguments: Any?) {}
        override fun debug(msg: String?, t: Throwable?) {}

        override fun isInfoEnabled(): Boolean = false
        override fun info(msg: String?) {}
        override fun info(format: String?, arg: Any?) {}
        override fun info(format: String?, arg1: Any?, arg2: Any?) {}
        override fun info(format: String?, vararg arguments: Any?) {}
        override fun info(msg: String?, t: Throwable?) {}

        override fun isErrorEnabled(): Boolean = false
        override fun error(msg: String?) {}
        override fun error(format: String?, arg: Any?) {}
        override fun error(format: String?, arg1: Any?, arg2: Any?) {}
        override fun error(format: String?, vararg arguments: Any?) {}
        override fun error(msg: String?, t: Throwable?) {}
    }

    @Test
    fun `warns when raw privateKey present`() {
        val log = CapturingLogger()
        PaymentDeprecation.warnIfRawCreds("eip155:1", mapOf("privateKey" to "0xabc"), Any(), log)
        assertEquals(1, log.warnings.size)
    }

    @Test
    fun `warns once per provider instance for same options`() {
        val log = CapturingLogger()
        val provider = Any()
        val opts = mapOf("mnemonic" to "abandon abandon")
        PaymentDeprecation.warnIfRawCreds("algorand:testnet", opts, provider, log)
        PaymentDeprecation.warnIfRawCreds("algorand:testnet", opts, provider, log)
        PaymentDeprecation.warnIfRawCreds("algorand:testnet", opts, provider, log)
        assertEquals(1, log.warnings.size)
    }

    @Test
    fun `does not warn when options empty`() {
        val log = CapturingLogger()
        PaymentDeprecation.warnIfRawCreds("eip155:1", emptyMap(), Any(), log)
        assertTrue(log.warnings.isEmpty())
    }

    @Test
    fun `does not warn for keys that merely contain raw cred substrings`() {
        val log = CapturingLogger()
        PaymentDeprecation.warnIfRawCreds(
            "eip155:1",
            mapOf(
                "privateKeyRef" to "kms://k1",
                "encryptedSeed" to "blob",
                "skipValidation" to true,
            ),
            Any(),
            log,
        )
        assertTrue(log.warnings.isEmpty())
    }

    @Test
    fun `matches all known raw cred keys case-insensitively`() {
        val log = CapturingLogger()
        PaymentDeprecation.warnIfRawCreds(
            "cardano:preview",
            mapOf("SecretKey" to "x", "WIF" to "y"),
            Any(),
            log,
        )
        assertEquals(1, log.warnings.size)
    }

    @Test
    fun `different provider instances each warn once`() {
        val log = CapturingLogger()
        val opts = mapOf("seed" to "s")
        PaymentDeprecation.warnIfRawCreds("bip122:0", opts, Any(), log)
        PaymentDeprecation.warnIfRawCreds("bip122:0", opts, Any(), log)
        assertEquals(2, log.warnings.size)
    }
}
