package org.trustweave.iondid

import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.trustweave.did.model.DidDocument
import org.trustweave.did.sidetree.SidetreeHttpClient
import org.trustweave.did.sidetree.SidetreeMethodSpec
import org.trustweave.did.sidetree.SidetreeOperationBuilder
import org.trustweave.did.sidetree.SidetreeP256KeyPair

/**
 * ION-specific Sidetree facade. All cryptographic and wire-protocol concerns
 * live in [:did:plugins:sidetree-core]; this class only wires the ION-flavoured
 * configuration ([SidetreeMethodSpec.ION] + the ION node URL) and exposes the
 * operations [IonDidMethod] needs.
 *
 * Replaces an earlier hand-rolled implementation that, like the original Orb
 * client, did not produce valid Sidetree update / deactivate operations.
 */
internal class SidetreeClient(
    httpClient: OkHttpClient,
    config: IonDidConfig,
) {

    private val builder = SidetreeOperationBuilder(SidetreeMethodSpec.ION)
    private val transport = SidetreeHttpClient(
        httpClient = httpClient,
        baseUrl = config.ionNodeUrl,
        methodSpec = SidetreeMethodSpec.ION,
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
