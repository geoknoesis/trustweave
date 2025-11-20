package com.geoknoesis.vericore.audit

import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Audit event types for tracking operations in VeriCore.
 */
enum class AuditEventType {
    // DID Operations
    DID_CREATED,
    DID_RESOLVED,
    DID_UPDATED,
    DID_DEACTIVATED,
    
    // Credential Operations
    CREDENTIAL_ISSUED,
    CREDENTIAL_VERIFIED,
    CREDENTIAL_REVOKED,
    CREDENTIAL_SUSPENDED,
    CREDENTIAL_UNREVOKED,
    CREDENTIAL_UNSUSPENDED,
    
    // Presentation Operations
    PRESENTATION_CREATED,
    PRESENTATION_VERIFIED,
    
    // Wallet Operations
    CREDENTIAL_STORED,
    CREDENTIAL_RETRIEVED,
    CREDENTIAL_DELETED,
    CREDENTIAL_ARCHIVED,
    CREDENTIAL_UNARCHIVED,
    
    // Key Operations
    KEY_GENERATED,
    KEY_DELETED,
    KEY_ROTATED,
    
    // Schema Operations
    SCHEMA_REGISTERED,
    SCHEMA_VALIDATED,
    
    // Trust Registry Operations
    TRUST_ANCHOR_ADDED,
    TRUST_ANCHOR_REMOVED,
    TRUST_CHECKED,
    
    // Blockchain Operations
    BLOCKCHAIN_ANCHORED,
    BLOCKCHAIN_VERIFIED,
    
    // System Operations
    CONFIGURATION_CHANGED,
    ERROR_OCCURRED,
    SECURITY_EVENT
}

/**
 * Audit event data model.
 * 
 * Represents an immutable audit log entry for tracking all operations in VeriCore.
 * 
 * @param id Unique event identifier
 * @param type Event type
 * @param timestamp When the event occurred
 * @param actor Who performed the action (DID, user ID, etc.)
 * @param resource What resource was affected (DID, credential ID, etc.)
 * @param action What action was performed
 * @param result Success or failure
 * @param metadata Additional context (JSON)
 * @param correlationId For tracking related events
 */
@Serializable
data class AuditEvent(
    val id: String,
    val type: AuditEventType,
    val timestamp: String, // ISO 8601
    val actor: String? = null, // DID or user identifier
    val resource: String? = null, // Resource identifier (DID, credential ID, etc.)
    val action: String,
    val result: AuditResult,
    val metadata: Map<String, String> = emptyMap(),
    val correlationId: String? = null,
    val errorMessage: String? = null,
    val errorCode: String? = null
)

/**
 * Audit result.
 */
enum class AuditResult {
    SUCCESS,
    FAILURE,
    PARTIAL
}

