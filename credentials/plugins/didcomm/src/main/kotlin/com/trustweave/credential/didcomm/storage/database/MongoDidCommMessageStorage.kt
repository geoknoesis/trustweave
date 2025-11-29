package com.trustweave.credential.didcomm.storage.database

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.bson.Document

/**
 * MongoDB-backed message storage.
 *
 * Uses MongoDB for flexible document storage with efficient queries.
 *
 * **Example Usage:**
 * ```kotlin
 * // Note: Requires MongoDB Kotlin driver
 * // val mongoClient = MongoClient.create("mongodb://localhost:27017")
 * // val storage = MongoDidCommMessageStorage(
 * //     mongoClient = mongoClient,
 * //     databaseName = "trustweave",
 * //     collectionName = "didcomm_messages"
 * // )
 * ```
 *
 * **Note**: Requires MongoDB Kotlin driver dependency:
 * ```kotlin
 * implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.0")
 * ```
 *
 * This implementation uses reflection to avoid direct dependency on MongoDB driver.
 * When MongoDB driver is available, it will work correctly.
 */
class MongoDidCommMessageStorage(
    private val mongoClient: Any, // MongoClient from mongodb-driver-kotlin-coroutine
    private val databaseName: String = "trustweave",
    private val collectionName: String = "didcomm_messages"
) : DidCommMessageStorage {

    // Note: Using reflection to avoid direct dependency
    // In production with MongoDB driver, these will be properly typed
    private val database: Any by lazy {
        getDatabaseMethod(mongoClient, databaseName)
    }

    private val collection: Any by lazy {
        getCollectionMethod(database, collectionName)
    }

    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    init {
        createIndexes()
    }

    override suspend fun store(message: DidCommMessage): String = withContext(Dispatchers.IO) {
        val messageJson = json.encodeToString(
            DidCommMessage.serializer(),
            message
        )

        // Parse JSON to BSON Document
        val document = Document.parse(messageJson).apply {
            put("_id", message.id)
            put("from_did", message.from)
            put("to_dids", message.to)
            put("type", message.type)
            put("thid", message.thid)
            put("pthid", message.pthid)
            put("created_time", message.created)
            put("expires_time", message.expiresTime)
            put("created_at", System.currentTimeMillis())
        }

        // Insert document
        insertOneMethod(collection, document)

        // Index by DID
        message.from?.let { indexMessageForDid(message.id, it, "from") }
        message.to.forEach { indexMessageForDid(message.id, it, "to") }

        // Index by thread
        message.thid?.let { indexMessageForThread(message.id, it) }

        message.id
    }

    override suspend fun get(messageId: String): DidCommMessage? = withContext(Dispatchers.IO) {
        val filter = createFilter("_id", messageId)
        val document = findOneMethod(collection, filter) as? Document
            ?: return@withContext null

        parseDocumentToMessage(document)
    }

    override suspend fun getMessagesForDid(
        did: String,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> = withContext(Dispatchers.IO) {
        val filter = createOrFilter(
            createFilter("from_did", did),
            createInFilter("to_dids", did)
        )

        val documents = findMethod(collection, filter, limit, offset, "created_time", -1)
            .map { it as Document }

        documents.mapNotNull { parseDocumentToMessage(it) }
    }

    override suspend fun getThreadMessages(thid: String): List<DidCommMessage> = withContext(Dispatchers.IO) {
        val filter = createFilter("thid", thid)
        val documents = findMethod(collection, filter, Int.MAX_VALUE, 0, "created_time", 1)
            .map { it as Document }

        documents.mapNotNull { parseDocumentToMessage(it) }
    }

    override suspend fun delete(messageId: String): Boolean = withContext(Dispatchers.IO) {
        val filter = createFilter("_id", messageId)
        val result = deleteOneMethod(collection, filter)
        result > 0
    }

    override suspend fun deleteMessagesForDid(did: String): Int = withContext(Dispatchers.IO) {
        val filter = createOrFilter(
            createFilter("from_did", did),
            createInFilter("to_dids", did)
        )
        deleteManyMethod(collection, filter)
    }

    override suspend fun deleteThreadMessages(thid: String): Int = withContext(Dispatchers.IO) {
        val filter = createFilter("thid", thid)
        deleteManyMethod(collection, filter)
    }

    override suspend fun countMessagesForDid(did: String): Int = withContext(Dispatchers.IO) {
        val filter = createOrFilter(
            createFilter("from_did", did),
            createInFilter("to_dids", did)
        )
        countDocumentsMethod(collection, filter).toInt()
    }

    override suspend fun search(
        filter: MessageFilter,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> = withContext(Dispatchers.IO) {
        val mongoFilter = buildMongoFilter(filter)
        val documents = findMethod(collection, mongoFilter, limit, offset, "created_time", -1)
            .map { it as Document }

        documents.mapNotNull { parseDocumentToMessage(it) }
    }

    private fun parseDocumentToMessage(document: Document): DidCommMessage? {
        return try {
            // Convert BSON Document to JSON string
            val jsonString = document.toJson()
            json.decodeFromString(DidCommMessage.serializer(), jsonString)
        } catch (e: Exception) {
            null
        }
    }

    private fun buildMongoFilter(filter: MessageFilter): Any {
        val conditions = mutableListOf<Any>()

        filter.fromDid?.let {
            conditions.add(createFilter("from_did", it))
        }
        filter.toDid?.let {
            conditions.add(createInFilter("to_dids", it))
        }
        filter.type?.let {
            conditions.add(createFilter("type", it))
        }
        filter.thid?.let {
            conditions.add(createFilter("thid", it))
        }
        filter.createdAfter?.let {
            conditions.add(createGteFilter("created_time", it))
        }
        filter.createdBefore?.let {
            conditions.add(createLteFilter("created_time", it))
        }

        return if (conditions.isEmpty()) {
            Document()
        } else {
            createAndFilter(conditions)
        }
    }

    private fun createIndexes() {
        // Create indexes for performance
        // Note: Using reflection to avoid direct dependency
        try {
            createIndexMethod(collection, "from_did", 1)
            createIndexMethod(collection, "to_dids", 1)
            createIndexMethod(collection, "thid", 1)
            createIndexMethod(collection, "created_time", -1) // Descending
            createIndexMethod(collection, "type", 1)

            // Compound index for DID + time queries
            createCompoundIndexMethod(collection, listOf("from_did" to 1, "created_time" to -1))
        } catch (e: Exception) {
            // Indexes may already exist, or MongoDB not available
        }
    }

    // Helper methods using reflection to avoid direct MongoDB dependency
    // In production, use proper MongoDB client types

    private fun getDatabaseMethod(client: Any, dbName: String): Any {
        return try {
            val method = client.javaClass.getMethod("getDatabase", String::class.java)
            method.invoke(client, dbName)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to get MongoDB database. Ensure MongoDB driver is available.", e)
        }
    }

    private fun getCollectionMethod(database: Any, collectionName: String): Any {
        return try {
            val method = database.javaClass.getMethod("getCollection", String::class.java, Class::class.java)
            method.invoke(database, collectionName, Document::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to get MongoDB collection. Ensure MongoDB driver is available.", e)
        }
    }

    private fun insertOneMethod(collection: Any, document: Document) {
        try {
            val method = collection.javaClass.getMethod("insertOne", Document::class.java)
            method.invoke(collection, document)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to insert document", e)
        }
    }

    private fun findOneMethod(collection: Any, filter: Any): Any? {
        return try {
            val method = collection.javaClass.getMethod("findOne", Any::class.java)
            method.invoke(collection, filter) as? Document
        } catch (e: Exception) {
            null
        }
    }

    private fun findMethod(
        collection: Any,
        filter: Any,
        limit: Int,
        skip: Int,
        sortField: String,
        sortDirection: Int
    ): List<Any> {
        return try {
            // Use MongoDB find with sort, skip, limit
            val findMethod = collection.javaClass.getMethod("find", Any::class.java)
            val cursor = findMethod.invoke(collection, filter)

            // Apply sort, skip, limit using cursor methods
            val sortMethod = cursor.javaClass.getMethod("sort", Any::class.java)
            val sortDoc = Document(sortField, sortDirection)
            val sortedCursor = sortMethod.invoke(cursor, sortDoc)

            val skipMethod = sortedCursor.javaClass.getMethod("skip", Int::class.java)
            val skippedCursor = skipMethod.invoke(sortedCursor, skip)

            val limitMethod = skippedCursor.javaClass.getMethod("limit", Int::class.java)
            val limitedCursor = limitMethod.invoke(skippedCursor, limit)

            val toListMethod = limitedCursor.javaClass.getMethod("toList")
            @Suppress("UNCHECKED_CAST")
            (toListMethod.invoke(limitedCursor) as? List<Any>) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun deleteOneMethod(collection: Any, filter: Any): Long {
        return try {
            val method = collection.javaClass.getMethod("deleteOne", Any::class.java)
            val result = method.invoke(collection, filter)
            val deletedCountMethod = result.javaClass.getMethod("deletedCount")
            deletedCountMethod.invoke(result) as? Long ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun deleteManyMethod(collection: Any, filter: Any): Int {
        return try {
            val method = collection.javaClass.getMethod("deleteMany", Any::class.java)
            val result = method.invoke(collection, filter)
            val deletedCountMethod = result.javaClass.getMethod("deletedCount")
            (deletedCountMethod.invoke(result) as? Long)?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun countDocumentsMethod(collection: Any, filter: Any): Long {
        return try {
            val method = collection.javaClass.getMethod("countDocuments", Any::class.java)
            method.invoke(collection, filter) as? Long ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun createIndexMethod(collection: Any, field: String, direction: Int) {
        try {
            val indexDoc = Document(field, direction)
            val method = collection.javaClass.getMethod("createIndex", Any::class.java)
            method.invoke(collection, indexDoc)
        } catch (e: Exception) {
            // Index may already exist
        }
    }

    private fun createCompoundIndexMethod(collection: Any, fields: List<Pair<String, Int>>) {
        try {
            val indexDoc = Document().apply {
                fields.forEach { (field, direction) ->
                    put(field, direction)
                }
            }
            val method = collection.javaClass.getMethod("createIndex", Any::class.java)
            method.invoke(collection, indexDoc)
        } catch (e: Exception) {
            // Index may already exist
        }
    }

    private fun createFilter(field: String, value: Any): Document {
        return Document(field, value)
    }

    private fun createInFilter(field: String, value: Any): Document {
        return Document(field, Document("\$in", listOf(value)))
    }

    private fun createGteFilter(field: String, value: Any): Document {
        return Document(field, Document("\$gte", value))
    }

    private fun createLteFilter(field: String, value: Any): Document {
        return Document(field, Document("\$lte", value))
    }

    private fun createOrFilter(vararg filters: Any): Document {
        return Document("\$or", filters.toList())
    }

    private fun createAndFilter(filters: List<Any>): Document {
        return Document("\$and", filters)
    }

    private fun indexMessageForDid(messageId: String, did: String, role: String) {
        // In MongoDB, we can query directly, but we can also maintain a separate index collection
        // For now, we'll rely on direct queries to to_dids array
    }

    private fun indexMessageForThread(messageId: String, thid: String) {
        // Thread indexing is handled by thid field in main collection
    }

    override fun setEncryption(encryption: com.trustweave.credential.didcomm.storage.encryption.MessageEncryption?) {
        // MongoDB encryption would be implemented similarly to PostgreSQL
        // For now, encryption is not implemented for MongoDB
    }

    override suspend fun markAsArchived(messageIds: List<String>, archiveId: String) = withContext(Dispatchers.IO) {
        val filter = createInFilter("_id", messageIds)
        val update = Document("\$set", Document(mapOf(
            "archived" to true,
            "archive_id" to archiveId,
            "archived_at" to System.currentTimeMillis()
        )))
        updateManyMethod(collection, filter, update)
        Unit
    }

    override suspend fun isArchived(messageId: String): Boolean = withContext(Dispatchers.IO) {
        val filter = createFilter("_id", messageId)
        val document = findOneMethod(collection, filter) as? Document
            ?: return@withContext false
        document.getBoolean("archived", false)
    }

    private fun updateManyMethod(collection: Any, filter: Any, update: Document): Int {
        return try {
            val method = collection.javaClass.getMethod("updateMany", Any::class.java, Any::class.java)
            val result = method.invoke(collection, filter, update)
            val modifiedCountMethod = result.javaClass.getMethod("modifiedCount")
            (modifiedCountMethod.invoke(result) as? Long)?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun createInFilter(field: String, values: List<String>): Document {
        return Document(field, Document("\$in", values))
    }
}

