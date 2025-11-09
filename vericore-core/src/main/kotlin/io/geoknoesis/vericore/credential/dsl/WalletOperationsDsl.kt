package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.wallet.CredentialCollection
import io.geoknoesis.vericore.credential.wallet.CredentialOrganization
import io.geoknoesis.vericore.credential.wallet.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wallet Organization Builder DSL.
 * 
 * Provides a fluent API for organizing credentials in wallets without manual type checks.
 * Automatically handles capability detection and provides helpful error messages.
 * 
 * **Example Usage**:
 * ```kotlin
 * wallet.organize {
 *     collection("Education", "Academic degrees") {
 *         add(bachelorId, masterId)
 *         tag(bachelorId, "education", "degree", "bachelor")
 *         tag(masterId, "education", "degree", "master")
 *     }
 *     
 *     collection("Work Experience") {
 *         add(job1Id, job2Id)
 *         tag(job1Id, "work", "employment", "completed")
 *         tag(job2Id, "work", "employment", "current")
 *     }
 * }
 * ```
 */
class WalletOrganizationBuilder(
    private val wallet: Wallet
) {
    private val collections = mutableListOf<CollectionOperation>()
    private val tags = mutableListOf<TagOperation>()
    private val metadata = mutableListOf<MetadataOperation>()
    
    /**
     * Create a collection and configure it.
     */
    fun collection(name: String, description: String? = null, block: CollectionBuilder.() -> Unit) {
        val builder = CollectionBuilder(name, description)
        builder.block()
        collections.add(CollectionOperation.Create(
            name = builder.name,
            description = builder.description,
            credentialIds = builder.credentialIds,
            tags = builder.tags
        ))
    }
    
    /**
     * Add tags to a credential.
     */
    fun tag(credentialId: String, vararg tags: String) {
        this.tags.add(TagOperation.Add(credentialId, tags.toSet()))
    }
    
    /**
     * Remove tags from a credential.
     */
    fun untag(credentialId: String, vararg tags: String) {
        this.tags.add(TagOperation.Remove(credentialId, tags.toSet()))
    }
    
    /**
     * Add metadata to a credential.
     */
    fun metadata(credentialId: String, block: MetadataBuilder.() -> Unit) {
        val builder = MetadataBuilder()
        builder.block()
        metadata.add(MetadataOperation.Add(credentialId, builder.metadata))
    }
    
    /**
     * Update notes for a credential.
     */
    fun notes(credentialId: String, notes: String?) {
        metadata.add(MetadataOperation.Notes(credentialId, notes))
    }
    
    /**
     * Execute all organization operations.
     */
    suspend fun execute(): OrganizationResult = withContext(Dispatchers.IO) {
        val orgWallet = wallet as? CredentialOrganization
            ?: throw IllegalStateException(
                "Wallet does not support organization features. " +
                "Create wallet with enableOrganization() in trustLayer.wallet { }"
            )
        
        val createdCollections = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        // Create collections and add credentials
        for (collectionOp in collections) {
            when (collectionOp) {
                is CollectionOperation.Create -> {
                    try {
                        val collectionId = orgWallet.createCollection(
                            collectionOp.name,
                            collectionOp.description
                        )
                        createdCollections.add(collectionId)
                        
                        // Add credentials to collection
                        for (credId in collectionOp.credentialIds) {
                            try {
                                orgWallet.addToCollection(credId, collectionId)
                            } catch (e: Exception) {
                                errors.add("Failed to add credential $credId to collection ${collectionOp.name}: ${e.message}")
                            }
                        }
                        
                        // Apply tags
                        for (tagOp in collectionOp.tags) {
                            try {
                                orgWallet.tagCredential(tagOp.credentialId, tagOp.tags)
                            } catch (e: Exception) {
                                errors.add("Failed to tag credential ${tagOp.credentialId}: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        errors.add("Failed to create collection ${collectionOp.name}: ${e.message}")
                    }
                }
            }
        }
        
        // Apply standalone tags
        for (tagOp in tags) {
            try {
                when (tagOp) {
                    is TagOperation.Add -> orgWallet.tagCredential(tagOp.credentialId, tagOp.tags)
                    is TagOperation.Remove -> orgWallet.untagCredential(tagOp.credentialId, tagOp.tags)
                }
            } catch (e: Exception) {
                errors.add("Failed to ${if (tagOp is TagOperation.Add) "tag" else "untag"} credential ${tagOp.credentialId}: ${e.message}")
            }
        }
        
        // Apply metadata
        for (metaOp in metadata) {
            try {
                when (metaOp) {
                    is MetadataOperation.Add -> orgWallet.addMetadata(metaOp.credentialId, metaOp.metadata)
                    is MetadataOperation.Notes -> orgWallet.updateNotes(metaOp.credentialId, metaOp.notes)
                }
            } catch (e: Exception) {
                errors.add("Failed to update metadata for credential ${metaOp.credentialId}: ${e.message}")
            }
        }
        
        OrganizationResult(
            collectionsCreated = createdCollections.size,
            errors = errors
        )
    }
    
    /**
     * Collection builder for configuring a collection.
     */
    inner class CollectionBuilder(
        val name: String,
        val description: String?
    ) {
        val credentialIds = mutableListOf<String>()
        val tags = mutableListOf<TagOperation>()
        
        /**
         * Add credentials to this collection.
         */
        fun add(vararg credentialIds: String) {
            this.credentialIds.addAll(credentialIds)
        }
        
        /**
         * Tag credentials in this collection.
         */
        fun tag(credentialId: String, vararg tags: String) {
            this.tags.add(TagOperation.Add(credentialId, tags.toSet()))
        }
    }
    
    /**
     * Metadata builder for configuring metadata.
     */
    inner class MetadataBuilder {
        val metadata = mutableMapOf<String, Any>()
        
        /**
         * Add a metadata entry.
         */
        infix fun String.to(value: Any) {
            metadata[this] = value
        }
    }
    
    /**
     * Collection operation.
     */
    private sealed class CollectionOperation {
        data class Create(
            val name: String,
            val description: String?,
            val credentialIds: List<String>,
            val tags: List<TagOperation>
        ) : CollectionOperation()
    }
    
    /**
     * Tag operation.
     */
    sealed class TagOperation {
        abstract val credentialId: String
        abstract val tags: Set<String>
        
        data class Add(override val credentialId: String, override val tags: Set<String>) : TagOperation()
        data class Remove(override val credentialId: String, override val tags: Set<String>) : TagOperation()
    }
    
    /**
     * Metadata operation.
     */
    private sealed class MetadataOperation {
        abstract val credentialId: String
        
        data class Add(override val credentialId: String, val metadata: Map<String, Any>) : MetadataOperation()
        data class Notes(override val credentialId: String, val notes: String?) : MetadataOperation()
    }
}

/**
 * Organization result.
 */
data class OrganizationResult(
    val collectionsCreated: Int,
    val errors: List<String>
) {
    val success: Boolean get() = errors.isEmpty()
}

/**
 * Extension function to organize credentials in a wallet.
 */
suspend fun Wallet.organize(block: WalletOrganizationBuilder.() -> Unit): OrganizationResult {
    val builder = WalletOrganizationBuilder(this)
    builder.block()
    return builder.execute()
}

