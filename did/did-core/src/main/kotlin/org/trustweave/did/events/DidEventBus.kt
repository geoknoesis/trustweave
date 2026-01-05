package org.trustweave.did.events

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * DID Event Bus.
 *
 * Provides event-driven architecture for DID lifecycle events, enabling
 * decoupled communication between components.
 *
 * **Event Types:**
 * - DID Created
 * - DID Updated
 * - DID Deactivated
 * - Verification Method Added/Removed
 * - Service Added/Removed
 *
 * **Use Cases:**
 * - Webhook notifications
 * - Cache invalidation
 * - Audit logging
 * - Integration with external systems
 * - Real-time updates
 *
 * **Example Usage:**
 * ```kotlin
 * val eventBus = DefaultDidEventBus()
 *
 * // Subscribe to events
 * eventBus.events.collect { event ->
 *     when (event) {
 *         is DidEvent.Created -> println("DID created: ${event.did}")
 *         is DidEvent.Updated -> println("DID updated: ${event.did}")
 *     }
 * }
 *
 * // Publish event
 * eventBus.publish(DidEvent.Created(did = Did("did:web:example.com")))
 * ```
 */
interface DidEventBus {
    /**
     * Flow of DID events.
     */
    val events: Flow<DidEvent>
    
    /**
     * Publish an event.
     *
     * @param event The event to publish
     */
    suspend fun publish(event: DidEvent)
    
    /**
     * Subscribe to events of a specific type.
     *
     * @param T The event type to subscribe to
     * @return Flow of events of the specified type
     */
    fun <T : DidEvent> subscribe(eventType: Class<T>): Flow<T>
}

/**
 * Base sealed class for all DID events.
 */
sealed class DidEvent {
    /**
     * The DID associated with this event.
     */
    abstract val did: Did
    
    /**
     * Timestamp when the event occurred.
     */
    abstract val timestamp: Instant
    
    /**
     * DID was created.
     */
    data class Created(
        override val did: Did,
        val document: DidDocument? = null,
        override val timestamp: Instant = kotlinx.datetime.Clock.System.now()
    ) : DidEvent()
    
    /**
     * DID was updated.
     */
    data class Updated(
        override val did: Did,
        val previousDocument: DidDocument? = null,
        val updatedDocument: DidDocument? = null,
        override val timestamp: Instant = kotlinx.datetime.Clock.System.now()
    ) : DidEvent()
    
    /**
     * DID was deactivated.
     */
    data class Deactivated(
        override val did: Did,
        val document: DidDocument? = null,
        override val timestamp: Instant = kotlinx.datetime.Clock.System.now()
    ) : DidEvent()
    
    /**
     * Verification method was added.
     */
    data class VerificationMethodAdded(
        override val did: Did,
        val methodId: String,
        override val timestamp: Instant = kotlinx.datetime.Clock.System.now()
    ) : DidEvent()
    
    /**
     * Verification method was removed.
     */
    data class VerificationMethodRemoved(
        override val did: Did,
        val methodId: String,
        override val timestamp: Instant = kotlinx.datetime.Clock.System.now()
    ) : DidEvent()
    
    /**
     * Service was added.
     */
    data class ServiceAdded(
        override val did: Did,
        val serviceId: String,
        override val timestamp: Instant = kotlinx.datetime.Clock.System.now()
    ) : DidEvent()
    
    /**
     * Service was removed.
     */
    data class ServiceRemoved(
        override val did: Did,
        val serviceId: String,
        override val timestamp: Instant = kotlinx.datetime.Clock.System.now()
    ) : DidEvent()
}

/**
 * Default implementation of DID event bus using Kotlin Flow.
 */
class DefaultDidEventBus : DidEventBus {
    private val _events = MutableSharedFlow<DidEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )
    
    override val events: Flow<DidEvent> = _events.asSharedFlow()
    
    override suspend fun publish(event: DidEvent) {
        _events.emit(event)
    }
    
    override fun <T : DidEvent> subscribe(eventType: Class<T>): Flow<T> {
        return events.filter { eventType.isInstance(it) }
            .map { it as T }
    }
}

/**
 * Extension function for type-safe event subscription.
 */
inline fun <reified T : DidEvent> DidEventBus.subscribe(): Flow<T> {
    return subscribe(T::class.java)
}

