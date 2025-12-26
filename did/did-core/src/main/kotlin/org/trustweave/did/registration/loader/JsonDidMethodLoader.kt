package org.trustweave.did.registration.loader

import org.trustweave.did.DidMethod
import org.trustweave.did.registration.impl.HttpDidMethod
import org.trustweave.did.registration.mapper.RegistryEntryMapper
import org.trustweave.did.registration.model.*
import org.trustweave.did.util.DidLogging
import kotlinx.serialization.json.*
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Loads DID methods from JSON registration files.
 *
 * This allows DID methods to be registered by simply adding a JSON file
 * that follows the DID Registration specification, making it easy to add
 * new method support without writing code.
 *
 * **Example Usage:**
 * ```kotlin
 * // Load from a directory
 * val loader = JsonDidMethodLoader()
 * val methods = loader.loadFromDirectory(Paths.get("did-methods"))
 *
 * // Register all methods
 * methods.forEach { registry.register(it) }
 *
 * // Or load a single file
 * val method = loader.loadFromFile(Paths.get("did-methods/example.json"))
 * registry.register(method)
 * ```
 */
class JsonDidMethodLoader {

    private val logger = DidLogging.getLogger(JsonDidMethodLoader::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Loads a DID method from a JSON file.
     *
     * @param filePath Path to the JSON registration file
     * @return A DidMethod instance
     * @throws IllegalArgumentException if the file is invalid
     */
    fun loadFromFile(filePath: Path): DidMethod {
        val jsonString = Files.readString(filePath)
        return loadFromString(jsonString)
    }

    /**
     * Loads a DID method from a JSON string.
     *
     * Supports both:
     * 1. Official DID Method Registry format (from identity.foundation/did-registration)
     * 2. Legacy Trustweave format (with driver/capabilities fields)
     *
     * @param jsonString JSON registration string
     * @return A DidMethod instance
     * @throws IllegalArgumentException if the JSON is invalid
     */
    fun loadFromString(jsonString: String): DidMethod {
        // Try to parse as official registry format first
        val registryEntry = try {
            DidMethodRegistryEntryParser.parse(jsonString)
        } catch (e: Exception) {
            null
        }

        if (registryEntry != null) {
            // Map from official registry format to DidMethod
            return RegistryEntryMapper.mapToDidMethod(registryEntry)
                ?: throw org.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                    message = "Cannot create DidMethod from registry entry: no implementation with driverUrl found for method '${registryEntry.name}'",
                    context = mapOf("method" to registryEntry.name)
                )
        }

        // Fall back to legacy format
        val spec = try {
            DidRegistrationSpecParser.parse(jsonString)
        } catch (e: Exception) {
            throw org.trustweave.core.exception.TrustWeaveException.InvalidJson(
                parseError = "Failed to parse DID registration JSON. Expected either official registry format or legacy Trustweave format: ${e.message ?: "Unknown error"}",
                jsonString = jsonString
            ).apply { initCause(e) }
        }

        return createMethodFromSpec(spec)
    }

    /**
     * Loads a DID method from an InputStream.
     *
     * @param inputStream Input stream containing JSON registration data
     * @return A DidMethod instance
     */
    fun loadFromInputStream(inputStream: InputStream): DidMethod {
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        return loadFromString(jsonString)
    }

    /**
     * Loads all DID methods from a directory.
     *
     * Scans the directory for JSON files and loads each as a DID method.
     *
     * @param directory Path to directory containing JSON registration files
     * @return List of DidMethod instances
     */
    fun loadFromDirectory(directory: Path): List<DidMethod> {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return emptyList()
        }

