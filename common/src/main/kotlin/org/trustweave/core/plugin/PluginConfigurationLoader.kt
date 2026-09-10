package org.trustweave.core.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import org.trustweave.core.exception.ConfigException
import org.trustweave.core.exception.TrustWeaveException
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Loads strict UTF-8 JSON configuration, limited to 1 MiB and 64 nested containers. */
object PluginConfigurationLoader {
    private val configSerializer = serializer<PluginConfiguration>()
    private val identifier = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

    fun loadFromFile(path: String): PluginConfiguration {
        if (path.isBlank()) throw ConfigException.InvalidFormat(parseError = "File path cannot be blank")
        val file = File(path)
        if (!file.exists()) throw ConfigException.NotFound(path = path)
        return read(path) { file.inputStream() }
    }

    fun loadFromResource(resource: String): PluginConfiguration {
        if (resource.isBlank()) throw ConfigException.InvalidFormat(parseError = "Resource path cannot be blank")
        return read(resource) {
            PluginConfigurationLoader::class.java.classLoader.getResourceAsStream(resource)
                ?: throw ConfigException.NotFound(path = resource)
        }
    }

    private fun read(
        path: String,
        open: () -> InputStream,
    ): PluginConfiguration =
        try {
            val bytes = open().use { it.readNBytes(ConfigurationJson.MAX_BYTES + 1) }
            val text =
                try {
                    require(bytes.size <= ConfigurationJson.MAX_BYTES)
                    Charsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString()
                } catch (_: Exception) {
                    throw invalid()
                }
            loadFromJson(text)
        } catch (failure: TrustWeaveException) {
            throw failure
        } catch (_: Exception) {
            throw ConfigException.ReadFailed(path = path, reason = "Unable to read configuration")
        }

    /** Rejects unknown fields, duplicate member names, ambiguous providers and invalid identifiers. */
    fun loadFromJson(jsonString: String): PluginConfiguration =
        try {
            Json.decodeFromJsonElement(configSerializer, ConfigurationJson.parse(jsonString)).also { config ->
                require(config.plugins.size <= 1024)
                require(
                    config.plugins
                        .map { it.id }
                        .distinct()
                        .size == config.plugins.size,
                )
                require(config.plugins.all { identifier.matches(it.id) && identifier.matches(it.provider) })
                require(config.defaultProviders.size <= 128 && config.providerChains.size <= 128)
                require(config.defaultProviders.all { (domain, provider) -> identifier.matches(domain) && identifier.matches(provider) })
                require(
                    config.providerChains.all { (domain, providers) ->
                        identifier.matches(domain) &&
                            providers.size in 1..64 &&
                            providers.distinct().size == providers.size &&
                            providers.all(identifier::matches)
                    },
                )
            }
        } catch (_: Exception) {
            // Parser messages and causes can contain credentials; never retain them.
            throw invalid()
        }

    fun loadFromJsonObject(jsonObject: JsonObject): PluginConfiguration = loadFromJson(jsonObject.toString())

    private fun invalid() = ConfigException.InvalidFormat(parseError = "Invalid configuration schema, syntax or limits")
}
