package com.trustweave.contract.evaluation

import com.trustweave.contract.models.ExecutionModel

/**
 * Reference to an evaluation engine from an execution model.
 *
 * Encapsulates engine identification and integrity verification requirements.
 */
sealed class EngineReference {
    /**
     * Engine identifier (null for Manual execution).
     */
    abstract val engineId: String?

    /**
     * Expected implementation hash for integrity verification (null if not required).
     */
    abstract val expectedHash: String?

    /**
     * Expected engine version for compatibility checks (null if not required).
     */
    abstract val expectedVersion: String?

    /**
     * Reference to an engine with integrity requirements.
     */
    data class WithEngine(
        override val engineId: String,
        override val expectedHash: String?,
        override val expectedVersion: String?
    ) : EngineReference()

    /**
     * Manual execution - no engine required.
     */
    object Manual : EngineReference() {
        override val engineId: String? = null
        override val expectedHash: String? = null
        override val expectedVersion: String? = null
    }
}

/**
 * Helper function to extract engine metadata from execution model.
 * Returns non-null engineId for non-Manual execution models.
 */
private fun ExecutionModel.getEngineMetadata(): Triple<String, String?, String?>? = when (this) {
    is ExecutionModel.Parametric -> Triple(evaluationEngine, engineHash, engineVersion)
    is ExecutionModel.Conditional -> Triple(evaluationEngine, engineHash, engineVersion)
    is ExecutionModel.Scheduled -> Triple(evaluationEngine, engineHash, engineVersion)
    is ExecutionModel.EventDriven -> Triple(evaluationEngine, engineHash, engineVersion)
    is ExecutionModel.Manual -> null
}

/**
 * Converts an ExecutionModel to an EngineReference.
 */
fun ExecutionModel.toEngineReference(): EngineReference {
    val metadata = getEngineMetadata() ?: return EngineReference.Manual
    val (engineId, expectedHash, expectedVersion) = metadata
    return EngineReference.WithEngine(
        engineId = engineId,
        expectedHash = expectedHash,
        expectedVersion = expectedVersion
    )
}

