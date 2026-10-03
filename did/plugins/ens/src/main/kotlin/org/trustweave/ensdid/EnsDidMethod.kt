package org.trustweave.ensdid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.base.AbstractBlockchainDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.ethrdid.EthrDidMethod
import org.trustweave.kms.KeyManagementService

/**
 * Implementation of did:ens method using ENS resolver integration.
 *
 * did:ens uses Ethereum Name Service (ENS) resolver with Ethereum DID documents:
 * - Format: `did:ens:{domain-name}` (e.g., "did:ens:example.eth")
 * - Resolves ENS names to Ethereum addresses, then resolves as did:ethr
 * - Integrates with ENS resolver for human-readable names
 *
 * **Not implemented:** the ENS name-to-address lookup is not implemented, so every resolution
 * currently fails with a `methodNotSupported` error ([NOT_IMPLEMENTED]). Creation, update and
 * deactivation are not part of did:ens and are refused.
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = EnsDidConfig.mainnet("https://eth-mainnet.g.alchemy.com/v2/KEY")
 * val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())
 * val method = EnsDidMethod(kms, anchorClient, config)
 *
 * // Resolve DID
 * val result = method.resolveDid("did:ens:example.eth")
 * ```
 */
class EnsDidMethod(
    kms: KeyManagementService,
    private val anchorClient: BlockchainAnchorClient,
    private val config: EnsDidConfig,
) : AbstractBlockchainDidMethod("ens", kms) {
    companion object {
        /** Error code for the ENS name-to-address step, which is not implemented. */
        const val NOT_IMPLEMENTED = "ENS_NOT_IMPLEMENTED"
    }

    // Delegate to EthrDidMethod for Ethereum DID resolution
    private val delegate: EthrDidMethod

    init {
        // Create EthrDidConfig from EnsDidConfig
        val ethrConfig =
            org.trustweave.ethrdid.EthrDidConfig(
                rpcUrl = config.rpcUrl,
                chainId = config.chainId,
                privateKey = config.privateKey,
                network = config.network ?: "mainnet",
                additionalProperties = config.additionalProperties,
            )

        delegate = EthrDidMethod(kms, anchorClient, ethrConfig)
    }

    override fun getBlockchainAnchorClient(): BlockchainAnchorClient = anchorClient

    override fun getChainId(): String = config.chainId

    override suspend fun canSubmitTransaction(): Boolean = config.privateKey != null

    override suspend fun findDocumentTxHash(did: String): String? {
        return null // ENS resolution doesn't use txHash directly
    }

    override suspend fun createDid(options: DidCreationOptions): DidDocument =
        withContext(Dispatchers.IO) {
            throw TrustWeaveException.Unknown(
                code = "NOT_SUPPORTED",
                message =
                    "did:ens does not support DID creation. " +
                        "Use ENS to register a domain name first, then resolve it as did:ens.",
            )
        }

    override suspend fun resolveDid(did: Did): DidResolutionResult =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                val didString = did.value
                // Extract ENS domain from did:ens
                val ensDomain = extractEnsDomain(didString)

                // Resolve ENS domain to Ethereum address
                val ethAddress = resolveEnsToAddress(ensDomain)

                // Resolve as did:ethr
                val ethrDidString = "did:ethr:$ethAddress"
                val ethrDid = Did(ethrDidString)
                val ethrResult = delegate.resolveDid(ethrDid)

                // Convert result to did:ens format
                when (ethrResult) {
                    is DidResolutionResult.Success -> {
                        val ethrDoc = ethrResult.document
                        val ensDocument = ethrDoc.copy(id = did)
                        storeDocument(ensDocument.id.value, ensDocument)

                        DidMethodUtils.createSuccessResolutionResult(
                            ensDocument,
                            method,
                            ethrResult.documentMetadata.created,
                            ethrResult.documentMetadata.updated,
                            retrieved = getLastFetched(ensDocument.id),
                        )
                    }
                    else -> {
                        DidMethodUtils.createErrorResolutionResult(
                            "notFound",
                            "ENS DID not found",
                            method,
                            didString,
                        )
                    }
                }
            } catch (e: TrustWeaveException) {
                // An unimplemented step is reported as such, not as a malformed DID.
                val error =
                    when {
                        e.code == NOT_IMPLEMENTED -> "methodNotSupported"
                        e is org.trustweave.did.exception.DidException.InvalidDidFormat -> "invalidDid"
                        else -> "internalError"
                    }
                DidMethodUtils.createErrorResolutionResult(error, e.message, method, did.value)
            } catch (e: IllegalArgumentException) {
                DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                DidMethodUtils.createErrorResolutionResult("internalError", e.message, method, did.value)
            }
        }

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument =
        withContext(Dispatchers.IO) {
            throw TrustWeaveException.Unknown(
                code = "NOT_SUPPORTED",
                message =
                    "did:ens does not support DID updates. " +
                        "Update the underlying did:ethr DID instead.",
            )
        }

    override suspend fun deactivateDid(did: Did): Boolean =
        withContext(Dispatchers.IO) {
            throw TrustWeaveException.Unknown(
                code = "NOT_SUPPORTED",
                message =
                    "did:ens does not support DID deactivation. " +
                        "Deactivate the underlying did:ethr DID instead.",
            )
        }

    /**
     * Extracts ENS domain from did:ens identifier.
     */
    private fun extractEnsDomain(did: String): String {
        val parsed =
            DidMethodUtils.parseDid(did)
                ?: throw IllegalArgumentException("Invalid DID format: $did")

        if (parsed.first != "ens") {
            throw IllegalArgumentException("Not a did:ens DID: $did")
        }

        return parsed.second
    }

    /**
     * Resolves an ENS domain to an Ethereum address.
     *
     * Not implemented: it needs the ENS registry/resolver contract calls (namehash, `resolver()`,
     * `addr()`), which this plugin does not make. Fails with [NOT_IMPLEMENTED], which
     * [resolveDid] reports as `methodNotSupported` rather than masking it as another error.
     */
    @Suppress("RedundantSuspendModifier")
    private suspend fun resolveEnsToAddress(ensDomain: String): String =
        throw TrustWeaveException.Unknown(
            code = NOT_IMPLEMENTED,
            message =
                "did:ens resolution is not implemented: resolving '$ensDomain' needs the ENS resolver " +
                    "contract (registry ${config.ensRegistryAddress}), which this plugin does not query",
        )
}
