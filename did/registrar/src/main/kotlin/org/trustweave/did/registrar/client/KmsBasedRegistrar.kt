package org.trustweave.did.registrar.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.model.Action
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.DidState
import org.trustweave.did.registrar.model.KeyManagementMode
import org.trustweave.did.registrar.model.KeyMaterial
import org.trustweave.did.registrar.model.OperationState
import org.trustweave.did.registrar.model.Secret
import org.trustweave.did.registrar.model.UpdateDidOptions
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import org.trustweave.did.registrar.storage.JobStorage
import org.trustweave.kms.KeyManagementService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * KMS-based DID Registrar implementation for Internal Secret Mode.
 *
 * This registrar uses a Key Management Service (KMS) to generate and manage keys
 * internally, following the DID Registration specification's Internal Secret Mode.
 *
 * **Key Management Modes:**
 * - **Internal Secret Mode**: This registrar generates keys using KMS and can optionally
 *   return them to the client if `returnSecrets=true` is specified.
 * - **External Secret Mode**: Not supported by this registrar. Use a registrar that
 *   supports external wallet/KMS integration.
 *
 * **Job Storage:**
 * If a [JobStorage] is provided, operations will be tracked with a `jobId` and can be
 * queried later. This enables support for long-running operations and status polling.
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val jobStorage = InMemoryJobStorage() // or DatabaseJobStorage
 * val registrar = KmsBasedRegistrar(kms, jobStorage = jobStorage) { method, kms ->
 *     KeyDidMethod(kms) // any DidMethod implementation for `method`
 * }
 *
 * val response = registrar.createDid(
 *     method = "key",
 *     options = CreateDidOptions(
 *         keyManagementMode = KeyManagementMode.INTERNAL_SECRET,
 *         returnSecrets = true
 *     )
 * )
 *
 * // If jobId is present, can query status later
 * response.jobId?.let { jobId ->
 *     val status = jobStorage.get(jobId)
 * }
 * ```
 *
 * **DID methods:** every operation is delegated to the [org.trustweave.did.DidMethod] that
 * [didMethodFactory] returns for the DID method name; that method generates its keys through
 * [kms]. Without a factory, or when the method throws [UnsupportedOperationException], the
 * operation returns a `FAILED` state saying so: this registrar never fabricates a DID and never
 * reports an update or deactivation it did not perform. Method instances are created once per
 * method name and reused, so stateful methods see their own earlier operations.
 *
 * @param kms Key Management Service for key generation and management
 * @param jobStorage Optional storage for tracking long-running operations (default: InMemoryJobStorage)
 * @param didMethodFactory Creates the DID method implementation for a method name; required for
 *                        any operation to succeed
 */
class KmsBasedRegistrar(
    private val kms: KeyManagementService,
    private val jobStorage: JobStorage = InMemoryJobStorage(),
    private val didMethodFactory: ((String, KeyManagementService) -> org.trustweave.did.DidMethod)? = null,
) : DidRegistrar {
    private val methods = ConcurrentHashMap<String, org.trustweave.did.DidMethod>()

    private fun didMethodFor(method: String): org.trustweave.did.DidMethod? {
        methods[method]?.let { return it }
        val created = didMethodFactory?.invoke(method, kms) ?: return null
        return methods.putIfAbsent(method, created) ?: created
    }

    private fun noMethod(method: String): String =
        "KmsBasedRegistrar has no didMethodFactory, so it cannot perform did:$method operations " +
            "(it never fabricates identifiers or reports operations it did not perform)"

    private suspend fun respond(state: DidState): DidRegistrationResponse {
        val jobId = UUID.randomUUID().toString()
        val response = DidRegistrationResponse(jobId = jobId, didState = state)
        jobStorage.store(jobId, response)
        return response
    }

    private suspend fun failed(
        did: String?,
        reason: String,
    ): DidRegistrationResponse =
        respond(
            DidState(
                state = OperationState.FAILED,
                did = did,
                didDocument = null,
                reason = reason,
                action = Action(type = "error", description = reason),
            ),
        )

    override suspend fun createDid(
        method: String,
        options: CreateDidOptions,
    ): DidRegistrationResponse =
        withContext(Dispatchers.IO) {
            if (options.keyManagementMode == KeyManagementMode.EXTERNAL_SECRET) {
                return@withContext failed(
                    null,
                    "External Secret Mode not supported by KmsBasedRegistrar. " +
                        "Use a registrar that supports external wallet/KMS integration.",
                )
            }

            try {
                val didMethod = didMethodFor(method) ?: return@withContext failed(null, noMethod(method))
                // The DID method generates its own key through the KMS; generating one here as
                // well would leave an orphaned key in the KMS for every DID created.
                val algorithmName = extractAlgorithm(options)
                val algorithm =
                    KeyAlgorithm.fromName(algorithmName)
                        ?: return@withContext failed(null, "Unsupported key algorithm: $algorithmName")
                val didDocument =
                    didMethod.createDid(
                        DidCreationOptions(
                            algorithm = algorithm,
                            additionalProperties = extractKmsOptions(options),
                        ),
                    )

                respond(
                    DidState(
                        state = OperationState.FINISHED,
                        did = didDocument.id.value,
                        secret = if (options.returnSecrets) buildSecret(didDocument) else null,
                        didDocument = didDocument,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: UnsupportedOperationException) {
                failed(null, "did:$method does not support create: ${e.message ?: "not supported"}")
            } catch (e: Exception) {
                failed(null, "Failed to create DID: ${e.message ?: e::class.simpleName}")
            }
        }

    override suspend fun updateDid(
        did: String,
        document: DidDocument,
        options: UpdateDidOptions,
    ): DidRegistrationResponse =
        withContext(Dispatchers.IO) {
            val parsed = parseDid(did) ?: return@withContext failed(did, "Invalid DID: $did")
            if (document.id.value != did) {
                return@withContext failed(did, "Document id ${document.id.value} does not match DID $did")
            }

            try {
                val didMethod = didMethodFor(parsed.method) ?: return@withContext failed(did, noMethod(parsed.method))
                val updated = didMethod.updateDid(parsed) { document }
                respond(DidState(state = OperationState.FINISHED, did = did, didDocument = updated))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: UnsupportedOperationException) {
                failed(did, "did:${parsed.method} does not support update: ${e.message ?: "not supported"}")
            } catch (e: Exception) {
                failed(did, "Failed to update DID: ${e.message ?: e::class.simpleName}")
            }
        }

    override suspend fun deactivateDid(
        did: String,
        options: DeactivateDidOptions,
    ): DidRegistrationResponse =
        withContext(Dispatchers.IO) {
            val parsed = parseDid(did) ?: return@withContext failed(did, "Invalid DID: $did")

            try {
                val didMethod = didMethodFor(parsed.method) ?: return@withContext failed(did, noMethod(parsed.method))
                if (didMethod.deactivateDid(parsed)) {
                    respond(DidState(state = OperationState.FINISHED, did = did, didDocument = null))
                } else {
                    failed(did, "did:${parsed.method} did not deactivate $did (not found or already deactivated)")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: UnsupportedOperationException) {
                failed(did, "did:${parsed.method} does not support deactivate: ${e.message ?: "not supported"}")
            } catch (e: Exception) {
                failed(did, "Failed to deactivate DID: ${e.message ?: e::class.simpleName}")
            }
        }

    private fun parseDid(did: String): Did? =
        try {
            Did(did)
        } catch (e: IllegalArgumentException) {
            null
        }

    /**
     * Extracts algorithm from options.
     */
    private fun extractAlgorithm(options: CreateDidOptions): String =
        when (val algorithmElement = options.methodSpecificOptions["algorithm"]) {
            null -> "Ed25519"
            is JsonPrimitive -> algorithmElement.content
            else -> algorithmElement.toString()
        }

    /**
     * Converts method-specific options to plain Kotlin values for the DID method. JSON strings
     * become their content (not their quoted JSON form), and numbers and booleans keep their type.
     */
    private fun extractKmsOptions(options: CreateDidOptions): Map<String, Any?> =
        options.methodSpecificOptions
            .filterKeys { it != "algorithm" }
            .mapValues { (_, value) -> jsonToPlain(value) }

    private fun jsonToPlain(value: JsonElement): Any? =
        when (value) {
            is JsonNull -> null
            is JsonPrimitive ->
                if (value.isString) {
                    value.content
                } else {
                    value.booleanOrNull ?: value.intOrNull ?: value.longOrNull ?: value.doubleOrNull ?: value.content
                }
            else -> value.toString()
        }

    /**
     * Builds a Secret describing the keys of a created DID.
     *
     * KMS does not expose private keys, so `privateKeyJwk` is always null: the key material stays
     * in the KMS. Each entry names the verification method so callers know which KMS-held key
     * controls the DID.
     */
    private fun buildSecret(document: DidDocument): Secret =
        Secret(
            keys =
                document.verificationMethod.map { vm ->
                    KeyMaterial(
                        id = vm.id.value,
                        type = vm.type,
                        privateKeyJwk = null,
                        privateKeyMultibase = null,
                        additionalProperties =
                            mapOf(
                                "publicKeyMultibase" to (vm.publicKeyMultibase ?: ""),
                                "note" to "Private key retained by KMS; it is never exported",
                            ),
                    )
                },
        )
}
