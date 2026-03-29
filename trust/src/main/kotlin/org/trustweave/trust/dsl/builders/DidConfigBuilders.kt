package org.trustweave.trust.dsl.builders

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.KeyPurpose
import org.trustweave.kms.KeyManagementService

/**
 * DID methods configuration builder.
 */
class DidConfigBuilder {
    val methods = mutableMapOf<String, DidMethodConfig>()
    var defaultMethod: String? = null
        private set

    fun default(methodName: String) {
        defaultMethod = methodName
    }

    fun method(name: String, block: DidMethodConfigBuilder.() -> Unit) {
        methods[name] = DidMethodConfigBuilder().apply(block).build()
        if (defaultMethod == null) {
            defaultMethod = name
        }
    }
}

/**
 * DID method configuration.
 */
data class DidMethodConfig(
    val algorithm: KeyAlgorithm? = null,
    val domain: String? = null,
    val additionalProperties: Map<String, Any?> = emptyMap()
) {
    fun toOptions(kms: KeyManagementService): DidCreationOptions {
        val resolvedAlgorithm = algorithm ?: KeyAlgorithm.ED25519
        val props = buildMap<String, Any?> {
            putAll(additionalProperties)
            put("kms", kms)
            domain?.let { put("domain", it) }
        }
        return DidCreationOptions(
            algorithm = resolvedAlgorithm,
            purposes = listOf(KeyPurpose.AUTHENTICATION),
            additionalProperties = props
        )
    }
}

/**
 * DID method configuration builder.
 */
class DidMethodConfigBuilder {
    private var algorithm: KeyAlgorithm? = null
    private var domain: String? = null
    private val options = mutableMapOf<String, Any?>()

    fun algorithm(name: String) {
        algorithm = KeyAlgorithm.fromName(name)
    }

    fun algorithm(value: KeyAlgorithm) {
        algorithm = value
    }

    fun domain(name: String) {
        domain = name
    }

    fun option(key: String, value: Any?) {
        options[key] = value
    }

    fun build(): DidMethodConfig = DidMethodConfig(algorithm, domain, options)
}
