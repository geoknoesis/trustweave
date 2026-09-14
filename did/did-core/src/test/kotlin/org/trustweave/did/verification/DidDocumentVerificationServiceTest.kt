package org.trustweave.did.verification

import kotlinx.coroutines.runBlocking
import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidService
import org.trustweave.did.model.ServiceEndpoint
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionMetadata
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Document verification, which decides whether a resolved document can be believed.
 *
 * Two things here are easy to get backwards and only a test holds them in place. A malformed
 * *verification method* is an error, because it is what signatures are checked against; a
 * malformed *service* is a warning, because a broken service endpoint does not make the keys
 * untrustworthy. And every unknown case — an unimplemented method, an unsupported key type, a
 * digest that cannot be decoded — has to fail closed, because "we could not check" must never
 * read the same as "we checked and it was fine".
 */
class DidDocumentVerificationServiceTest {
    private val did = Did("did:key:z6MkTest")

    private fun service(
        conformant: Boolean = true,
        digest: String = "uAAAA",
        failWith: Throwable? = null,
    ) = DefaultDidDocumentVerificationService(FakeCanonicalization(conformant, digest, failWith))

    private class FakeCanonicalization(
        override val isConformant: Boolean,
        private val digest: String,
        private val failWith: Throwable?,
    ) : DidDocumentCanonicalizationService {
        override suspend fun canonicalize(document: DidDocument): String = "canonical"

        override suspend fun computeDigest(document: DidDocument): String {
            failWith?.let { throw it }
            return digest
        }

        override suspend fun normalize(document: DidDocument): DidDocument = document
    }

    private fun method(
        fragment: String = "key-1",
        type: String = "Ed25519VerificationKey2020",
        multibase: String? = "z6MkTest",
    ) = VerificationMethod(
        id = VerificationMethodId(did = did, keyId = KeyId(fragment)),
        type = type,
        controller = did,
        publicKeyMultibase = multibase,
    )

    private fun document(
        id: Did = did,
        methods: List<VerificationMethod> = listOf(method()),
        services: List<DidService> = emptyList(),
    ) = DidDocument(id = id, verificationMethod = methods, service = services)

    private fun metadata(digest: String? = null) = DidResolutionMetadata(properties = digest?.let { mapOf("digest" to it) }.orEmpty())

    // ---------------------------------------------------------------- structure

    @Test
    fun `a well-formed document verifies`() =
        runBlocking<Unit> {
            val result = service().verifyDocument(document(), metadata())
            assertTrue(result.valid, result.errors.toString())
            assertTrue(result.errors.isEmpty())
        }

    @Test
    fun `a verification method with no key material is an error`() =
        runBlocking<Unit> {
            // It is what signatures get checked against, so a method nobody can use is not a
            // cosmetic problem with the document.
            val result = service().verifyDocument(document(methods = listOf(method(multibase = null))), metadata())
            assertFalse(result.valid)
            assertTrue(result.errors.any { "verification method" in it.lowercase() }, result.errors.toString())
        }

    @Test
    fun `a verification method with no type is an error`() =
        runBlocking<Unit> {
            val result = service().verifyDocument(document(methods = listOf(method(type = ""))), metadata())
            assertFalse(result.valid)
        }

    @Test
    fun `a malformed service is a warning, not an error`() =
        runBlocking<Unit> {
            // A broken service endpoint does not make the document's keys untrustworthy, so it
            // must not invalidate a document whose verification methods are sound.
            val broken = DidService(id = "", type = listOf("LinkedDomains"), serviceEndpoint = ServiceEndpoint.Url("https://x"))
            val result = service().verifyDocument(document(services = listOf(broken)), metadata())

            assertTrue(result.valid, "a bad service must not invalidate the document: ${result.errors}")
            assertTrue(result.warnings.any { "service" in it.lowercase() }, result.warnings.toString())
        }

    @Test
    fun `a service with no type is also only a warning`() =
        runBlocking<Unit> {
            val broken = DidService(id = "#svc", type = emptyList(), serviceEndpoint = ServiceEndpoint.Url("https://x"))
            val result = service().verifyDocument(document(services = listOf(broken)), metadata())
            assertTrue(result.valid)
            assertTrue(result.warnings.isNotEmpty())
        }

    @Test
    fun `every malformed method is reported, not just the first`() =
        runBlocking<Unit> {
            val result =
                service().verifyDocument(
                    document(methods = listOf(method("a", multibase = null), method("b", type = ""))),
                    metadata(),
                )
            assertTrue(result.errors.size >= 2, "each bad method should be named: ${result.errors}")
        }

