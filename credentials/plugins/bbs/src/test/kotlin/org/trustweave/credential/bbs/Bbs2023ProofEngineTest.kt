package org.trustweave.credential.bbs

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.testkit.proof.ProofEngineTestData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Bbs2023ProofEngineTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private val issuerDid = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
    private val subjectDid = "did:key:z6MkiTBz1ymuepAQ4HEHYSF1H8quG5GLVVQR3djdX3mDooWp"

    private val testClaims = mapOf(
        "givenName" to JsonPrimitive("Alice"),
        "familyName" to JsonPrimitive("Smith"),
        "degree" to JsonPrimitive("BachelorOfScience"),
        "gpa" to JsonPrimitive("3.8"),
    )

    private fun buildEngine(
        keyPair: Bls12381KeyPair? = null,
        resolver: DidResolver? = null,
    ): Bbs2023ProofEngine {
        val properties = buildMap<String, Any> {
            if (keyPair != null) put("keyPair", keyPair)
        }
        return Bbs2023ProofEngine(ProofEngineConfig(properties = properties, didResolver = resolver))
    }

    /** Verification method ID the engine produces at issuance: `{issuer}#{keyId}`. */
    private fun vmIdFor(keyPair: Bls12381KeyPair, did: String = issuerDid): String =
        "$did#${keyPair.keyId}"

    /** Multibase (base58btc) encoding of the public key with the bls12_381-g2-pub multicodec prefix. */
    private fun multibaseFor(keyPair: Bls12381KeyPair): String =
        "z" + (byteArrayOf(0xEB.toByte(), 0x01) + keyPair.publicKeyBytes).encodeBase58()

    private fun didDocumentFor(
        did: String,
        vmId: String,
        keyPair: Bls12381KeyPair,
        includeVerificationMethod: Boolean = true,
        authorizeAssertion: Boolean = true,
        publicKeyJwk: Map<String, Any?>? = null,
    ): DidDocument {
        val verificationMethodId = VerificationMethodId.parse(vmId)
        return DidDocument(
            id = Did(did),
            verificationMethod = if (includeVerificationMethod) {
                listOf(
                    VerificationMethod(
                        id = verificationMethodId,
                        type = "Bls12381G2Key2020",
                        controller = Did(did),
                        publicKeyJwk = publicKeyJwk,
                        publicKeyMultibase = if (publicKeyJwk == null) multibaseFor(keyPair) else null,
                    ),
                )
            } else {
                emptyList()
            },
            assertionMethod = if (authorizeAssertion) listOf(verificationMethodId) else emptyList(),
        )
    }

    private fun resolverFor(vararg documents: DidDocument): DidResolver =
        DidResolver { did ->
            documents.firstOrNull { it.id.value == did.value }
                ?.let { DidResolutionResult.Success(it) }
                ?: DidResolutionResult.Failure.NotFound(did)
        }

    /** Resolver + engine wired so the issuer's DID document authorizes [keyPair] for assertions. */
    private fun engineWithIssuerResolver(keyPair: Bls12381KeyPair): Bbs2023ProofEngine {
        val document = didDocumentFor(issuerDid, vmIdFor(keyPair), keyPair)
        return buildEngine(keyPair, resolverFor(document))
    }

    private fun buildRequest(
        keyPair: Bls12381KeyPair,
        claims: Map<String, JsonPrimitive> = testClaims,
    ): IssuanceRequest =
        IssuanceRequest(
            format = ProofSuiteId.BBS_2023,
            issuer = Issuer.from(issuerDid),
            credentialSubject = CredentialSubject.fromIri(
                subjectDid,
                claims = claims,
            ),
            type = listOf(
                CredentialType.VerifiableCredential,
                CredentialType.Custom("UniversityDegreeCredential"),
            ),
        )

    // -------------------------------------------------------------------------
    // Key generation tests
    // -------------------------------------------------------------------------

    @Test
    fun `generateKeyPair produces valid key pair`() {
        val kp = BbsCryptoSuite.generateKeyPair("test-key-1")

        assertEquals("test-key-1", kp.keyId)
        assertEquals(96, kp.publicKeyBytes.size, "Public key should be 96 bytes (G2 compressed)")
        assertNotNull(kp.secretKeyBytes, "Secret key should be present")
        assertEquals(32, kp.secretKeyBytes!!.size, "Secret key should be 32 bytes")
        assertTrue(kp.publicKeyBase64.isNotEmpty(), "Base64 encoding should be non-empty")
    }

    @Test
    fun `generateKeyPair returns different keys each time`() {
        val kp1 = BbsCryptoSuite.generateKeyPair("key-a")
        val kp2 = BbsCryptoSuite.generateKeyPair("key-b")

        assertFalse(
            kp1.publicKeyBytes.contentEquals(kp2.publicKeyBytes),
            "Two distinct key pairs should have different public keys",
        )
    }

    // -------------------------------------------------------------------------
    // Issue tests
    // -------------------------------------------------------------------------

    @Test
    fun `issue returns credential with DataIntegrityProof`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-1")
        val engine = buildEngine(kp)
        val request = buildRequest(kp)

        val vc = engine.issue(request)

        assertNotNull(vc.proof, "Issued credential must have a proof")
        assertIs<CredentialProof.LinkedDataProof>(vc.proof, "Proof should be LinkedDataProof")

        val ldProof = vc.proof as CredentialProof.LinkedDataProof
        assertEquals("DataIntegrityProof", ldProof.type)
        assertTrue(ldProof.proofValue.isNotEmpty(), "Proof value should be non-empty")

        val cryptosuite = ldProof.additionalProperties["cryptosuite"]
        assertNotNull(cryptosuite, "cryptosuite property should be present")
        assertEquals("bbs-2023", (cryptosuite as JsonPrimitive).content)
    }

    @Test
    fun `issue credential has correct issuer and subject`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-2")
        val engine = buildEngine(kp)
        val request = buildRequest(kp)

        val vc = engine.issue(request)

        assertEquals(issuerDid, (vc.issuer as Issuer.IriIssuer).id.value)
        assertEquals(subjectDid, vc.credentialSubject.id!!.value)
    }

    @Test
    fun `issue with no configured key pair uses ephemeral key`() = runTest {
        val engine = buildEngine(keyPair = null)
        val request = IssuanceRequest(
            format = ProofSuiteId.BBS_2023,
            issuer = Issuer.from(issuerDid),
            credentialSubject = CredentialSubject.fromIri(subjectDid, claims = testClaims),
            type = listOf(CredentialType.VerifiableCredential),
        )
        // Should succeed without throwing
        val vc = engine.issue(request)
        assertNotNull(vc.proof)
    }

    @Test
    fun `issued credential preserves all claims`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-3")
        val engine = buildEngine(kp)
        val request = buildRequest(kp)

        val vc = engine.issue(request)

        assertEquals(4, vc.credentialSubject.claims.size)
        assertEquals("Alice", (vc.credentialSubject.claims["givenName"] as JsonPrimitive).content)
    }

    // -------------------------------------------------------------------------
    // Verify tests
    // -------------------------------------------------------------------------

    @Test
    fun `verify returns Valid for freshly issued credential with key resolved from issuer DID document`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-4")
        val engine = engineWithIssuerResolver(kp)
        val request = buildRequest(kp)

        val vc = engine.issue(request)
        val result = engine.verify(vc, VerificationOptions())

        assertIs<VerificationResult.Valid>(result, "Expected Valid but got $result")
    }

    @Test
    fun `verify returns Valid when issuer DID document carries the key as publicKeyJwk`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-jwk")
        val document = didDocumentFor(
            did = issuerDid,
            vmId = vmIdFor(kp),
            keyPair = kp,
            publicKeyJwk = mapOf(
                "kty" to "OKP",
                "crv" to "Bls12381G2",
                "x" to kp.publicKeyBase64Url,
            ),
        )
        val engine = buildEngine(kp, resolverFor(document))

        val vc = engine.issue(buildRequest(kp))
        val result = engine.verify(vc, VerificationOptions())

        assertIs<VerificationResult.Valid>(result, "Expected Valid but got $result")
    }

    @Test
    fun `verify returns InvalidProof for wrong format credential`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-5")
        val engine = buildEngine(kp)

        // Build a VC with a JWT proof (wrong type)
        val vc = ProofEngineTestData.createMinimalCredential(
            format = ProofSuiteId.BBS_2023,
            issuerDid = issuerDid,
            subjectDid = subjectDid,
            claims = mapOf("name" to "Bob"),
            proof = CredentialProof.JwtProof("eyJhbGciOiJFZERTQSJ9.e30.sig"),
        )

        val result = engine.verify(vc, VerificationOptions())
        assertIs<VerificationResult.Invalid.InvalidProof>(result)
    }

    @Test
    fun `verify returns InvalidProof for tampered claim`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-6")
        val engine = engineWithIssuerResolver(kp)
        val request = buildRequest(kp)

        val vc = engine.issue(request)

        // Tamper: replace one claim value
        val tamperedClaims = vc.credentialSubject.claims.toMutableMap().also {
            it["givenName"] = JsonPrimitive("Mallory")
        }
        val tamperedVc = vc.copy(
            credentialSubject = vc.credentialSubject.copy(claims = tamperedClaims),
        )

        val result = engine.verify(tamperedVc, VerificationOptions())
        assertIs<VerificationResult.Invalid.InvalidProof>(result, "Tampered credential should fail verification")
    }

    @Test
    fun `verify returns InvalidProof when cryptosuite is wrong`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-7")
        val engine = buildEngine(kp)
        val request = buildRequest(kp)

        val vc = engine.issue(request)
        val origProof = vc.proof as CredentialProof.LinkedDataProof
        val wrongCryptosuite = origProof.copy(
            additionalProperties = mapOf("cryptosuite" to JsonPrimitive("ecdsa-2019")),
        )
        val badVc = vc.copy(proof = wrongCryptosuite)

        val result = engine.verify(badVc, VerificationOptions())
        assertIs<VerificationResult.Invalid.InvalidProof>(result)
    }

    // -------------------------------------------------------------------------
    // Issuer key binding tests (P0: key must come from the issuer DID document,
    // never from the attacker-controlled proof)
    // -------------------------------------------------------------------------

    @Test
    fun `verify fails when proof verificationMethod is not rooted in the issuer DID`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-binding-1")
        // Attacker controls subjectDid and its DID document, which legitimately
        // authorizes the attacker's key — but the credential claims issuerDid.
        val attackerDoc = didDocumentFor(subjectDid, vmIdFor(kp, did = subjectDid), kp)
        val issuerDoc = didDocumentFor(issuerDid, vmIdFor(kp), kp)
        val engine = buildEngine(kp, resolverFor(issuerDoc, attackerDoc))

        val vc = engine.issue(buildRequest(kp))
        val proof = vc.proof as CredentialProof.LinkedDataProof
        val forged = vc.copy(
            proof = proof.copy(verificationMethod = vmIdFor(kp, did = subjectDid)),
        )

        val result = engine.verify(forged, VerificationOptions())
        val invalid = assertIs<VerificationResult.Invalid.InvalidProof>(
            result,
            "Proof bound to a foreign DID must be rejected even if that DID resolves",
        )
        assertTrue(
            "not rooted in" in invalid.reason,
            "Failure reason should explain the issuer binding violation, got: ${invalid.reason}",
        )
    }

    @Test
    fun `verify fails when issuer DID document does not contain the verification method`() = runTest {
        // Attacker signs with their own key, claiming issuerDid as issuer. The proof's
        // verificationMethod is rooted in issuerDid, but the real issuer DID document
        // only lists the legitimate issuer key.
        val attackerKp = BbsCryptoSuite.generateKeyPair("attacker-key")
        val legitimateKp = BbsCryptoSuite.generateKeyPair("issuer-key")
        val issuerDoc = didDocumentFor(issuerDid, vmIdFor(legitimateKp), legitimateKp)
        val engine = buildEngine(attackerKp, resolverFor(issuerDoc))

        val forged = engine.issue(buildRequest(attackerKp))

        val result = engine.verify(forged, VerificationOptions())
        val invalid = assertIs<VerificationResult.Invalid.InvalidIssuer>(
            result,
            "Credential signed with a key absent from the issuer DID document must be rejected",
        )
        assertTrue(
            "does not contain" in invalid.reason,
            "Failure reason should mention the missing verification method, got: ${invalid.reason}",
        )
    }

    @Test
    fun `verify fails when verification method is not referenced by assertionMethod`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-binding-3")
        val document = didDocumentFor(issuerDid, vmIdFor(kp), kp, authorizeAssertion = false)
        val engine = buildEngine(kp, resolverFor(document))

        val vc = engine.issue(buildRequest(kp))

        val result = engine.verify(vc, VerificationOptions())
        val invalid = assertIs<VerificationResult.Invalid.InvalidIssuer>(result)
        assertTrue(
            "assertionMethod" in invalid.reason,
            "Failure reason should mention the missing assertionMethod reference, got: ${invalid.reason}",
        )
    }

    @Test
    fun `verify fails closed when no DID resolver is configured`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-binding-4")
        val engine = buildEngine(kp, resolver = null)

        val vc = engine.issue(buildRequest(kp))

        val result = engine.verify(vc, VerificationOptions())
        val invalid = assertIs<VerificationResult.Invalid.InvalidIssuer>(
            result,
            "Verification without a resolver must fail closed, never trust proof-embedded keys",
        )
        assertTrue(
            "No DID resolver configured" in invalid.reason,
            "Failure reason should explain the missing resolver, got: ${invalid.reason}",
        )
    }

    @Test
    fun `verify fails when issuer DID does not resolve`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-binding-5")
        val engine = buildEngine(kp, resolverFor(/* no documents */))

        val vc = engine.issue(buildRequest(kp))

        val result = engine.verify(vc, VerificationOptions())
        assertIs<VerificationResult.Invalid.InvalidIssuer>(result)
    }

    @Test
    fun `verify rejects legacy proofs embedding the public key in the verificationMethod fragment`() = runTest {
        // Regression for the original exploit: an attacker mints a credential claiming
        // issuerDid and smuggles their own 96-byte public key into the fragment
        // (`#bbs-<base64url>`). The key bytes in the proof must never be trusted.
        val attackerKp = BbsCryptoSuite.generateKeyPair("legacy-attacker-key")
        val legitimateKp = BbsCryptoSuite.generateKeyPair("legacy-issuer-key")
        val issuerDoc = didDocumentFor(issuerDid, vmIdFor(legitimateKp), legitimateKp)
        val engine = buildEngine(attackerKp, resolverFor(issuerDoc))

        val vc = engine.issue(buildRequest(attackerKp))
        val proof = vc.proof as CredentialProof.LinkedDataProof
        val forged = vc.copy(
            proof = proof.copy(
                verificationMethod = "$issuerDid#bbs-${attackerKp.publicKeyBase64Url}",
            ),
        )

        val result = engine.verify(forged, VerificationOptions())
        assertIs<VerificationResult.Invalid>(
            result,
            "Proof-embedded key bytes must never be decoded and trusted",
        )
    }

    // -------------------------------------------------------------------------
    // Presentation / selective disclosure tests
    // -------------------------------------------------------------------------

    @Test
    fun `createPresentation with all claims disclosed succeeds`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-8")
        val engine = buildEngine(kp)
        val vc = engine.issue(buildRequest(kp))

        val request = PresentationRequest(disclosedClaims = null)  // null = all
        val vp = engine.createPresentation(listOf(vc), request)

        assertEquals(1, vp.verifiableCredential.size)
        // All 4 claims should still be present
        assertEquals(4, vp.verifiableCredential.first().credentialSubject.claims.size)
    }

    @Test
    fun `createPresentation with selective disclosure hides non-disclosed claims`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-9")
        val engine = buildEngine(kp)
        val vc = engine.issue(buildRequest(kp))

        // Disclose only givenName and degree
        val request = PresentationRequest(disclosedClaims = setOf("givenName", "degree"))
        val vp = engine.createPresentation(listOf(vc), request)

        val presentedClaims = vp.verifiableCredential.first().credentialSubject.claims
        assertEquals(
            setOf("givenName", "degree"),
            presentedClaims.keys,
            "Only disclosed claims should be present in the presentation",
        )
        assertFalse("familyName" in presentedClaims, "familyName should not be disclosed")
        assertFalse("gpa" in presentedClaims, "gpa should not be disclosed")
    }

    @Test
    fun `createPresentation derived proof does not contain original BBS signature`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-10")
        val engine = buildEngine(kp)
        val vc = engine.issue(buildRequest(kp))

        val originalProofValue = (vc.proof as CredentialProof.LinkedDataProof).proofValue

        val request = PresentationRequest(disclosedClaims = setOf("givenName"))
        val vp = engine.createPresentation(listOf(vc), request)

        val derivedProofValue =
            (vp.verifiableCredential.first().proof as CredentialProof.LinkedDataProof).proofValue

        assertFalse(
            derivedProofValue == originalProofValue,
            "Derived proof value should differ from the original BBS+ signature",
        )
    }

    @Test
    fun `createPresentation derived proof has bbs-2023-derived cryptosuite`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("bbs-key-11")
        val engine = buildEngine(kp)
        val vc = engine.issue(buildRequest(kp))

        val request = PresentationRequest(disclosedClaims = setOf("givenName"))
        val vp = engine.createPresentation(listOf(vc), request)

        val derivedProof =
            vp.verifiableCredential.first().proof as CredentialProof.LinkedDataProof
        val cryptosuite = (derivedProof.additionalProperties["cryptosuite"] as JsonPrimitive).content
        assertEquals("bbs-2023-derived", cryptosuite)
    }

    // -------------------------------------------------------------------------
    // Provider tests
    // -------------------------------------------------------------------------

    @Test
    fun `Bbs2023ProofEngineProvider creates engine for BBS_2023 format`() {
        val provider = Bbs2023ProofEngineProvider()

        assertEquals("bbs-2023", provider.name)
        assertTrue(ProofSuiteId.BBS_2023 in provider.supportedFormatIds)

        val engine = provider.create()
        assertNotNull(engine, "Provider should create a non-null engine")
        assertEquals(ProofSuiteId.BBS_2023, engine.format)
    }

    @Test
    fun `Bbs2023ProofEngineProvider passes keyPair and didResolver through options`() = runTest {
        val kp = BbsCryptoSuite.generateKeyPair("provider-key-1")
        val resolver = resolverFor(didDocumentFor(issuerDid, vmIdFor(kp), kp))
        val provider = Bbs2023ProofEngineProvider()
        val engine = provider.create(
            mapOf("keyPair" to kp, "didResolver" to resolver),
        ) as Bbs2023ProofEngine

        // Issue a credential to prove the key pair and resolver were wired in
        val request = buildRequest(kp)
        val vc = engine.issue(request)
        val result = engine.verify(vc, VerificationOptions())
        assertIs<VerificationResult.Valid>(result)
    }

    // -------------------------------------------------------------------------
    // BbsCryptoSuite unit tests
    // -------------------------------------------------------------------------

    @Test
    fun `hashToScalar is deterministic`() {
        val msg = "hello".toByteArray()
        val s1 = BbsCryptoSuite.hashToScalar(msg)
        val s2 = BbsCryptoSuite.hashToScalar(msg)
        assertEquals(s1, s2)
    }

    @Test
    fun `hashToScalar produces different scalars for different inputs`() {
        val s1 = BbsCryptoSuite.hashToScalar("hello".toByteArray())
        val s2 = BbsCryptoSuite.hashToScalar("world".toByteArray())
        assertFalse(s1 == s2)
    }

    @Test
    fun `sign and verify round-trips correctly`() {
        val kp = BbsCryptoSuite.generateKeyPair("round-trip-key")
        val messages = listOf("name=Alice".toByteArray(), "age=30".toByteArray())

        val sig = BbsCryptoSuite.sign(kp.secretKeyBytes!!, kp.publicKeyBytes, messages)
        assertEquals(BbsCryptoSuite.SIGNATURE_SIZE, sig.size, "BBS+ signature should be ${BbsCryptoSuite.SIGNATURE_SIZE} bytes")

        val verified = BbsCryptoSuite.verify(kp.publicKeyBytes, sig, messages)
        assertTrue(verified, "Signature should verify correctly with the same public key and messages")
    }

    @Test
    fun `verify returns false for different messages`() {
        val kp = BbsCryptoSuite.generateKeyPair("tamper-key")
        val messages = listOf("name=Alice".toByteArray(), "age=30".toByteArray())
        val sig = BbsCryptoSuite.sign(kp.secretKeyBytes!!, kp.publicKeyBytes, messages)

        val tampered = listOf("name=Mallory".toByteArray(), "age=30".toByteArray())
        val verified = BbsCryptoSuite.verify(kp.publicKeyBytes, sig, tampered)
        assertFalse(verified, "Signature should not verify for tampered messages")
    }

    @Test
    fun `deriveProof produces correct number of disclosed messages`() {
        val kp = BbsCryptoSuite.generateKeyPair("derive-key")
        val messages = listOf(
            "name=Alice".toByteArray(),
            "age=30".toByteArray(),
            "country=NL".toByteArray(),
        )
        val sig = BbsCryptoSuite.sign(kp.secretKeyBytes!!, kp.publicKeyBytes, messages)
        val derived = BbsCryptoSuite.deriveProof(
            signature = sig,
            publicKey = kp.publicKeyBytes,
            messages = messages,
            disclosed = setOf(0, 2),
        )

        assertEquals(2, derived.disclosedMessages.size)
        assertEquals(setOf(0, 2), derived.disclosedIndices)
        assertTrue(derived.disclosedMessages[0].contentEquals("name=Alice".toByteArray()))
        assertTrue(derived.disclosedMessages[1].contentEquals("country=NL".toByteArray()))
    }

    @Test
    fun `ProofSuiteId BBS_2023 has correct value`() {
        assertEquals("bbs-2023", ProofSuiteId.BBS_2023.value)
        assertEquals("bbs-2023", ProofSuiteId.BBS_2023.toString())
    }
}
