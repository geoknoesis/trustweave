package com.trustweave.credential.didcomm.protocol.util

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import kotlinx.serialization.json.*

/**
 * Serialization utilities for credentials in DIDComm protocol context.
 * 
 * This provides protocol-specific serialization for embedding credentials
 * in DIDComm message attachments. Format-specific serialization should
 * be handled by format plugins; this utility provides a generic JSON
 * representation for protocol message purposes.
 */

/**
 * Serialize VerifiableCredential to JSON for DIDComm message attachments.
 * 
 * Uses kotlinx.serialization to serialize the VC model directly.
 */
fun VerifiableCredential.toJsonObject(): JsonObject {
    val json = Json { prettyPrint = false; encodeDefaults = false }
    return json.encodeToJsonElement(VerifiableCredential.serializer(), this) as JsonObject
}

/**
 * Serialize VerifiablePresentation to JSON for DIDComm message attachments.
 * 
 * Uses kotlinx.serialization to serialize the VP model directly.
 */
fun VerifiablePresentation.toJsonObject(): JsonObject {
    val json = Json { prettyPrint = false; encodeDefaults = false }
    return json.encodeToJsonElement(VerifiablePresentation.serializer(), this) as JsonObject
}

