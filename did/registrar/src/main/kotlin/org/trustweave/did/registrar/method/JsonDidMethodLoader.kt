package org.trustweave.did.registrar.method

import org.trustweave.core.exception.SerializationException
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidMethod
import org.trustweave.did.registration.model.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Loads DID methods from JSON registration files.
 *
 * Supports both the official DID Method Registry format and the legacy TrustWeave format.
 */
class JsonDidMethodLoader {

    private val logger = LoggerFactory.getLogger(JsonDidMethodLoader::class.java)

    fun loadFromFile(filePath: Path): DidMethod = loadFromString(Files.readString(filePath))

    fun loadFromString(jsonString: String): DidMethod {
        val registryEntry = try { DidMethodRegistryEntryParser.parse(jsonString) } catch (e: Exception) { null }
        if (registryEntry != null) {
            return RegistryEntryMapper.mapToDidMethod(registryEntry)
                ?: throw TrustWeaveException.InvalidOperation(
                    message = "Cannot create DidMethod from registry entry: no implementation with driverUrl found for '${registryEntry.name}'",
                    context = mapOf("method" to registryEntry.name)
                )
        }
        val spec = try {
            DidRegistrationSpecParser.parse(jsonString)
        } catch (e: Exception) {
            throw SerializationException.InvalidJson(
                parseError = "Failed to parse DID registration JSON: ${e.message}",
                jsonString = jsonString
            ).apply { initCause(e) }
        }
        return createMethodFromSpec(spec)
    }

    fun loadFromInputStream(inputStream: InputStream): DidMethod =
        loadFromString(inputStream.bufferedReader().use { it.readText() })

    fun loadFromDirectory(directory: Path): List<DidMethod> {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.filter { it.toString().endsWith(".json", ignoreCase = true) }
                .map { filePath ->
                    try { loadFromFile(filePath) } catch (e: Exception) {
                        logger.error("Failed to load DID method from $filePath", e); null
                    }
                }
                .filter { it != null }.map { it as DidMethod }
                .collect(java.util.stream.Collectors.toList())
        }
    }

    fun loadFromClasspath(resourcePath: String): List<DidMethod> {
        val classLoader = Thread.currentThread().contextClassLoader ?: JsonDidMethodLoader::class.java.classLoader
        val resourceUrl = classLoader.getResource(resourcePath) ?: return emptyList()
        return when (resourceUrl.protocol) {
            "file" -> loadFromDirectory(Paths.get(resourceUrl.toURI()))
            "jar" -> loadFromJarResource(classLoader, resourcePath)
            else -> { logger.warn("Unsupported resource protocol: ${resourceUrl.protocol}"); emptyList() }
        }
    }

    fun loadRegistryEntriesFromDirectory(directory: Path): List<DidMethodRegistryEntry> {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.filter { it.toString().endsWith(".json", ignoreCase = true) }
                .map { filePath ->
                    try {
                        val s = Files.readString(filePath)
                        try { DidMethodRegistryEntryParser.parse(s) }
                        catch (e: Exception) { convertLegacySpecToEntry(DidRegistrationSpecParser.parse(s)) }
                    } catch (e: Exception) { logger.error("Failed to load $filePath", e); null }
                }
                .filter { it != null }.map { it as DidMethodRegistryEntry }
                .collect(java.util.stream.Collectors.toList())
        }
    }

    fun loadRegistryEntriesFromClasspath(resourcePath: String): List<DidMethodRegistryEntry> {
        val classLoader = Thread.currentThread().contextClassLoader ?: JsonDidMethodLoader::class.java.classLoader
        val resourceUrl = classLoader.getResource(resourcePath) ?: return emptyList()
        return when (resourceUrl.protocol) {
            "file" -> loadRegistryEntriesFromDirectory(Paths.get(resourceUrl.toURI()))
            "jar" -> loadRegistryEntriesFromJarResource(classLoader, resourcePath)
            else -> emptyList()
        }
    }

    private fun loadFromJarResource(classLoader: ClassLoader, resourcePath: String): List<DidMethod> {
        val methods = mutableListOf<DidMethod>()
        val en = classLoader.getResources(resourcePath)
        while (en.hasMoreElements()) {
            val url = en.nextElement()
            val conn = url.openConnection()
            if (conn.javaClass.name.contains("JarURLConnection")) {
                val jar = (conn as java.net.JarURLConnection).jarFile
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.name.startsWith(resourcePath) && e.name.endsWith(".json")) {
                        try { jar.getInputStream(e).use { methods.add(loadFromInputStream(it)) } }
                        catch (ex: Exception) { logger.error("Failed from JAR entry ${e.name}", ex) }
                    }
                }
            }
        }
        return methods
    }

    private fun loadRegistryEntriesFromJarResource(classLoader: ClassLoader, resourcePath: String): List<DidMethodRegistryEntry> {
        val entries = mutableListOf<DidMethodRegistryEntry>()
        val en = classLoader.getResources(resourcePath)
        while (en.hasMoreElements()) {
            val url = en.nextElement()
            val conn = url.openConnection()
            if (conn.javaClass.name.contains("JarURLConnection")) {
                val jar = (conn as java.net.JarURLConnection).jarFile
                val jarEntries = jar.entries()
                while (jarEntries.hasMoreElements()) {
                    val e = jarEntries.nextElement()
                    if (e.name.startsWith(resourcePath) && e.name.endsWith(".json")) {
                        try {
                            jar.getInputStream(e).use { is2 ->
                                val s = is2.bufferedReader().use { it.readText() }
                                try { entries.add(DidMethodRegistryEntryParser.parse(s)) }
                                catch (ex: Exception) { entries.add(convertLegacySpecToEntry(DidRegistrationSpecParser.parse(s))) }
                            }
                        } catch (ex: Exception) { logger.error("Failed from JAR entry ${e.name}", ex) }
                    }
                }
            }
        }
        return entries
    }

    private fun convertLegacySpecToEntry(spec: DidRegistrationSpec): DidMethodRegistryEntry {
        val impl = spec.driver?.baseUrl?.let { MethodImplementation(name = "Universal Resolver", driverUrl = it, testNet = false) }
        return DidMethodRegistryEntry(name = spec.name, status = spec.status, specification = spec.specification, contact = spec.contact, implementations = listOfNotNull(impl))
    }

    private fun createMethodFromSpec(spec: DidRegistrationSpec): DidMethod {
        val driver = spec.driver ?: throw TrustWeaveException.InvalidState(message = "Driver configuration is required", context = mapOf("method" to spec.name))
        return when (driver.type) {
            "universal-resolver" -> HttpDidMethod(spec)
            else -> throw TrustWeaveException.InvalidOperation(
                message = "Unsupported driver type: ${driver.type}. Use DidMethodProvider SPI for native/custom implementations.",
                context = mapOf("driverType" to driver.type, "method" to spec.name)
            )
        }
    }
}
