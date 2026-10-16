package org.trustweave.did.registry

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.spi.DidMethodProvider
import java.util.concurrent.atomic.AtomicReference

/** SPI providers registered through src/test/resources, exercised by DidMethodRegistryAutoRegisterTest. */
class CovFakeMethod(
    override val method: String,
    val tag: String,
) : DidMethod {
    override suspend fun createDid(options: DidCreationOptions): DidDocument = DidDocument(Did("did:$method:$tag"))

    override suspend fun resolveDid(did: Did): DidResolutionResult = DidResolutionResult.Success(DidDocument(did))

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument = DidDocument(did)

    override suspend fun deactivateDid(did: Did): Boolean = true
}

object CovSpiProbe {
    val lastOptions = AtomicReference<DidCreationOptions?>(null)
}

class CovOkProvider : DidMethodProvider {
    override val name = "cov-ok"
    override val supportedMethods = listOf("covok")

    override fun create(
        methodName: String,
        options: DidCreationOptions,
    ): DidMethod? {
        CovSpiProbe.lastOptions.set(options)
        return CovFakeMethod(methodName, "ok")
    }
}

class CovNullProvider : DidMethodProvider {
    override val name = "cov-null"
    override val supportedMethods = listOf("covnull")

    override fun create(
        methodName: String,
        options: DidCreationOptions,
    ): DidMethod? = null
}

class CovThrowingProvider : DidMethodProvider {
    override val name = "cov-throwing"
    override val supportedMethods = listOf("covthrow")

    override fun create(
        methodName: String,
        options: DidCreationOptions,
    ): DidMethod? = throw IllegalStateException("boom")
}

class CovMissingEnvProvider : DidMethodProvider {
    override val name = "cov-env"
    override val supportedMethods = listOf("covenv")
    override val requiredEnvironmentVariables = listOf("TRUSTWEAVE_COV_SURELY_UNSET_VARIABLE")

    override fun create(
        methodName: String,
        options: DidCreationOptions,
    ): DidMethod? = CovFakeMethod(methodName, "env")
}

class CovLowPriorityDupProvider : DidMethodProvider {
    override val name = "cov-dup-low"
    override val supportedMethods = listOf("covdup")
    override val priority = -10

    override fun create(
        methodName: String,
        options: DidCreationOptions,
    ): DidMethod? = CovFakeMethod(methodName, "low")
}

class CovHighPriorityDupProvider : DidMethodProvider {
    override val name = "cov-dup-high"
    override val supportedMethods = listOf("covdup")
    override val priority = 10

    override fun create(
        methodName: String,
        options: DidCreationOptions,
    ): DidMethod? = CovFakeMethod(methodName, "high")
}
