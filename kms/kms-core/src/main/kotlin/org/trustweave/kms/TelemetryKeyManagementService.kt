package org.trustweave.kms

import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.telemetry.Operation
import org.trustweave.core.telemetry.Telemetry
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.SignResult

/**
 * Reports key generation and signing to [Telemetry], and delegates everything else unchanged.
 *
 * The SDK's instrumentation used to stop at the HTTP boundary, so a host could see that a request
 * was slow without seeing that the time went to an HSM round trip. Wrapping the configured service
 * closes that gap without every provider plugin having to carry instrumentation of its own.
 *
 * Kotlin interface delegation supplies every other member, so a method added to
 * [KeyManagementService] keeps working here rather than silently losing its implementation.
 *
 * ```kotlin
 * val kms = TelemetryKeyManagementService(AwsKeyManagementService(...))
 * ```
 *
 * Nothing is recorded until a host installs a sink. Key material and signed bytes are never
 * attached to an event; the algorithm is, because it is low-cardinality and useful.
 */
public class TelemetryKeyManagementService(
    private val delegate: KeyManagementService,
) : KeyManagementService by delegate {
    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>,
    ): GenerateKeyResult =
        Telemetry.measure(Operation.KMS_GENERATE_KEY, mapOf("kms.algorithm" to algorithm.name)) {
            delegate.generateKey(algorithm, options).also { result ->
                if (result is GenerateKeyResult.Failure) {
                    Telemetry.rejected(
                        Operation.KMS_GENERATE_KEY,
                        result::class.simpleName ?: "Failure",
                        mapOf("kms.algorithm" to algorithm.name),
                    )
                }
            }
        }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?,
    ): SignResult =
        Telemetry.measure(Operation.KMS_SIGN, mapOf("kms.algorithm" to (algorithm?.name ?: "key-default"))) {
            delegate.sign(keyId, data, algorithm).also { result ->
                if (result is SignResult.Failure) {
                    Telemetry.rejected(
                        Operation.KMS_SIGN,
                        result::class.simpleName ?: "Failure",
                        mapOf("kms.algorithm" to (algorithm?.name ?: "key-default")),
                    )
                }
            }
        }
}

/** Wraps this service so its key generation and signing are reported to [Telemetry]. */
public fun KeyManagementService.withTelemetry(): KeyManagementService =
    if (this is TelemetryKeyManagementService) this else TelemetryKeyManagementService(this)