    // ---------------------------------------------------------------- digest integrity

    @Test
    fun `a matching digest verifies`() =
        runBlocking<Unit> {
            val digest = multibase(byteArrayOf(1, 2, 3))
            val result = service(digest = digest).verifyDocument(document(), metadata(digest))
            assertTrue(result.valid, result.errors.toString())
        }

    @Test
    fun `a digest mismatch is an error`() =
        runBlocking<Unit> {
            val result =
                service(digest = multibase(byteArrayOf(1, 2, 3)))
                    .verifyDocument(document(), metadata(multibase(byteArrayOf(9, 9, 9))))
            assertFalse(result.valid)
            assertTrue(result.errors.any { "mismatch" in it.lowercase() }, result.errors.toString())
        }

    @Test
    fun `a non-conformant canonicalizer skips the digest check with a warning rather than failing it`() =
        runBlocking<Unit> {
            // Running the comparison with a non-conformant implementation would report a mismatch
            // against every external resolver, which is worse than not checking: it would train
            // an operator to ignore the error.
            val result =
                service(conformant = false, digest = multibase(byteArrayOf(1)))
                    .verifyDocument(document(), metadata(multibase(byteArrayOf(9))))

            assertTrue(result.valid, "a skipped check is not a failed one: ${result.errors}")
            assertTrue(result.warnings.any { "skipped" in it.lowercase() }, result.warnings.toString())
        }

    @Test
    fun `a digest that cannot be decoded is an error rather than a raw string comparison`() =
        runBlocking<Unit> {
            // Falling back to comparing the encoded strings would make an unsupported encoding
            // look like a mismatch, or worse, accidentally match.
            val result = service(digest = multibase(byteArrayOf(1))).verifyDocument(document(), metadata("%unsupported"))
            assertFalse(result.valid)
            assertTrue(result.errors.any { "encoding" in it.lowercase() }, result.errors.toString())
            assertFalse(result.errors.any { "mismatch" in it.lowercase() }, "one cause, one error: ${result.errors}")
        }

    @Test
    fun `a digest computation that throws is reported without propagating`() =
        runBlocking<Unit> {
            val result =
                service(failWith = IllegalStateException("canonicalization exploded"))
                    .verifyDocument(document(), metadata(multibase(byteArrayOf(1))))
            assertFalse(result.valid)
            assertTrue(result.errors.any { "computation failed" in it.lowercase() }, result.errors.toString())
            assertFalse(result.errors.any { "mismatch" in it.lowercase() }, "a failure is not a mismatch")
        }

    @Test
    fun `no digest in the metadata means no digest check at all`() =
        runBlocking<Unit> {
            val result = service(failWith = IllegalStateException("must not be called")).verifyDocument(document(), metadata())
            assertTrue(result.valid, result.errors.toString())
            assertTrue(result.warnings.isEmpty())
        }

    // ---------------------------------------------------------------- signatures, all fail-closed

    @Test
    fun `a method that does not match the document's DID is refused`() =
        runBlocking<Unit> {
            assertFalse(service().verifyDocumentSignature(document(), method = "web"))
        }

    @Test
    fun `an unknown DID method cannot be verified and says no`() =
        runBlocking<Unit> {
            val ionDoc = document(id = Did("did:unknown:abc"))
            assertFalse(service().verifyDocumentSignature(ionDoc, method = "unknown"))
        }

    @Test
    fun `ion signature verification is not implemented and fails closed`() =
        runBlocking<Unit> {
            // Recorded rather than endorsed: until the proof chain is implemented, a did:ion
            // document must not verify. A stub returning true here would be a silent bypass.
            val ionDoc = document(id = Did("did:ion:abc"))
            assertFalse(service().verifyDocumentSignature(ionDoc, method = "ion"))
        }

    @Test
    fun `an unsupported key type cannot verify a signature`() =
        runBlocking<Unit> {
            assertFalse(
                service().verifyVerificationMethod(
                    method = method(type = "UnsupportedKey2099"),
                    signature = "sig",
                    data = byteArrayOf(1),
                ),
            )
        }

    @Test
    fun `a malformed signature on a supported key type is refused rather than throwing`() =
        runBlocking<Unit> {
            assertFalse(
                service().verifyVerificationMethod(
                    method = method(),
                    signature = "not-base64-!!",
                    data = byteArrayOf(1),
                ),
            )
        }

    private fun multibase(bytes: ByteArray) = "u" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
