package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.trustweave.did.DidMethodResolver
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolution.ResolutionOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolverOptionsContractTest {

    private val did = Did("did:example:123456789abcdefghi")

    private val method = DidMethodResolver { requested ->
        DidResolutionResult.Success(document = DidDocument(id = requested))
    }

    @Test
    fun `empty options delegate to the single-argument form`() = runBlocking<Unit> {
        val result = method.resolveDid(did, ResolutionOptions.EMPTY)
        assertTrue(result is DidResolutionResult.Success)
    }

    @Test
    fun `method-independent options still delegate`() = runBlocking<Unit> {
        val result = method.resolveDid(did, ResolutionOptions(accept = "application/did"))
        assertTrue(result is DidResolutionResult.Success)
    }

    @Test
    fun `an unsupported versionId yields FEATURE_NOT_SUPPORTED`() = runBlocking<Unit> {
        val result = method.resolveDid(did, ResolutionOptions(versionId = "3"))
        assertEquals(DidErrorType.FEATURE_NOT_SUPPORTED, result.errorType)
    }

    @Test
    fun `an unsupported versionTime yields FEATURE_NOT_SUPPORTED`() = runBlocking<Unit> {
        val options = ResolutionOptions(versionTime = Instant.parse("2021-05-10T17:00:00Z"))
        assertEquals(DidErrorType.FEATURE_NOT_SUPPORTED, method.resolveDid(did, options).errorType)
    }

    @Test
    fun `an unsupported noCache yields FEATURE_NOT_SUPPORTED`() = runBlocking<Unit> {
        assertEquals(
            DidErrorType.FEATURE_NOT_SUPPORTED,
            method.resolveDid(did, ResolutionOptions(noCache = true)).errorType
        )
    }

    @Test
    fun `invalid options yield INVALID_OPTIONS ahead of feature support`() = runBlocking<Unit> {
        val options = ResolutionOptions(
            versionId = "3",
            versionTime = Instant.parse("2021-05-10T17:00:00Z")
        )
        assertEquals(DidErrorType.INVALID_OPTIONS, method.resolveDid(did, options).errorType)
    }

    @Test
    fun `a DidResolver gets the same default behaviour`() = runBlocking<Unit> {
        val resolver = DidResolver { requested -> DidResolutionResult.Success(DidDocument(id = requested)) }
        assertEquals(
            DidErrorType.FEATURE_NOT_SUPPORTED,
            resolver.resolve(did, ResolutionOptions(versionId = "3")).errorType
        )
    }
}
