package org.trustweave.credential.schema.internal

import org.trustweave.credential.model.SchemaFormat
import org.trustweave.credential.schema.SchemaValidator
import org.trustweave.credential.schema.SchemaValidatorRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of SchemaValidatorRegistry.
 * 
 * Thread-safe registry for schema validators.
 */
internal class DefaultSchemaValidatorRegistry : SchemaValidatorRegistry {
    private val validators = ConcurrentHashMap<SchemaFormat, SchemaValidator>()

    override fun register(validator: SchemaValidator) {
        validators[validator.schemaFormat] = validator
    }

    override fun unregister(format: SchemaFormat): Boolean {
        return validators.remove(format) != null
    }

    override fun get(format: SchemaFormat): SchemaValidator? {
        return validators[format]
    }

    override fun hasValidator(format: SchemaFormat): Boolean {
        return validators.containsKey(format)
    }

    override fun getRegisteredFormats(): List<SchemaFormat> {
        return validators.keys.toList()
    }

    override fun clear() {
        validators.clear()
    }
}

