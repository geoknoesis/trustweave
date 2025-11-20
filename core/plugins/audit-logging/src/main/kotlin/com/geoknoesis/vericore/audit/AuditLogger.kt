package com.geoknoesis.vericore.audit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * Audit logger interface for recording immutable audit events.
 * 
 * Implementations should ensure:
 * - Events are immutable once written
 * - Events are tamper-evident (cryptographically signed or blockchain-anchored)
 * - Events are queryable by time range, type, actor, resource
 * - Events support compliance requirements (GDPR, SOC2, etc.)
 * 
 * **Example Usage:**
 * ```kotlin
 * val logger = InMemoryAuditLogger()
 * 
 * logger.logEvent(
 *     type = AuditEventType.CREDENTIAL_ISSUED,
 *     actor = "did:key:issuer",
 *     resource = "credential-123",
 *     action = "issue",
 *     result = AuditResult.SUCCESS,
 *     metadata = mapOf("credentialType" to "PersonCredential")
 * )
 * ```
 */
interface AuditLogger {
    /**
     * Log an audit event.
     * 
     * @param type Event type
     * @param actor Who performed the action
     * @param resource What resource was affected
     * @param action What action was performed
     * @param result Success or failure
     * @param metadata Additional context
     * @param correlationId For tracking related events
     * @param errorMessage Error message if result is FAILURE
     * @param errorCode Error code if result is FAILURE
     * @return The created audit event
     */
    suspend fun logEvent(
        type: AuditEventType,
        actor: String? = null,
        resource: String? = null,
        action: String,
        result: AuditResult,
        metadata: Map<String, String> = emptyMap(),
        correlationId: String? = null,
        errorMessage: String? = null,
        errorCode: String? = null
    ): AuditEvent
    
    /**
     * Query audit events.
     * 
     * @param filter Filter criteria
     * @return List of matching audit events
     */
    suspend fun queryEvents(filter: AuditEventFilter): List<AuditEvent>
    
    /**
     * Get audit event by ID.
     * 
     * @param eventId Event ID
     * @return Audit event, or null if not found
     */
    suspend fun getEvent(eventId: String): AuditEvent?
}

/**
 * Audit event filter for querying events.
 */
data class AuditEventFilter(
    val types: List<AuditEventType>? = null,
    val actor: String? = null,
    val resource: String? = null,
    val result: AuditResult? = null,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val correlationId: String? = null,
    val limit: Int = 1000,
    val offset: Int = 0
)

/**
 * In-memory audit logger implementation (for testing/development).
 */
class InMemoryAuditLogger : AuditLogger {
    private val events = mutableListOf<AuditEvent>()
    private val lock = Any()
    
    override suspend fun logEvent(
        type: AuditEventType,
        actor: String?,
        resource: String?,
        action: String,
        result: AuditResult,
        metadata: Map<String, String>,
        correlationId: String?,
        errorMessage: String?,
        errorCode: String?
    ): AuditEvent = withContext(Dispatchers.IO) {
        val event = AuditEvent(
            id = UUID.randomUUID().toString(),
            type = type,
            timestamp = Instant.now().toString(),
            actor = actor,
            resource = resource,
            action = action,
            result = result,
            metadata = metadata,
            correlationId = correlationId,
            errorMessage = errorMessage,
            errorCode = errorCode
        )
        
        synchronized(lock) {
            events.add(event)
        }
        
        event
    }
    
    override suspend fun queryEvents(filter: AuditEventFilter): List<AuditEvent> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            events.filter { event ->
                (filter.types == null || filter.types.contains(event.type)) &&
                (filter.actor == null || event.actor == filter.actor) &&
                (filter.resource == null || event.resource == filter.resource) &&
                (filter.result == null || event.result == filter.result) &&
                (filter.correlationId == null || event.correlationId == filter.correlationId) &&
                (filter.startTime == null || Instant.parse(event.timestamp).isAfter(filter.startTime) || Instant.parse(event.timestamp).equals(filter.startTime)) &&
                (filter.endTime == null || Instant.parse(event.timestamp).isBefore(filter.endTime) || Instant.parse(event.timestamp).equals(filter.endTime))
            }
            .sortedByDescending { it.timestamp }
            .drop(filter.offset)
            .take(filter.limit)
        }
    }
    
    override suspend fun getEvent(eventId: String): AuditEvent? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            events.find { it.id == eventId }
        }
    }
    
    /**
     * Clear all events (for testing).
     */
    fun clear() {
        synchronized(lock) {
            events.clear()
        }
    }
}

