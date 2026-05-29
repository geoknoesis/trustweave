package org.trustweave.did.orb

import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.trustweave.did.model.DidDocument
import org.trustweave.did.sidetree.SidetreeHttpClient
import org.trustweave.did.sidetree.SidetreeMethodSpec
import org.trustweave.did.sidetree.SidetreeOperationBuilder
import org.trustweave.did.sidetree.SidetreeP256KeyPair

/**
 * Orb-specific Sidetree facade. All cryptographic and wire-protocol concerns
 * live in [:did:plugins:sidetree-core]; this class only wires the Orb-flavoured
 * configuration ([SidetreeMethodSpec.ORB], base URL, optional auth header) and
 * exposes the small set of operations [OrbDidMethod] needs.
 */
internal class SidetreeOrbClient(
    httpClient: OkHttpClient,
    config: OrbDidConfig,
) {

    private val methodSpec = SidetreeMethodSpec(
        namespace = "${config.namespace}:",
        operationsPath = SidetreeMethodSpec.ORB.operationsPath,
        identifiersPath = SidetreeMethodSpec.ORB.identifiersPath,
        // Orb requires every create operation to carry an `anchorOrigin` inside
        // `suffixData` so it can route witnesses; we default to the configured
        // base URL. Operators must include the same URL in Orb's `--allowed-origins`.
        suffixDataExtensionFields = mapOf("anchorOrigin" to (config.anchorOrigin ?: config.baseUrl)),
        // Recover operations require the same anchorOrigin inside the signedData
        // payload — the recovered DID keeps its anchor-origin association.
        recoverSignedDataExtensionFields = mapOf("anchorOrigin" to (config.anchorOrigin ?: config.baseUrl)),
    )

    private val builder = SidetreeOperationBuilder(methodSpec)

    /** Exposed for OrbDidMethod's suffix extraction; same instance as the builder used here. */
    internal val builderForExtraction: SidetreeOperationBuilder get() = builder
    private val transport = SidetreeHttpClient(
        httpClient = httpClient,
        baseUrl = config.baseUrl,
        methodSpec = methodSpec,
        authHeader = config.authHeader,
    )

    suspend fun buildCreateOperation(publicKeyJwk: Map<String, Any?>): SidetreeOperationBuilder.CreateOperationResult =
        builder.buildCreateOperation(publicKeyJwk)

    suspend fun buildUpdateOperation(
        did: String,
        updatedDocument: DidDocument,
        previousUpdateKeyPair: SidetreeP256KeyPair,
        nextUpdatePublicJwk: Map<String, Any?>,
    ): JsonObject = builder.buildUpdateOperation(did, updatedDocument, previousUpdateKeyPair, nextUpdatePublicJwk)

    suspend fun buildRecoverOperation(
        did: String,
        newDocument: org.trustweave.did.model.DidDocument,
        previousRecoveryKeyPair: SidetreeP256KeyPair,
        nextUpdatePublicJwk: Map<String, Any?>,
        nextRecoveryPublicJwk: Map<String, Any?>,
    ): JsonObject = builder.buildRecoverOperation(
        did,
        newDocument,
        previousRecoveryKeyPair,
        nextUpdatePublicJwk,
        nextRecoveryPublicJwk,
    )

    suspend fun buildDeactivateOperation(
        did: String,
        previousRecoveryKeyPair: SidetreeP256KeyPair,
    ): JsonObject = builder.buildDeactivateOperation(did, previousRecoveryKeyPair)

    fun generateP256KeyPair(): SidetreeP256KeyPair = SidetreeP256KeyPair.generate()

    suspend fun submitOperation(operation: JsonObject): SidetreeHttpClient.OperationResponse =
        transport.submitOperation(operation)

    suspend fun resolveDid(did: String): SidetreeHttpClient.ResolutionResponse =
        transport.resolveDid(did)
}
