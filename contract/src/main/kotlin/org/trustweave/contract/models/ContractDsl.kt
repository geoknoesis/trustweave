package org.trustweave.contract.models

import org.trustweave.contract.ContractDraftRequest
import org.trustweave.core.json.JsonDataBuilder
import org.trustweave.core.json.JsonDataDsl
import org.trustweave.core.json.jsonData
import kotlinx.serialization.json.JsonObject

/**
 * Builder for [ExecutionContext].
 *
 * ```kotlin
 * executionContext {
 *     trigger {
 *         "floodDepthCm" to 75.0
 *         "credentialId" to floodCredential.id
 *     }
 *     timeContext = "2026-05-28T12:00:00Z"
 * }
 * ```
 */
@JsonDataDsl
class ExecutionContextBuilder {
    private var triggerData: JsonObject? = null
    private var eventData: JsonObject? = null
    private var additionalContext: JsonObject? = null

    /** ISO 8601 timestamp for time-based execution. */
    var timeContext: String? = null

    /** Parametric trigger data. */
    fun trigger(block: JsonDataBuilder.() -> Unit) {
        triggerData = jsonData(block)
    }

    /** Event-driven payload. */
    fun event(block: JsonDataBuilder.() -> Unit) {
        eventData = jsonData(block)
    }

    /** Free-form additional context. */
    fun additionalContext(block: JsonDataBuilder.() -> Unit) {
        additionalContext = jsonData(block)
    }

    fun build(): ExecutionContext = ExecutionContext(
        triggerData = triggerData,
        eventData = eventData,
        timeContext = timeContext,
        additionalContext = additionalContext
    )
}

/**
 * Build an [ExecutionContext] using the DSL.
 *
 * ```kotlin
 * trustWeave.contracts.executeContract(activeContract) {
 *     trigger { "floodDepthCm" to 75.0 }
 * }
 * ```
 */
fun executionContext(block: ExecutionContextBuilder.() -> Unit): ExecutionContext =
    ExecutionContextBuilder().apply(block).build()

/**
 * Builder for [ContractDraftRequest].
 *
 * ```kotlin
 * val request = contractDraft {
 *     contractType = ContractType.Insurance
 *     executionModel = ExecutionModel.Parametric
 *     parties = ContractParties(insurerDid, insuredDid)
 *     terms = terms
 *     effectiveDate = "2026-05-28T00:00:00Z"
 *     contractData {
 *         "domain" to "flood"
 *         "thresholds" {
 *             "floodDepthCm" to 50.0
 *         }
 *     }
 * }
 * ```
 */
@JsonDataDsl
class ContractDraftBuilder {
    var contractType: ContractType? = null
    var executionModel: ExecutionModel? = null
    var parties: ContractParties? = null
    var terms: ContractTerms? = null
    var effectiveDate: String? = null
    var expirationDate: String? = null
    private var contractData: JsonObject? = null

    fun contractData(block: JsonDataBuilder.() -> Unit) {
        contractData = jsonData(block)
    }

    fun build(): ContractDraftRequest {
        val type = requireNotNull(contractType) { "contractType is required" }
        val model = requireNotNull(executionModel) { "executionModel is required" }
        val party = requireNotNull(parties) { "parties is required" }
        val termsValue = requireNotNull(terms) { "terms is required" }
        val effective = requireNotNull(effectiveDate) { "effectiveDate is required" }
        val data = requireNotNull(contractData) {
            "contractData { } block is required"
        }
        return ContractDraftRequest(
            contractType = type,
            executionModel = model,
            parties = party,
            terms = termsValue,
            effectiveDate = effective,
            expirationDate = expirationDate,
            contractData = data
        )
    }
}

/**
 * Build a [ContractDraftRequest] using the DSL.
 */
fun contractDraft(block: ContractDraftBuilder.() -> Unit): ContractDraftRequest =
    ContractDraftBuilder().apply(block).build()
