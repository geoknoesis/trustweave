package org.trustweave.anchor.payment

import kotlinx.serialization.json.JsonElement

/**
 * Describes a ledger-touching operation for the purposes of fee estimation
 * and policy evaluation. Plugins inspect this to size gas / fee.
 *
 * `kind` is a free-form short identifier ("did.create", "credential.anchor",
 * "contract.execute", "revocation.update"). The treasury and policy layer use
 * it for per-operation caps and for the ledger entry.
 *
 * `payloadSizeBytes` lets size-sensitive chains (Bitcoin sat/vB,
 * Cardano-tx-size-based fees) compute an estimate without re-serialising.
 *
 * `contractCall` carries optional contract-execution detail (target address,
 * abi-encoded calldata) so EVM plugins can call `eth_estimateGas` and Cardano
 * plugins can size script execution units.
 */
data class OperationDescriptor(
    val kind: String,
    val chainId: String,
    val payload: JsonElement? = null,
    val payloadSizeBytes: Long? = null,
    val contractCall: ContractCall? = null,
) {
    init {
        require(kind.isNotBlank()) { "kind must not be blank" }
        require(chainId.isNotBlank()) { "chainId must not be blank" }
    }
}

data class ContractCall(
    val contractAddress: String,
    val callData: ByteArray,
    val value: TokenAmount? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContractCall) return false
        return contractAddress == other.contractAddress &&
            callData.contentEquals(other.callData) &&
            value == other.value
    }

    override fun hashCode(): Int {
        var result = contractAddress.hashCode()
        result = 31 * result + callData.contentHashCode()
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }
}
