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
    )

    private val builder = SidetreeOperationBuilder(methodSpec)
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
