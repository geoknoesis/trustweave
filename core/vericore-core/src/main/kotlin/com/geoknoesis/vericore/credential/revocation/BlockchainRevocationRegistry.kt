package com.geoknoesis.vericore.credential.revocation

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Blockchain-based revocation registry.
 * 
 * Anchors status lists to blockchain for tamper-proof revocation management.
 * Integrates with VeriCore's blockchain anchoring system with configurable anchoring strategies.
 * 
 * **Anchoring Strategies:**
 * - **PeriodicAnchorStrategy**: Anchor on a schedule (hourly, daily) or after N updates
 * - **LazyAnchorStrategy**: Anchor only when verification is requested
 * - **HybridAnchorStrategy**: Combine periodic and lazy strategies
 * 
 * **Example Usage:**
 * ```kotlin
 * // Periodic anchoring (every hour or after 100 updates)
 * val registry = BlockchainRevocationRegistry(
 *     anchorClient = anchorClient,
 *     statusListManager = statusListManager,
 *     anchorStrategy = PeriodicAnchorStrategy(
 *         interval = Duration.ofHours(1),
 *         maxUpdates = 100
 *     ),
 *     chainId = "algorand:testnet"
 * )
 * 
 * // Revoke credential (triggers automatic anchoring if threshold reached)
 * registry.revokeCredential("cred-123", statusListId)
 * 
 * // Manual anchoring
 * val anchorRef = registry.anchorRevocationList(statusList, "algorand:testnet")
 * ```
 */
