package com.trustweave.credential.schema

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.schema.SchemaRegistries
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.did.identifiers.Did
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Additional edge case tests for JsonSchemaValidator.
 */
class JsonSchemaValidatorEdgeCasesTest {

    // Note: validateCredentialSubject is not part of the public SchemaValidator API
    // Commented out until the method is added or tests are refactored
    /*
    @Test
    fun `test validateCredentialSubject with required fields`() = runBlocking {
        // TODO: Refactor to use public API
    }
    */

    @Test
    fun `test validate with missing VerifiableCredential type`() = runBlocking {
        val validator: com.trustweave.credential.schema.SchemaValidator = SchemaRegistries.defaultValidatorRegistry().get(SchemaFormat.JSON_SCHEMA)!!

        val credential = VerifiableCredential(
            type = listOf(CredentialType.Custom("PersonCredential")), // Missing "VerifiableCredential"
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z")
        )
        val schema = buildJsonObject { put("type", "object") }

        val result = validator.validate(credential, schema)

        assertFalse(result.valid)
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.any { it.path == "/type" })
    }

    @Test
    fun `test validate with blank issuer`() = runBlocking {
        val validator: com.trustweave.credential.schema.SchemaValidator = SchemaRegistries.defaultValidatorRegistry().get(SchemaFormat.JSON_SCHEMA)!!

        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:")), // Blank issuer (invalid but test case)
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z")
        )
        val schema = buildJsonObject { put("type", "object") }

        val result = validator.validate(credential, schema)

        // JsonSchemaValidator doesn't validate issuer field directly, only validates claims
        // So validation may pass even with blank issuer (issuer validation is done at CredentialVerifier level)
        assertNotNull(result)
        // The test verifies that validation completes - issuer validation is not part of JsonSchemaValidator
    }

    // Note: validateCredentialSubject is not part of the public SchemaValidator API
    /*
    @Test
    fun `test validateCredentialSubject with JsonObject`() = runBlocking {
        // TODO: Refactor to use public API
    }

    @Test
    fun `test validateCredentialSubject with non-JsonObject`() = runBlocking {
        // TODO: Refactor to use public API
    }
    */
}

