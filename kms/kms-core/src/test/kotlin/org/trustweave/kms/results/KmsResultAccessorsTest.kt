package org.trustweave.kms.results

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.UnsupportedAlgorithmException
import org.trustweave.kms.exception.KmsException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KmsResultAccessorsTest {
    private val handle = KeyHandle(KeyId("k1"), "Ed25519")
    private val cause = IllegalStateException("backend")

    // GenerateKeyResult

    @Test
    fun `generate success exposes its handle and fold picks the success branch`() {
        val result: GenerateKeyResult = GenerateKeyResult.Success(handle)

        assertTrue(result.isSuccess && !result.isFailure)
        assertSame(handle, result.keyHandleOrNull)
        assertSame(handle, result.getOrThrow())
        assertEquals("ok:k1", result.fold({ "bad" }, { "ok:${it.id.value}" }))
    }

    @Test
    fun `generate failure variants map to distinct exceptions`() {
        val unsupported = GenerateKeyResult.Failure.UnsupportedAlgorithm(Algorithm.P384, setOf(Algorithm.Ed25519, Algorithm.P256))
        assertTrue(unsupported.reason!!.contains("'P-384' is not supported"), unsupported.reason)
        assertTrue(unsupported.reason!!.contains("Ed25519"))
        assertFailsWith<UnsupportedAlgorithmException> { unsupported.getOrThrow() }

        val explicit = GenerateKeyResult.Failure.UnsupportedAlgorithm(Algorithm.P384, emptySet(), null)
        assertEquals("Algorithm 'P-384' is not supported", assertFailsWith<UnsupportedAlgorithmException> { explicit.getOrThrow() }.message)

        val invalid = GenerateKeyResult.Failure.InvalidOptions(Algorithm.Ed25519, "bad option")
        assertEquals("bad option", assertFailsWith<IllegalArgumentException> { invalid.getOrThrow() }.message)
        assertTrue(invalid.invalidOptions.isEmpty())

        val duplicate = GenerateKeyResult.Failure.DuplicateKeyId(KeyId("dup"))
        assertEquals("Key with ID 'dup' already exists", assertFailsWith<IllegalArgumentException> { duplicate.getOrThrow() }.message)

        val error = GenerateKeyResult.Failure.Error(Algorithm.Ed25519, "hsm down", cause)
        val thrown = assertFailsWith<TrustWeaveException.Unknown> { error.getOrThrow() }
        assertEquals("hsm down", thrown.message)
        assertSame(cause, thrown.cause)
    }

    @Test
    fun `generate callbacks run only on the matching branch`() {
        val calls = mutableListOf<String>()
        val ok: GenerateKeyResult = GenerateKeyResult.Success(handle)
        val bad: GenerateKeyResult = GenerateKeyResult.Failure.DuplicateKeyId(KeyId("d"))

        ok.onSuccess { calls += "ok-success" }.onFailure { calls += "ok-failure" }
        bad.onSuccess { calls += "bad-success" }.onFailure { calls += "bad-failure" }

        assertEquals(listOf("ok-success", "bad-failure"), calls)
        assertNull(bad.keyHandleOrNull)
        assertEquals("fail", bad.fold({ "fail" }, { "ok" }))
    }

    // SignResult

    @Test
    fun `sign success compares by signature content`() {
        val a = SignResult.Success(byteArrayOf(1, 2))
        val b = SignResult.Success(byteArrayOf(1, 2))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == SignResult.Success(byteArrayOf(1, 3)))
        assertTrue(a.isSuccess)
        assertEquals(listOf<Byte>(1, 2), a.signatureOrNull!!.toList())
        assertEquals(2, a.getOrThrow().size)
    }

    @Test
    fun `sign failures map to the documented exceptions`() {
        val notFound = SignResult.Failure.KeyNotFound(KeyId("gone"))
        assertNull(notFound.reason)
        assertEquals("gone", assertFailsWith<KmsException.KeyNotFound> { notFound.getOrThrow() }.keyId)

        val withReason =
            SignResult.Failure.UnsupportedAlgorithm(KeyId("k"), Algorithm.P256, Algorithm.Ed25519, "mismatch")
        assertEquals("mismatch", assertFailsWith<UnsupportedAlgorithmException> { withReason.getOrThrow() }.message)

        val derived = SignResult.Failure.UnsupportedAlgorithm(KeyId("k"), Algorithm.P256, Algorithm.Ed25519)
        assertEquals(
            "Algorithm 'P-256' is not compatible with key algorithm 'Ed25519'",
            assertFailsWith<UnsupportedAlgorithmException> { derived.getOrThrow() }.message,
        )

        val noRequest = SignResult.Failure.UnsupportedAlgorithm(KeyId("k"), null, Algorithm.Ed25519)
        assertTrue(assertFailsWith<UnsupportedAlgorithmException> { noRequest.getOrThrow() }.message!!.contains("'null'"))

        val error = SignResult.Failure.Error(KeyId("k"), "timeout", cause)
        assertSame(cause, assertFailsWith<TrustWeaveException.Unknown> { error.getOrThrow() }.cause)
    }

    @Test
    fun `sign callbacks and fold choose by outcome`() {
        val failure: SignResult = SignResult.Failure.KeyNotFound(KeyId("x"))
        val success: SignResult = SignResult.Success(byteArrayOf(9))
        var failed: SignResult.Failure? = null
        var seen: ByteArray? = null

        failure.onFailure { failed = it }.onSuccess { error("must not run") }
        success.onSuccess { seen = it }.onFailure { error("must not run") }

        assertSame<Any?>(failure, failed)
        assertEquals(listOf<Byte>(9), seen!!.toList())
        assertNull(failure.signatureOrNull)
        assertTrue(failure.isFailure)
        assertEquals(1, success.fold({ 0 }, { it.size }))
        assertEquals(0, failure.fold({ 0 }, { it.size }))
    }

    // GetPublicKeyResult

    @Test
    fun `public key lookups map failures to exceptions`() {
        val found: GetPublicKeyResult = GetPublicKeyResult.Success(handle)
        assertSame(handle, found.getOrThrow())
        assertSame(handle, found.keyHandleOrNull)

        val missing = GetPublicKeyResult.Failure.KeyNotFound(KeyId("nope"))
        assertEquals("nope", assertFailsWith<KmsException.KeyNotFound> { missing.getOrThrow() }.keyId)
        assertNull((missing as GetPublicKeyResult).keyHandleOrNull)

        val error = GetPublicKeyResult.Failure.Error(KeyId("k"), "io", cause)
        assertSame(cause, assertFailsWith<TrustWeaveException.Unknown> { error.getOrThrow() }.cause)
    }

    @Test
    fun `public key callbacks and fold choose by outcome`() {
        val calls = mutableListOf<String>()
        val missing: GetPublicKeyResult = GetPublicKeyResult.Failure.KeyNotFound(KeyId("m"), "gone")
        val found: GetPublicKeyResult = GetPublicKeyResult.Success(handle)

        missing.onSuccess { calls += "s" }.onFailure { calls += "f:${(it as GetPublicKeyResult.Failure.KeyNotFound).reason}" }
        found.onSuccess { calls += "s:${it.id.value}" }.onFailure { calls += "f" }

        assertEquals(listOf("f:gone", "s:k1"), calls)
        assertEquals("none", missing.fold({ "none" }, { "some" }))
        assertEquals("some", found.fold({ "none" }, { "some" }))
    }

    // DeleteKeyResult

    @Test
    fun `delete treats not-found as a successful no-op and error as a failure`() {
        assertTrue(DeleteKeyResult.Deleted.isSuccess && DeleteKeyResult.Deleted.wasDeleted)
        assertTrue(DeleteKeyResult.NotFound.isSuccess)
        assertFalse(DeleteKeyResult.NotFound.wasDeleted)

        val error = DeleteKeyResult.Failure.Error(KeyId("k"), "denied", cause)
        assertTrue(error.isFailure && !error.isSuccess)

        assertTrue(DeleteKeyResult.Deleted.getOrThrow())
        assertFalse(DeleteKeyResult.NotFound.getOrThrow())
        assertEquals("denied", assertFailsWith<TrustWeaveException.Unknown> { error.getOrThrow() }.message)
    }

    @Test
    fun `delete callbacks and fold choose by outcome`() {
        val calls = mutableListOf<String>()
        val results: List<DeleteKeyResult> =
            listOf(DeleteKeyResult.Deleted, DeleteKeyResult.NotFound, DeleteKeyResult.Failure.Error(KeyId("k"), "x"))

        results.forEach { r -> r.onSuccess { calls += "s" }.onFailure { calls += "f" } }

        assertEquals(listOf("s", "s", "f"), calls)
        assertEquals(listOf("deleted:true", "deleted:false", "failed"), results.map { r -> r.fold({ "failed" }, { "deleted:$it" }) })
    }
}
