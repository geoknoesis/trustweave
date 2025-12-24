package org.trustweave.did.registration.loader

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.registration.mapper.RegistryEntryMapper
import org.trustweave.did.registration.model.DidMethodRegistryEntry
import org.trustweave.did.spi.DidMethodProvider
import java.nio.file.Path

/**
 * DID Method Provider that loads methods from JSON registration files.
 *
 * This provider can be registered via ServiceLoader to automatically
 * discover and load DID methods from JSON files.
 *
 * **ServiceLoader Registration:**
 * Create a file `META-INF/services/org.trustweave.did.spi.DidMethodProvider`
 * with the content:
 * ```
 * org.trustweave.did.registration.loader.JsonDidMethodProvider
 * ```
 */
class JsonDidMethodProvider(
    private val registryEntries: List<DidMethodRegistryEntry>
) : DidMethodProvider {

    private val methods = registryEntries.mapNotNull { entry ->
        RegistryEntryMapper.mapToDidMethod(entry)?.let { method ->
            entry.name to method
        }
    }.toMap()

    override val name: String = "json-registration"

    override val supportedMethods: List<String> = methods.keys.toList()

    override fun create(
        methodName: String,
        options: DidCreationOptions
    ): DidMethod? {
        return methods[methodName]
    }

    companion object {
        /**
         * Creates a provider from a directory of JSON registration files.
         */
        fun fromDirectory(directory: Path): JsonDidMethodProvider {
            val loader = JsonDidMethodLoader()
            val entries = loader.loadRegistryEntriesFromDirectory(directory)
            return JsonDidMethodProvider(entries)
        }

        /**
         * Creates a provider from a classpath resource directory.
         */
        fun fromClasspath(resourcePath: String = "did-methods"): JsonDidMethodProvider {
            val loader = JsonDidMethodLoader()
            val entries = loader.loadRegistryEntriesFromClasspath(resourcePath)
            return JsonDidMethodProvider(entries)
        }
    }
}

