package com.trustweave.credential

/**
 * Schema format enumeration.
 * 
 * Used to specify which schema validation format a plugin supports.
 * This enum is specific to credential schema validation.
 */
enum class SchemaFormat {
    /** JSON Schema Draft 7 or Draft 2020-12 */
    JSON_SCHEMA,

    /** SHACL (Shapes Constraint Language) for RDF validation */
    SHACL
}