        return Files.list(directory).use { stream ->
            stream
                .filter { it.toString().endsWith(".json", ignoreCase = true) }
                .map { filePath ->
                    try {
                        loadFromFile(filePath)
                    } catch (e: Exception) {
                        // Log error but continue loading other files
                        logger.error("Failed to load DID method from $filePath", e)
                        null
                    }
                }
                .filter { it != null }
                .map { it as DidMethod }
                .collect(java.util.stream.Collectors.toList())
        }
    }

    /**
     * Loads all DID methods from a classpath resource directory.
     *
     * Useful for bundling method registrations with the application.
     *
     * @param resourcePath Classpath resource path (e.g., "did-methods")
     * @return List of DidMethod instances
     */
    fun loadFromClasspath(resourcePath: String): List<DidMethod> {
        val classLoader = Thread.currentThread().contextClassLoader
            ?: JsonDidMethodLoader::class.java.classLoader

        val resourceUrl = classLoader.getResource(resourcePath)
            ?: return emptyList()

        return when {
            resourceUrl.protocol == "file" -> {
                val path = Paths.get(resourceUrl.toURI())
                loadFromDirectory(path)
            }
            resourceUrl.protocol == "jar" -> {
                // For JAR files, we need to list resources differently
                loadFromJarResource(classLoader, resourcePath)
            }
            else -> {
                logger.warn("Unsupported resource protocol: ${resourceUrl.protocol}")
                emptyList()
            }
        }
    }

    private fun loadFromJarResource(classLoader: ClassLoader, resourcePath: String): List<DidMethod> {
        val methods = mutableListOf<DidMethod>()
        val resourceEnum = classLoader.getResources(resourcePath)

        while (resourceEnum.hasMoreElements()) {
            val url = resourceEnum.nextElement()
            val connection = url.openConnection()

            if (connection.javaClass.name.contains("JarURLConnection")) {
                val jarConnection = connection as java.net.JarURLConnection
                val jarFile = jarConnection.jarFile

                val entries = jarFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (name.startsWith(resourcePath) && name.endsWith(".json")) {
                        try {
                            jarFile.getInputStream(entry).use { inputStream ->
                                methods.add(loadFromInputStream(inputStream))
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to load DID method from JAR entry $name", e)
                        }
                    }
                }
            }
        }

        return methods
    }

    /**
     * Loads registry entries from a directory (for JsonDidMethodProvider).
     */
    fun loadRegistryEntriesFromDirectory(directory: Path): List<DidMethodRegistryEntry> {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return emptyList()
        }

        return Files.list(directory).use { stream ->
            stream
                .filter { it.toString().endsWith(".json", ignoreCase = true) }
                .map { filePath ->
                    try {
                        val jsonString = Files.readString(filePath)
                        DidMethodRegistryEntryParser.parse(jsonString)
                    } catch (e: Exception) {
                        // Try legacy format
                        try {
                            val jsonString = Files.readString(filePath)
                            val spec = DidRegistrationSpecParser.parse(jsonString)
                            // Convert legacy spec to registry entry
                            convertLegacySpecToEntry(spec)
                        } catch (e2: Exception) {
                            logger.error("Failed to load DID method from $filePath", e2)
                            null
                        }
                    }
                }
                .filter { it != null }
                .map { it as DidMethodRegistryEntry }
                .collect(java.util.stream.Collectors.toList())
        }
    }

    /**
     * Loads registry entries from classpath (for JsonDidMethodProvider).
     */
    fun loadRegistryEntriesFromClasspath(resourcePath: String): List<DidMethodRegistryEntry> {
        val classLoader = Thread.currentThread().contextClassLoader
            ?: JsonDidMethodLoader::class.java.classLoader

        val resourceUrl = classLoader.getResource(resourcePath)
            ?: return emptyList()

        return when {
            resourceUrl.protocol == "file" -> {
                val path = Paths.get(resourceUrl.toURI())
                loadRegistryEntriesFromDirectory(path)
            }
            resourceUrl.protocol == "jar" -> {
                loadRegistryEntriesFromJarResource(classLoader, resourcePath)
            }
            else -> {
                logger.warn("Unsupported resource protocol: ${resourceUrl.protocol}")
                emptyList()
            }
        }
    }

    private fun loadRegistryEntriesFromJarResource(classLoader: ClassLoader, resourcePath: String): List<DidMethodRegistryEntry> {
        val entries = mutableListOf<DidMethodRegistryEntry>()
        val resourceEnum = classLoader.getResources(resourcePath)

        while (resourceEnum.hasMoreElements()) {
            val url = resourceEnum.nextElement()
            val connection = url.openConnection()

            if (connection.javaClass.name.contains("JarURLConnection")) {
                val jarConnection = connection as java.net.JarURLConnection
                val jarFile = jarConnection.jarFile

                val jarEntries = jarFile.entries()
                while (jarEntries.hasMoreElements()) {
                    val entry = jarEntries.nextElement()
                    val name = entry.name

                    if (name.startsWith(resourcePath) && name.endsWith(".json")) {
                        try {
                            jarFile.getInputStream(entry).use { inputStream ->
                                val jsonString = inputStream.bufferedReader().use { it.readText() }
                                entries.add(DidMethodRegistryEntryParser.parse(jsonString))
                            }
                        } catch (e: Exception) {
                            // Try legacy format
                            try {
                                jarFile.getInputStream(entry).use { inputStream ->
                                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                                    val spec = DidRegistrationSpecParser.parse(jsonString)
                                    entries.add(convertLegacySpecToEntry(spec))
                                }
                            } catch (e2: Exception) {
                                logger.error("Failed to load DID method from JAR entry $name", e2)
                            }
                        }
                    }
                }
            }
        }

        return entries
    }

    /**
     * Converts legacy DidRegistrationSpec to DidMethodRegistryEntry for backward compatibility.
     */
    private fun convertLegacySpecToEntry(spec: DidRegistrationSpec): DidMethodRegistryEntry {
        val implementation = spec.driver?.baseUrl?.let { baseUrl ->
            MethodImplementation(
                name = "Universal Resolver",
                driverUrl = baseUrl,
                testNet = false
            )
        }

        return DidMethodRegistryEntry(
            name = spec.name,
            status = spec.status,
            specification = spec.specification,
            contact = spec.contact,
            implementations = listOfNotNull(implementation)
        )
    }

    /**
     * Creates a DidMethod instance from a registration spec (legacy format).
     */
    private fun createMethodFromSpec(spec: DidRegistrationSpec): DidMethod {
        val driver = spec.driver
            ?: throw org.trustweave.core.exception.TrustWeaveException.InvalidState(
                message = "Driver configuration is required",
                context = mapOf("method" to spec.name)
            )

        return when (driver.type) {
            "universal-resolver" -> {
                HttpDidMethod(spec)
            }
            "native" -> {
                throw org.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                    message = "Native driver type requires a custom implementation. Use DidMethodProvider SPI for native implementations.",
                    context = mapOf("driverType" to driver.type, "method" to spec.name)
                )
            }
            "custom" -> {
                throw org.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                    message = "Custom driver type requires additional configuration. Use DidMethodProvider SPI for custom implementations.",
                    context = mapOf("driverType" to driver.type, "method" to spec.name)
                )
            }
            else -> {
                throw org.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                    message = "Unsupported driver type: ${driver.type}",
                    context = mapOf("driverType" to driver.type, "method" to spec.name, "supportedTypes" to listOf("universal-resolver", "native", "custom"))
                )
            }
        }
    }
}