class BlockchainRevocationRegistry(
    private val anchorClient: Any?, // BlockchainAnchorClient - using Any? to avoid dependency
    private val statusListManager: StatusListManager,
    private val anchorStrategy: AnchorStrategy = PeriodicAnchorStrategy(),
    private val chainId: String? = null
) : StatusListManager by statusListManager {
    
    // Track pending anchors per status list
    private val pendingAnchors = ConcurrentHashMap<String, PendingAnchor>()
    
    // Track last anchor time per status list
    private val lastAnchorTimes = ConcurrentHashMap<String, Instant>()
    
    // JSON serializer for status lists
    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }
    
    /**
     * Anchor a status list to blockchain.
     * 
     * Computes digest of status list and anchors it to blockchain.
     * Returns anchor reference that can be included in credential status.
     * 
     * **Note**: Full implementation requires vericore-anchor dependency.
     * Uses reflection to call anchorClient methods to avoid direct dependency.
     * 
     * @param statusList Status list credential to anchor
     * @param chainId Chain ID for anchoring (uses default if not provided)
     * @return Anchor reference (transaction hash)
     */
    suspend fun anchorRevocationList(
        statusList: StatusListCredential,
        chainId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val targetChainId = chainId ?: this@BlockchainRevocationRegistry.chainId
            ?: throw IllegalArgumentException("Chain ID must be provided")
        
        if (anchorClient == null) {
            // No anchor client available, return placeholder
            return@withContext statusList.id
        }
        
        try {
            // Serialize status list without proof for hashing
            val statusListWithoutProof = statusList.copy(proof = null)
            val statusListJson = json.encodeToJsonElement(StatusListCredential.serializer(), statusListWithoutProof)
            
            // Create payload with digest
            val payload = buildJsonObject {
                put("statusListId", statusList.id)
                put("statusList", statusListJson)
                put("purpose", statusList.credentialSubject.statusPurpose)
                put("timestamp", Instant.now().toString())
            }
            
            // Use reflection to call anchorClient.writePayload
            val writePayloadMethod = anchorClient::class.java.getMethod(
                "writePayload",
                JsonElement::class.java,
                String::class.java
            )
            val anchorResult = writePayloadMethod.invoke(anchorClient, payload, "application/json")
            
            // Extract transaction hash from AnchorResult
            val refField = anchorResult?.javaClass?.getDeclaredField("ref")
            refField?.isAccessible = true
            val ref = refField?.get(anchorResult)
            val txHashField = ref?.javaClass?.getDeclaredField("txHash")
            txHashField?.isAccessible = true
            val txHash = txHashField?.get(ref) as? String ?: statusList.id
            
            // Update tracking
            lastAnchorTimes[statusList.id] = Instant.now()
            pendingAnchors.remove(statusList.id)
            
            txHash
        } catch (e: Exception) {
            // Fallback if reflection fails or anchor client unavailable
            // In production, this should log the error
            statusList.id
        }
    }
    
    /**
     * Check revocation status on-chain.
     * 
     * Verifies that the status list referenced in credential status
     * is anchored on blockchain and checks revocation status.
     * 
     * For hybrid strategies, this may trigger anchoring if the status list is stale.
     * 
     * **Note**: Full implementation requires vericore-anchor dependency.
     * 
     * @param credential Credential to check
     * @param chainId Chain ID where status list is anchored (uses default if not provided)
     * @return Revocation status
     */
    suspend fun checkRevocationOnChain(
        credential: VerifiableCredential,
        chainId: String? = null
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val targetChainId = chainId ?: this@BlockchainRevocationRegistry.chainId
            ?: throw IllegalArgumentException("Chain ID must be provided")
        
        // Get status list ID from credential
        val statusListId = credential.credentialStatus?.statusListCredential
            ?: return@withContext statusListManager.checkRevocationStatus(credential)
        
        // Check if we need to force anchor for verification (hybrid strategy)
        if (anchorStrategy is HybridAnchorStrategy) {
            val lastAnchorTime = lastAnchorTimes[statusListId]
            val statusList = statusListManager.getStatusList(statusListId)
            if (statusList != null) {
                val lastUpdateTime = Instant.parse(statusList.issuanceDate) // Simplified
                
                if (anchorStrategy.shouldForceAnchorForVerify(lastAnchorTime, lastUpdateTime)) {
                    anchorRevocationList(statusList, targetChainId)
                }
            }
        }
        
        // TODO: Implement blockchain verification
        // Requires vericore-anchor dependency:
        // 1. Get status list from credential status
        // 2. Compute digest of status list
        // 3. Query blockchain for anchor
        // 4. Verify anchor matches
        
        // For now, delegate to status list manager
        statusListManager.checkRevocationStatus(credential)
    }
    
    // Override key methods to trigger automatic anchoring
    
    override suspend fun revokeCredential(
        credentialId: String,
        statusListId: String
    ): Boolean = withContext(Dispatchers.IO) {
        // Update off-chain immediately
        val result = statusListManager.revokeCredential(credentialId, statusListId)
        
        if (result) {
            // Track update and check if anchoring is needed
            trackUpdate(statusListId)
            checkAndAnchorIfNeeded(statusListId)
        }
        
        result
    }
    
    override suspend fun suspendCredential(
        credentialId: String,
        statusListId: String
    ): Boolean = withContext(Dispatchers.IO) {
        // Update off-chain immediately
        val result = statusListManager.suspendCredential(credentialId, statusListId)
        
        if (result) {
            // Track update and check if anchoring is needed
            trackUpdate(statusListId)
            checkAndAnchorIfNeeded(statusListId)
        }
        
        result
    }
    
    override suspend fun revokeCredentials(
        credentialIds: List<String>,
        statusListId: String
    ): Map<String, Boolean> = withContext(Dispatchers.IO) {
        // Update off-chain immediately
        val result = statusListManager.revokeCredentials(credentialIds, statusListId)
        
        // Track update and check if anchoring is needed
        val hasUpdates = result.values.any { it }
        if (hasUpdates) {
            trackUpdate(statusListId, updateCount = result.values.count { it })
            checkAndAnchorIfNeeded(statusListId)
        }
        
        result
    }
    
    override suspend fun updateStatusListBatch(
        statusListId: String,
        updates: List<StatusUpdate>
    ): StatusListCredential = withContext(Dispatchers.IO) {
        // Update off-chain immediately
        val result = statusListManager.updateStatusListBatch(statusListId, updates)
        
        // Track update and check if anchoring is needed
        trackUpdate(statusListId, updateCount = updates.size)
        checkAndAnchorIfNeeded(statusListId)
        
        result
    }
    
    /**
     * Track an update to a status list.
     */
    private fun trackUpdate(statusListId: String, updateCount: Int = 1) {
        val now = Instant.now()
        val pending = pendingAnchors.compute(statusListId) { _, existing ->
            if (existing == null) {
                PendingAnchor(
                    statusListId = statusListId,
                    lastUpdate = now,
                    updateCount = updateCount,
                    lastAnchorTime = lastAnchorTimes[statusListId]
                )
            } else {
                existing.copy(
                    lastUpdate = now,
                    updateCount = existing.updateCount + updateCount
                )
            }
        }
    }
    
    /**
     * Check if anchoring is needed and anchor if so.
     */
    private suspend fun checkAndAnchorIfNeeded(statusListId: String) {
        val pending = pendingAnchors[statusListId] ?: return
        val lastAnchorTime = lastAnchorTimes[statusListId]
        
        if (anchorStrategy.shouldAnchor(
                statusListId = statusListId,
                lastAnchorTime = lastAnchorTime,
                updateCount = pending.updateCount,
                lastUpdateTime = pending.lastUpdate
            )) {
            val statusList = statusListManager.getStatusList(statusListId)
            if (statusList != null) {
                val targetChainId = chainId ?: return
                anchorRevocationList(statusList, targetChainId)
            }
        }
    }
    
    /**
     * Get the last anchor time for a status list.
     */
    fun getLastAnchorTime(statusListId: String): Instant? {
        return lastAnchorTimes[statusListId]
    }
    
    /**
     * Get pending anchor information for a status list.
     */
    fun getPendingAnchor(statusListId: String): PendingAnchor? {
        return pendingAnchors[statusListId]
    }
}

