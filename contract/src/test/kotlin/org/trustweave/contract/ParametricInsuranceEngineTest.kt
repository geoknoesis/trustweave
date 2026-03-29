package org.trustweave.contract

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import org.trustweave.contract.evaluation.EvaluationContext
import org.trustweave.contract.evaluation.engines.ParametricInsuranceEngine
import org.trustweave.contract.models.ConditionType
import org.trustweave.contract.models.ContractCondition
import org.trustweave.contract.models.ExecutionModel
import org.trustweave.contract.models.TriggerType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ParametricInsuranceEngineTest {

    private val engine = ParametricInsuranceEngine()

    private val context = EvaluationContext(
        contractId = "contract-test-001",
        executionModel = ExecutionModel.Parametric(
            triggerType = TriggerType.EarthObservation,
            evaluationEngine = "parametric-insurance"
        )
    )

    private fun condition(type: ConditionType, expression: String) = ContractCondition(
        id = "cond-1",
        description = "Test condition",
        conditionType = type,
        expression = expression
    )

    private fun data(vararg pairs: Pair<String, Double>): JsonObject = buildJsonObject {
        for ((k, v) in pairs) put(k, v)
    }

    // ── Engine metadata ──────────────────────────────────────────────────────

    @Test
    fun `engine ID is parametric-insurance`() {
        assertEquals("parametric-insurance", engine.engineId)
    }

    @Test
    fun `engine supports THRESHOLD, RANGE, and COMPARISON`() {
        assertTrue(engine.supportedConditionTypes.contains(ConditionType.THRESHOLD))
        assertTrue(engine.supportedConditionTypes.contains(ConditionType.RANGE))
        assertTrue(engine.supportedConditionTypes.contains(ConditionType.COMPARISON))
    }

    // ── THRESHOLD conditions ─────────────────────────────────────────────────

    @Test
    fun `threshold ge - true when value equals threshold`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.THRESHOLD, "$.floodDepthCm >= 50"),
            data("floodDepthCm" to 50.0),
            context
        )
        assertTrue(result)
    }

    @Test
    fun `threshold ge - true when value exceeds threshold`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.THRESHOLD, "$.floodDepthCm >= 50"),
            data("floodDepthCm" to 75.0),
            context
        )
        assertTrue(result)
    }

    @Test
    fun `threshold ge - false when value below threshold`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.THRESHOLD, "$.floodDepthCm >= 50"),
            data("floodDepthCm" to 25.0),
            context
        )
        assertFalse(result)
    }

    @Test
    fun `threshold le - true when value below or equal`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.THRESHOLD, "$.temperature <= 30"),
            data("temperature" to 30.0),
            context
        )
        assertTrue(result)
    }

    @Test
    fun `threshold eq - true for matching value`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.THRESHOLD, "$.rainfallMm == 100"),
            data("rainfallMm" to 100.0),
            context
        )
        assertTrue(result)
    }

    @Test
    fun `threshold ne - true when values differ`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.THRESHOLD, "$.rainfallMm != 100"),
            data("rainfallMm" to 50.0),
            context
        )
        assertTrue(result)
    }

    @Test
    fun `threshold gt - false when equal`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.THRESHOLD, "$.value > 10"),
            data("value" to 10.0),
            context
        )
        assertFalse(result)
    }

    @Test
    fun `threshold lt - true when below`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.THRESHOLD, "$.value < 10"),
            data("value" to 5.0),
            context
        )
        assertTrue(result)
    }

    // ── RANGE conditions ─────────────────────────────────────────────────────

    @Test
    fun `range - true when value within bounds`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.RANGE, "$.temperature >= 20 && $.temperature <= 30"),
            data("temperature" to 25.0),
            context
        )
        assertTrue(result)
    }

    @Test
    fun `range - true when value equals lower bound`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.RANGE, "$.temperature >= 20 && $.temperature <= 30"),
            data("temperature" to 20.0),
            context
        )
        assertTrue(result)
    }

    @Test
    fun `range - false when value below lower bound`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.RANGE, "$.temperature >= 20 && $.temperature <= 30"),
            data("temperature" to 15.0),
            context
        )
        assertFalse(result)
    }

    @Test
    fun `range - false when value above upper bound`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.RANGE, "$.temperature >= 20 && $.temperature <= 30"),
            data("temperature" to 35.0),
            context
        )
        assertFalse(result)
    }

    // ── COMPARISON conditions ────────────────────────────────────────────────

    @Test
    fun `comparison - true when left greater than right`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.COMPARISON, "$.currentValue > $.previousValue"),
            data("currentValue" to 200.0, "previousValue" to 100.0),
            context
        )
        assertTrue(result)
    }

    @Test
    fun `comparison - false when left equals right with gt operator`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.COMPARISON, "$.a > $.b"),
            data("a" to 100.0, "b" to 100.0),
            context
        )
        assertFalse(result)
    }

    @Test
    fun `comparison - true when values equal with eq operator`() = runTest {
        val result = engine.evaluateCondition(
            condition(ConditionType.COMPARISON, "$.a == $.b"),
            data("a" to 42.0, "b" to 42.0),
            context
        )
        assertTrue(result)
    }

    // ── evaluateConditions (batch) ───────────────────────────────────────────

    @Test
    fun `evaluateConditions returns results for all conditions`() = runTest {
        val conditions = listOf(
            ContractCondition("c1", "flood check", ConditionType.THRESHOLD, "$.floodDepth >= 50"),
            ContractCondition("c2", "temperature ok", ConditionType.THRESHOLD, "$.temp <= 35")
        )
        val inputData = buildJsonObject {
            put("floodDepth", 60.0)
            put("temp", 30.0)
        }
        val results = engine.evaluateConditions(conditions, inputData, context)
        assertEquals(2, results.size)
        assertTrue(results["c1"]!!)
        assertTrue(results["c2"]!!)
    }

    // ── Error cases ──────────────────────────────────────────────────────────

    @Test
    fun `unsupported condition type throws UnsupportedOperationException`() = runTest {
        assertFailsWith<UnsupportedOperationException> {
            engine.evaluateCondition(
                condition(ConditionType.TEMPORAL, "$.time > 100"),
                data("time" to 200.0),
                context
            )
        }
    }

    @Test
    fun `threshold missing path throws IllegalArgumentException`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            engine.evaluateCondition(
                condition(ConditionType.THRESHOLD, "$.missingPath >= 50"),
                data("other" to 10.0),
                context
            )
        }
    }

    @Test
    fun `range missing ampersand throws IllegalArgumentException`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            engine.evaluateCondition(
                condition(ConditionType.RANGE, "$.temperature >= 20"),
                data("temperature" to 25.0),
                context
            )
        }
    }

    @Test
    fun `comparison right side not a path throws IllegalArgumentException`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            engine.evaluateCondition(
                condition(ConditionType.COMPARISON, "$.a > 100"),
                data("a" to 50.0),
                context
            )
        }
    }
}
