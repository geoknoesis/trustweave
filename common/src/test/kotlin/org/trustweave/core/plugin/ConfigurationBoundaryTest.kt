package org.trustweave.core.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.trustweave.core.exception.ConfigException
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ConfigurationBoundaryTest {
    private val plugin = """{"id":"test","type":"CREDENTIAL_SERVICE","provider":"native"}"""

    @Test
    fun `ambiguous JSON and unknown schema fields fail closed`() {
        listOf(
            """{"plugins":[],"plugins":[]}""",
            """{"plugins":[],"\u0070lugins":[]}""",
            """{"defaultProviders":{"wallet":"a","wallet":"b"}}""",
            """{"plugins":[$plugin,$plugin]}""",
            """{"plugin":[]}""",
            """{plugins:[]}""",
            """{"plugins":[],}""",
            "[]",
            """{"providerChains":{"wallet":[]}}""",
            """{"providerChains":{"wallet":["a","a"]}}""",
            """{"defaultProviders":{"wallet":" "}}""",
            """{"plugins":[{"id":"test","type":"CREDENTIAL_SERVICE","provider":"native","config":{"secret":"a","secret":"b"}}]}""",
        ).forEach { input ->
            assertFailsWith<ConfigException.InvalidFormat> { PluginConfigurationLoader.loadFromJson(input) }
        }
    }

    @Test
    fun `schema errors never retain secret input or parser excerpts`() {
        val secret = "secret-do-not-log"
        val input = """{"plugins":[],"$secret":"$secret"}"""
        val failure = assertFailsWith<ConfigException.InvalidFormat> { PluginConfigurationLoader.loadFromJson(input) }
        assertNull(failure.jsonString)
        assertNull(failure.cause)
        assertFalse(failure.toString().contains(secret))
        assertFalse(failure.context.toString().contains(secret))
        assertFailsWith<ConfigException.InvalidFormat> {
            PluginConfigurationLoader.loadFromJsonObject(Json.parseToJsonElement(input).jsonObject)
        }
        val model = PluginConfig("safe", PluginType.CREDENTIAL_SERVICE, "native", config = mapOf(secret to secret))
        assertFalse(model.toString().contains(secret))
        assertFalse(PluginConfiguration(plugins = listOf(model)).toString().contains(secret))
    }

    @Test
    fun `bounded strict UTF8 applies to strings and files`(
        @TempDir directory: Path,
    ) {
        val oversized = " ".repeat(ConfigurationJson.MAX_BYTES + 1)
        val malformed = directory.resolve("malformed.json").toFile()
        malformed.writeBytes(byteArrayOf(0x7b, 0x22, 0xc3.toByte(), 0x28, 0x22, 0x3a, 0x31, 0x7d))
        assertFailsWith<ConfigException.InvalidFormat> { PluginConfigurationLoader.loadFromFile(malformed.path) }
        val large = directory.resolve("large.json").toFile()
        large.writeText(oversized)
        assertFailsWith<ConfigException.InvalidFormat> { PluginConfigurationLoader.loadFromFile(large.path) }
        assertFailsWith<ConfigException.InvalidFormat> { PluginConfigurationLoader.loadFromJson(oversized) }
        assertFailsWith<ConfigException.InvalidFormat> { PluginConfigurationLoader.loadFromJson("\"\uD800\"") }
        assertFailsWith<IllegalArgumentException> {
            ConfigurationJson.parse("""{"x":"${"é".repeat(ConfigurationJson.MAX_BYTES / 2)}"}""")
        }
        assertEquals(1, ConfigurationJson.parse("""{"x":${"[".repeat(63)}0${"]".repeat(63)}}""").size)
        assertFailsWith<IllegalArgumentException> {
            ConfigurationJson.parse("""{"x":${"[".repeat(64)}0${"]".repeat(64)}}""")
        }
    }

    @Test
    fun `valid escaped values and discovered fallback providers remain compatible`() {
        val config =
            PluginConfigurationLoader.loadFromJson(
                """{"plugins":[$plugin],"providerChains":{"wallet":["native","spi-fallback"]}}""",
            )
        assertEquals(listOf("native", "spi-fallback"), config.providerChains["wallet"])
        val parsed = ConfigurationJson.parse("""{"a":{"x":1},"b":{"x":2},"escaped":"\"},[\\"}""")
        assertEquals(3, parsed.size)
        assertEquals(PluginConfiguration(), PluginConfigurationLoader.loadFromJson("{}" + " ".repeat(ConfigurationJson.MAX_BYTES - 2)))
    }
}
