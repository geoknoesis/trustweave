package org.trustweave.trust.types

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.wallet.Wallet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class ResultUnwrappingTest {
    private val issuer = Did("did:key:z6MkIssuer")
    private val credential =
        VerifiableCredential(
            id = CredentialId("urn:vc:1"),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.fromDid(issuer),
            issuanceDate = Instant.parse("2025-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject.fromIri(Iri("did:key:z6MkSubject")),
        )
    private val at = Instant.parse("2025-06-01T00:00:00Z")

    private fun message(result: VerificationResult): String = assertFailsWith<IllegalStateException> { result.getOrThrow() }.message!!

    // VerificationResult

    @Test
    fun `a valid verification yields the credential`() {
        val valid = VerificationResult.Valid(credential, Iri(issuer.value), null, at, null)

        assertSame(credential, valid.getOrThrow())
        assertTrue(valid.valid)
        assertTrue(valid.proofValid && valid.issuerValid && valid.trustRegistryValid && valid.notExpired && valid.notRevoked)
    }

    @Test
    fun `each invalid outcome explains itself and carries its errors`() {
        val expired = message(VerificationResult.Invalid.Expired(credential, at, listOf("too old")))
        assertTrue(expired.contains("expired at $at") && expired.contains("too old"), expired)

        val revoked = message(VerificationResult.Invalid.Revoked(credential, at, listOf("listed")))
        assertTrue(revoked.contains("revoked at $at") && revoked.contains("listed"), revoked)
        assertTrue(message(VerificationResult.Invalid.Revoked(credential, null)).startsWith("Credential has been revoked. "))

        val suspended = message(VerificationResult.Invalid.Suspended(credential, at, errors = listOf("paused")))
        assertTrue(suspended.contains("suspended since $at") && suspended.contains("paused"), suspended)
        assertTrue(message(VerificationResult.Invalid.Suspended(credential)).startsWith("Credential is suspended. "))

        val proof = message(VerificationResult.Invalid.InvalidProof(credential, "bad signature", listOf("sig")))
        assertTrue(proof.contains("proof is invalid: bad signature") && proof.contains("sig"), proof)

        val issuerFailure = message(VerificationResult.Invalid.InvalidIssuer(credential, Iri(issuer.value), "unresolvable"))
        assertTrue(issuerFailure.contains("Failed to resolve issuer ${issuer.value}: unresolvable"), issuerFailure)
        assertTrue(issuerFailure.contains("TrustWeave.build"), "points at how to register the DID method")

        val untrusted = message(VerificationResult.Invalid.UntrustedIssuer(credential, issuer, listOf("no anchor")))
        assertTrue(untrusted.contains("Issuer ${issuer.value} is not trusted") && untrusted.contains("addAnchor"), untrusted)

        val early = message(VerificationResult.Invalid.NotYetValid(credential, at, listOf("early")))
        assertTrue(early.contains("not yet valid (valid from: $at)"), early)

        val format = message(VerificationResult.Invalid.UnsupportedFormat(credential, ProofSuiteId.VC_JWT, listOf("no engine")))
        assertTrue(format.contains("format 'vc-jwt' is not supported"), format)

        val schema =
            message(VerificationResult.Invalid.SchemaValidationFailed(credential, "s1", listOf("name required"), listOf("schema")))
        assertTrue(schema.contains("Schema validation failed") && schema.contains("skipSchema"), schema)

        val revocationCheck = message(VerificationResult.Invalid.RevocationCheckFailed(credential, "list offline", listOf("io")))
        assertTrue(revocationCheck.contains("Revocation check failed: list offline"), revocationCheck)
    }

    @Test
    fun `adapter-not-ready prefers explicit errors then the reason then a default`() {
        assertEquals("a; b", message(VerificationResult.Invalid.AdapterNotReady(credential, "r", listOf("a", "b"))))
        assertEquals("r", message(VerificationResult.Invalid.AdapterNotReady(credential, "r", emptyList())))
        assertEquals(
            "Credential verification service is not available.",
            message(VerificationResult.Invalid.AdapterNotReady(credential, null, emptyList())),
        )
    }

    @Test
    fun `multiple failures list every underlying error`() {
        val failures =
            listOf<VerificationResult.Invalid>(
                VerificationResult.Invalid.InvalidProof(credential, "p", listOf("proof broke")),
                VerificationResult.Invalid.Expired(credential, at, listOf("expired")),
            )
        val result = VerificationResult.Invalid.MultipleFailures(credential, failures, listOf("extra"))

        val text = message(result)
        assertTrue(text.contains("multiple errors") && text.contains("proof broke") && text.contains("expired"), text)
        assertEquals(listOf("proof broke", "expired", "extra"), result.allErrors)
    }

    @Test
    fun `the verification flags are false only for their own failure kind`() {
        val revoked = VerificationResult.Invalid.Revoked(credential, at)
        assertTrue(revoked.revoked)
        assertFalse(revoked.notRevoked)
        assertFalse(revoked.valid)
        assertTrue(revoked.notExpired)

        assertFalse(VerificationResult.Invalid.Expired(credential, at).notExpired)
        assertFalse(VerificationResult.Invalid.InvalidProof(credential, "x").proofValid)
        assertFalse(VerificationResult.Invalid.InvalidIssuer(credential, Iri(issuer.value), "x").issuerValid)
        assertFalse(VerificationResult.Invalid.UntrustedIssuer(credential, issuer).trustRegistryValid)
    }

    @Test
    fun `delegation and proof-purpose flags are derived from error text`() {
        val delegation = VerificationResult.Invalid.InvalidProof(credential, "x", listOf("Delegation chain broken"))
        assertFalse(delegation.delegationValid)
        assertTrue(delegation.proofPurposeValid)

        val capability = VerificationResult.Invalid.InvalidProof(credential, "x", listOf("missing CAPABILITY"))
        assertFalse(capability.delegationValid)

        val purpose = VerificationResult.Invalid.InvalidProof(credential, "x", listOf("wrong proofPurpose"))
        assertFalse(purpose.proofPurposeValid)
        assertFalse(VerificationResult.Invalid.InvalidProof(credential, "x", listOf("Proof Purpose mismatch")).proofPurposeValid)

        val clean = VerificationResult.Invalid.InvalidProof(credential, "x", listOf("unrelated"))
        assertTrue(clean.delegationValid && clean.proofPurposeValid)
        assertEquals(listOf("unrelated"), clean.errors)
        assertTrue(clean.warnings.isEmpty())
    }

    // DidCreationResult

    private val document = DidDocument(id = Did("did:key:z6MkNew"))

    private fun didMessage(failure: DidCreationResult) = assertFailsWith<IllegalStateException> { failure.getOrThrow() }.message!!

    @Test
    fun `successful DID creation can be taken apart`() {
        val ok = DidCreationResult.Success(document.id, document)

        assertEquals(document.id to document, ok.getOrThrow())
        assertEquals(document.id, ok.getOrThrowDid())
        assertSame(document, ok.getOrThrowDocument())
    }

    @Test
    fun `unregistered DID method suggests the available alternatives or registration`() {
        val withOthers = didMessage(DidCreationResult.Failure.MethodNotRegistered("web", listOf("key", "ion")))
        assertTrue(withOthers.contains("'web' is not registered") && withOthers.contains("key, ion"), withOthers)

        val none = didMessage(DidCreationResult.Failure.MethodNotRegistered("web", emptyList()))
        assertTrue(none.contains("No DID methods are registered"), none)
    }

    @Test
    fun `other DID creation failures name the cause of the failure`() {
        assertTrue(didMessage(DidCreationResult.Failure.KeyGenerationFailed("no entropy")).contains("Key generation failed: no entropy"))
        assertTrue(didMessage(DidCreationResult.Failure.DocumentCreationFailed("bad doc")).contains("document creation failed: bad doc"))

        val withDetails = didMessage(DidCreationResult.Failure.InvalidConfiguration("bad", mapOf("a" to 1, "b" to "x")))
        assertTrue(withDetails.contains("Invalid DID configuration: bad.") && withDetails.contains("Details: a=1; b=x"), withDetails)
        assertFalse(didMessage(DidCreationResult.Failure.InvalidConfiguration("bad")).contains("Details:"))

        val cause = RuntimeException("io")
        val other = assertFailsWith<IllegalStateException> { DidCreationResult.Failure.Other("boom", cause).getOrThrow() }
        assertEquals("DID creation failed: boom", other.message)
        assertSame(cause, other.cause)
    }

    @Test
    fun `DID creation with key surfaces the wrapped creation failure`() {
        assertEquals(document.id to "key-1", DidCreationWithKeyResult.Success(document.id, "key-1").getOrThrow())

        fun failure(f: DidCreationResult.Failure) =
            assertFailsWith<IllegalStateException> { DidCreationWithKeyResult.Failure.FromCreation(f).getOrThrow() }

        assertEquals(
            "DID method 'web' not registered. Available: key, ion",
            failure(DidCreationResult.Failure.MethodNotRegistered("web", listOf("key", "ion"))).message,
        )
        assertEquals("Key generation failed: r", failure(DidCreationResult.Failure.KeyGenerationFailed("r")).message)
        assertEquals("Document creation failed: r", failure(DidCreationResult.Failure.DocumentCreationFailed("r")).message)
        assertEquals("Invalid configuration: r", failure(DidCreationResult.Failure.InvalidConfiguration("r")).message)
        val cause = RuntimeException()
        val other = failure(DidCreationResult.Failure.Other("r", cause))
        assertEquals("DID creation failed: r", other.message)
        assertSame(cause, other.cause)

        val extraction =
            assertFailsWith<IllegalStateException> {
                DidCreationWithKeyResult.Failure.KeyExtractionFailed(document.id, "no vm", cause).getOrThrow()
            }
        assertEquals("Key extraction failed for ${document.id.value}: no vm", extraction.message)
        assertSame(cause, extraction.cause)
    }

    // Wallet

    private class StubWallet : Wallet {
        override val walletId = "w"

        override suspend fun store(credential: VerifiableCredential) = "id"

        override suspend fun get(credentialId: String): VerifiableCredential? = null

        override suspend fun list(filter: org.trustweave.wallet.CredentialFilter?) = emptyList<VerifiableCredential>()

        override suspend fun delete(credentialId: String) = false

        override suspend fun query(query: org.trustweave.wallet.CredentialQueryBuilder.() -> Unit) = emptyList<VerifiableCredential>()
    }

    @Test
    fun `wallet creation results unwrap or explain the failure`() {
        val wallet = StubWallet()
        assertSame(wallet, WalletCreationResult.Success(wallet).getOrThrow())

        fun failure(f: WalletCreationResult) = assertFailsWith<IllegalStateException> { f.getOrThrow() }

        assertEquals("Invalid holder DID 'x': malformed", failure(WalletCreationResult.Failure.InvalidHolderDid("x", "malformed")).message)
        val factory = failure(WalletCreationResult.Failure.FactoryNotConfigured("none")).message!!
        assertTrue(factory.startsWith("Wallet factory not configured: none.") && factory.contains("wallet { factory"), factory)

        val cause = RuntimeException("disk")
        val storage = failure(WalletCreationResult.Failure.StorageFailed("full", cause))
        assertTrue(storage.message!!.startsWith("Wallet storage failed: full."))
        assertSame(cause, storage.cause)

        val other = failure(WalletCreationResult.Failure.Other("weird", cause))
        assertEquals("Wallet creation failed: weird", other.message)
        assertSame(cause, other.cause)
    }

    // Presentation

    private val presentation =
        VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:key:z6MkHolder"),
            verifiableCredential = listOf(credential),
        )

    @Test
    fun `presentation success exposes the presentation and no errors`() {
        val ok = PresentationResult.Success(presentation)

        assertSame(presentation, ok.getOrThrow())
        assertSame(presentation, ok.presentationOrNull)
        assertTrue(ok.allErrors.isEmpty())
    }

    @Test
    fun `presentation failures throw invalid-state errors with stable codes`() {
        val cause = RuntimeException("io")

        val notReady =
            assertFailsWith<TrustWeaveException.InvalidState> {
                PresentationResult.Failure
                    .AdapterNotReady(
                        listOf("e1", "e2"),
                    ).getOrThrow()
            }
        assertEquals("PRESENTATION_ADAPTER_NOT_READY", notReady.code)
        assertEquals("e1; e2", notReady.message)
        assertEquals(listOf("e1", "e2"), notReady.context["errors"])

        val invalid =
            assertFailsWith<TrustWeaveException.InvalidState> { PresentationResult.Failure.InvalidRequest(listOf("bad")).getOrThrow() }
        assertEquals("PRESENTATION_INVALID_REQUEST", invalid.code)

        val adapter =
            assertFailsWith<TrustWeaveException.InvalidState> { PresentationResult.Failure.AdapterError("exploded", cause).getOrThrow() }
        assertEquals("PRESENTATION_ADAPTER_ERROR", adapter.code)
        assertEquals("exploded", adapter.message)
        assertSame(cause, adapter.cause)
    }

    @Test
    fun `presentation failure helpers expose errors and no presentation`() {
        val failure: PresentationResult = PresentationResult.Failure.InvalidRequest(listOf("x"), listOf("w"))

        assertNull(failure.presentationOrNull)
        assertEquals(listOf("x"), failure.allErrors)
        assertEquals(listOf("w"), (failure as PresentationResult.Failure).warnings)
    }

    @Test
    fun `the reason-only adapter-not-ready constructor falls back to guidance`() {
        assertEquals(listOf("custom"), PresentationResult.Failure.AdapterNotReady("custom").errors)
        assertTrue(
            PresentationResult.Failure
                .AdapterNotReady()
                .errors
                .single()
                .contains("TrustWeave.build"),
        )
    }

    // KeyIdResult

    @Test
    fun `key id results unwrap or rethrow with their cause`() {
        assertEquals("k", KeyIdResult.Success("k").getOrThrow())

        val cause = RuntimeException("x")
        val thrown = assertFailsWith<IllegalStateException> { KeyIdResult.Failure("no key", cause).getOrThrow() }
        assertEquals("no key", thrown.message)
        assertSame(cause, thrown.cause)
    }

    @Test
    fun `identity wrappers accept strings and DIDs alike`() {
        assertEquals(IssuerIdentity(Did("did:key:a")), IssuerIdentity("did:key:a"))
        assertEquals(VerifierIdentity(Did("did:key:a")), VerifierIdentity("did:key:a"))
        assertEquals(HolderIdentity(Did("did:key:a")), HolderIdentity("did:key:a"))
        assertFailsWith<IllegalArgumentException> { IssuerIdentity("not-a-did") }
    }
}
