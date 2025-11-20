package com.geoknoesis.vericore.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Notification event types.
 */
enum class NotificationType {
    CREDENTIAL_ISSUED,
    CREDENTIAL_EXPIRING,
    CREDENTIAL_EXPIRED,
    CREDENTIAL_REVOKED,
    CREDENTIAL_VERIFIED,
    PRESENTATION_CREATED,
    PRESENTATION_VERIFIED,
    DID_CREATED,
    DID_UPDATED,
    KEY_ROTATED,
    SYSTEM_ALERT
}

/**
 * Notification data model.
 */
@Serializable
data class Notification(
    val id: String,
    val type: NotificationType,
    val timestamp: String, // ISO 8601
    val recipient: String, // DID, email, user ID, etc.
    val title: String,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
    val read: Boolean = false
)

/**
 * Notification service interface.
 * 
 * Handles sending notifications via various channels (push, email, webhook, etc.).
 * 
 * **Example Usage:**
 * ```kotlin
 * val service = NotificationService()
 * 
 * service.sendNotification(
 *     type = NotificationType.CREDENTIAL_ISSUED,
 *     recipient = "did:key:holder",
 *     title = "Credential Issued",
 *     message = "Your credential has been issued",
 *     metadata = mapOf("credentialId" to "cred-123")
 * )
 * ```
 */
interface NotificationService {
    /**
     * Send a notification.
     * 
     * @param type Notification type
     * @param recipient Recipient identifier
     * @param title Notification title
     * @param message Notification message
     * @param metadata Additional metadata
     */
    suspend fun sendNotification(
        type: NotificationType,
        recipient: String,
        title: String,
        message: String,
        metadata: Map<String, String> = emptyMap()
    ): Notification
    
    /**
     * Get notifications for a recipient.
     * 
     * @param recipient Recipient identifier
     * @param unreadOnly Only return unread notifications
     * @param limit Maximum number of notifications
     * @return List of notifications
     */
    suspend fun getNotifications(
        recipient: String,
        unreadOnly: Boolean = false,
        limit: Int = 100
    ): List<Notification>
    
    /**
     * Mark notification as read.
     * 
     * @param notificationId Notification ID
     * @return true if marked as read
     */
    suspend fun markAsRead(notificationId: String): Boolean
}

/**
 * Webhook configuration.
 */
data class WebhookConfig(
    val url: String,
    val secret: String? = null,
    val events: List<NotificationType> = emptyList() // Empty = all events
)

/**
 * Notification service implementation with webhook support.
 */
class NotificationServiceImpl(
    private val webhooks: List<WebhookConfig> = emptyList()
) : NotificationService {
    private val notifications = ConcurrentHashMap<String, MutableList<Notification>>()
    private val httpClient = OkHttpClient()
    private val json = Json { prettyPrint = false; encodeDefaults = false }
    private val lock = Any()
    
    override suspend fun sendNotification(
        type: NotificationType,
        recipient: String,
        title: String,
        message: String,
        metadata: Map<String, String>
    ): Notification = withContext(Dispatchers.IO) {
        val notification = Notification(
            id = UUID.randomUUID().toString(),
            type = type,
            timestamp = Instant.now().toString(),
            recipient = recipient,
            title = title,
            message = message,
            metadata = metadata,
            read = false
        )
        
        // Store notification
        synchronized(lock) {
            notifications.computeIfAbsent(recipient) { mutableListOf() }.add(notification)
        }
        
        // Send webhooks
        webhooks.forEach { webhook ->
            if (webhook.events.isEmpty() || webhook.events.contains(type)) {
                sendWebhook(webhook, notification)
            }
        }
        
        notification
    }
    
    override suspend fun getNotifications(
        recipient: String,
        unreadOnly: Boolean,
        limit: Int
    ): List<Notification> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            notifications[recipient]?.let { list ->
                (if (unreadOnly) list.filter { !it.read } else list)
                    .sortedByDescending { it.timestamp }
                    .take(limit)
            } ?: emptyList()
        }
    }
    
    override suspend fun markAsRead(notificationId: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            notifications.values.forEach { list ->
                list.find { it.id == notificationId }?.let { notification ->
                    val index = list.indexOf(notification)
                    if (index >= 0) {
                        list[index] = notification.copy(read = true)
                        return@withContext true
                    }
                }
            }
            false
        }
    }
    
    private suspend fun sendWebhook(config: WebhookConfig, notification: Notification) {
        try {
            val payload = json.encodeToString(Notification.serializer(), notification)
            val body = payload.toRequestBody("application/json".toMediaType())
            
            val requestBuilder = Request.Builder()
                .url(config.url)
                .post(body)
                .header("Content-Type", "application/json")
            
            config.secret?.let { secret ->
                // Add signature header (simplified - in production use proper HMAC)
                requestBuilder.header("X-Webhook-Secret", secret)
            }
            
            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.close()
        } catch (e: Exception) {
            // Log error but don't fail notification
            println("Failed to send webhook: ${e.message}")
        }
    }
}

