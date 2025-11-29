package com.trustweave.credential

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for SchemaFormat enum.
 *
 * SchemaFormat is defined in the credentials:core module.
 */
class SchemaFormatTest {

    @Test
    fun `test SchemaFormat enum values`() {
        assertEquals(SchemaFormat.JSON_SCHEMA, SchemaFormat.valueOf("JSON_SCHEMA"))
        assertEquals(SchemaFormat.SHACL, SchemaFormat.valueOf("SHACL"))
        assertEquals(2, SchemaFormat.values().size)
    }
}

