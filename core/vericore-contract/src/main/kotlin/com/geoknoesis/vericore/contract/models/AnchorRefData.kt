package com.geoknoesis.vericore.contract.models

import com.geoknoesis.vericore.anchor.AnchorRef
import kotlinx.serialization.Serializable

/**
 * Serializable representation of AnchorRef for contract models.
 * 
 * AnchorRef itself is not serializable, so this wrapper provides
 * serialization support while maintaining compatibility.
 */
@Serializable
data class AnchorRefData(
    val chainId: String,
    val txHash: String,
    val contract: String? = null,
    val extra: Map<String, String> = emptyMap()
) {
    /**
     * Converts to AnchorRef.
     */
    fun toAnchorRef(): AnchorRef = AnchorRef(
        chainId = chainId,
        txHash = txHash,
        contract = contract,
        extra = extra
    )
    
    companion object {
        /**
         * Creates AnchorRefData from AnchorRef.
         */
        fun fromAnchorRef(ref: AnchorRef): AnchorRefData = AnchorRefData(
            chainId = ref.chainId,
            txHash = ref.txHash,
            contract = ref.contract,
            extra = ref.extra
        )
    }
}

