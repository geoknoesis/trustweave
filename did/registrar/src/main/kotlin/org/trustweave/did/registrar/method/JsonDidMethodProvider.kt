package org.trustweave.did.registrar.method

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.registration.model.DidMethodRegistryEntry
import org.trustweave.did.spi.DidMethodProvider
import java.nio.file.Path

/**
 * [DidMethodProvider] that loads methods from JSON registration files.
 */
class JsonDidMethodProvider(
    private val registryEntries: List<DidMethodRegistryEntry>
) : DidMethodProvider {

    private val methods = registryEntries.mapNotNull { entry ->
        RegistryEntryMapper.mapToDidMethod(entry)?.let { method -> entry.name to method }
    }.toMap()

    override val name: String = "json-registration"
    override val supportedMethods: List<String> = methods.keys.toList()

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? = methods[methodName]

    companion object {
        fun fromDirectory(directory: Path): JsonDidMethodProvider {
            val entries = JsonDidMethodLoader().loadRegistryEntriesFromDirectory(directory)
            return JsonDidMethodProvider(entries)
        }

        fun fromClasspath(resourcePath: String = "did-methods"): JsonDidMethodProvider {
            val entries = JsonDidMethodLoader().loadRegistryEntriesFromClasspath(resourcePath)
            return JsonDidMethodProvider(entries)
        }
    }
}
