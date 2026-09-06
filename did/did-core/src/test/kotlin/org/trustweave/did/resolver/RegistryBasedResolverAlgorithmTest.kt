package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.model.DidService
import org.trustweave.did.model.ServiceEndpoint
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolution.ResolutionOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegistryBasedResolverAlgorithmTest {
    private val did = Did("did:example:123456789abcdefghi")

    private class StubMethod(
        private val document: DidDocument,
    ) : DidMethod {
        override val method: String = "example"

        override suspend fun createDid(options: DidCreationOptions): DidDocument = document

        override suspend fun resolveDid(did: Did): DidResolutionResult = DidResolutionResult.Success(document)

        override suspend fun updateDid(
            did: Did,
            updater: (DidDocument) -> DidDocument,
        ): DidDocument = updater(document)

        override suspend fun deactivateDid(did: Did): Boolean = true
    }

    private fun resolverFor(document: DidDocument): RegistryBasedResolver {
        val registry = DidMethodRegistry()
        registry.register(StubMethod(document))
        return RegistryBasedResolver(registry)
    }

    @Test
    fun `an unregistered method yields METHOD_NOT_SUPPORTED`() =
        runBlocking<Unit> {
            val resolver = RegistryBasedResolver(DidMethodRegistry())
            val result = resolver.resolve(Did("did:nope:123"))
            assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, result.errorType)
        }

    @Test
    fun `invalid options yield INVALID_OPTIONS before the method is consulted`() =
        runBlocking<Unit> {
            val resolver = resolverFor(DidDocument(id = did))
            val options =
                ResolutionOptions(
                    versionId = "3",
                    versionTime = kotlinx.datetime.Instant.parse("2021-05-10T17:00:00Z"),
                )
            assertEquals(DidErrorType.INVALID_OPTIONS, resolver.resolve(did, options).errorType)
        }

    @Test
    fun `contradictory options plus an unsupported accept still yield INVALID_OPTIONS`() =
        runBlocking<Unit> {
            // Pins the step-4-before-step-3 ordering: without it, the unsupported `accept` below
            // would be reached first and report REPRESENTATION_NOT_SUPPORTED instead.
            val resolver = resolverFor(DidDocument(id = did))
            val options =
                ResolutionOptions(
                    versionId = "3",
                    versionTime = kotlinx.datetime.Instant.parse("2021-05-10T17:00:00Z"),
                    accept = "application/did+cbor",
                )
            assertEquals(DidErrorType.INVALID_OPTIONS, resolver.resolve(did, options).errorType)
        }

    @Test
    fun `an unsupported accept media type yields REPRESENTATION_NOT_SUPPORTED`() =
        runBlocking<Unit> {
            val resolver = resolverFor(DidDocument(id = did))
            val result = resolver.resolve(did, ResolutionOptions(accept = "application/did+cbor"))
            assertEquals(DidErrorType.REPRESENTATION_NOT_SUPPORTED, result.errorType)
        }

    @Test
    fun `a supported accept media type is echoed in contentType`() =
        runBlocking<Unit> {
            val resolver = resolverFor(DidDocument(id = did))
            val result = resolver.resolve(did, ResolutionOptions(accept = "application/did+ld+json"))
            assertEquals("application/did+ld+json", (result as DidResolutionResult.Success).resolutionMetadata.contentType)
        }

    @Test
    fun `without accept the method's own contentType is preserved`() =
        runBlocking<Unit> {
            // Deliberately returns a contentType other than the application/did default so the
            // assertion below only holds if the resolver leaves it alone rather than overwriting it.
            val registry = DidMethodRegistry()
            registry.register(
                object : DidMethod {
                    override val method: String = "example"

                    override suspend fun createDid(options: DidCreationOptions): DidDocument = DidDocument(id = did)

                    override suspend fun resolveDid(did: Did): DidResolutionResult =
                        DidResolutionResult.Success(
                            document = DidDocument(id = did),
                            resolutionMetadata = DidResolutionMetadata(contentType = "application/did+cbor-vendor"),
                        )

                    override suspend fun updateDid(
                        did: Did,
                        updater: (DidDocument) -> DidDocument,
                    ): DidDocument = throw UnsupportedOperationException()

                    override suspend fun deactivateDid(did: Did): Boolean = true
                },
            )
            val result = RegistryBasedResolver(registry).resolve(did) as DidResolutionResult.Success
            assertEquals("application/did+cbor-vendor", result.resolutionMetadata.contentType)
        }

    @Test
    fun `expandRelativeUrls rewrites relative service ids`() =
        runBlocking<Unit> {
            val document =
                DidDocument(
                    id = did,
                    service =
                        listOf(
                            DidService("#vcs", listOf("VerifiableCredentialService"), ServiceEndpoint.Url("https://example.com/vc/")),
                        ),
                )
            val resolver = resolverFor(document)
            val result = resolver.resolve(did, ResolutionOptions(expandRelativeUrls = true))
            assertEquals(
                "did:example:123456789abcdefghi#vcs",
                (result as DidResolutionResult.Success)
                    .document.service
                    .first()
                    .id,
            )
        }

    @Test
    fun `without the option relative service ids are left alone`() =
        runBlocking<Unit> {
            val document =
                DidDocument(
                    id = did,
                    service =
                        listOf(
                            DidService("#vcs", listOf("VerifiableCredentialService"), ServiceEndpoint.Url("https://example.com/vc/")),
                        ),
                )
            val result = resolverFor(document).resolve(did) as DidResolutionResult.Success
            assertEquals(
                "#vcs",
                result.document.service
                    .first()
                    .id,
            )
        }

    @Test
    fun `a document whose id does not match the requested DID is rejected`() =
        runBlocking<Unit> {
            val resolver = resolverFor(DidDocument(id = Did("did:example:someoneelse")))
            val result = resolver.resolve(did)
            assertEquals(DidErrorType.INVALID_DID_DOCUMENT, result.errorType)
        }

    @Test
    fun `an unexpected method failure yields INTERNAL_ERROR`() =
        runBlocking<Unit> {
            val registry = DidMethodRegistry()
            registry.register(
                object : DidMethod {
                    override val method: String = "example"

                    override suspend fun createDid(options: DidCreationOptions): DidDocument = throw UnsupportedOperationException()

                    override suspend fun resolveDid(did: Did): DidResolutionResult = error("boom")

                    override suspend fun updateDid(
                        did: Did,
                        updater: (DidDocument) -> DidDocument,
                    ): DidDocument = throw UnsupportedOperationException()

                    override suspend fun deactivateDid(did: Did): Boolean = false
                },
            )
            val result = RegistryBasedResolver(registry).resolve(did)
            assertEquals(DidErrorType.INTERNAL_ERROR, result.errorType)
            assertTrue(result is DidResolutionResult.Failure)
        }

    @Test
    fun `a deactivated DID returns Deactivated with no document exposed`() =
        runBlocking<Unit> {
            val registry = DidMethodRegistry()
            registry.register(
                object : DidMethod {
                    override val method: String = "example"

                    override suspend fun createDid(options: DidCreationOptions): DidDocument = DidDocument(id = did)

                    override suspend fun resolveDid(did: Did): DidResolutionResult =
                        DidResolutionResult.Success(
                            document = DidDocument(id = did),
                            documentMetadata = DidDocumentMetadata(deactivated = true),
                        )

                    override suspend fun updateDid(
                        did: Did,
                        updater: (DidDocument) -> DidDocument,
                    ): DidDocument = throw UnsupportedOperationException()

                    override suspend fun deactivateDid(did: Did): Boolean = true
                },
            )

            val result = RegistryBasedResolver(registry).resolve(did)

            assertTrue(result is DidResolutionResult.Deactivated)
            // Deactivated has no `document` property at all — this is a structural guarantee, not
            // just a runtime check, that a deactivated DID never exposes its (former) document.
            assertTrue((result as DidResolutionResult.Deactivated).documentMetadata.deactivated)
        }
}
