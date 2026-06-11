package org.trustweave.credential.proof.internal.engines

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.internal.JsonLdContextLoader
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-stack interoperability test: verifies a credential that was SIGNED BY ANOTHER
 * IMPLEMENTATION through this module's real `Ed25519Signature2020` verification path.
 *
 * The fixture (`interop/digitalbazaar/signed-credential.json`) was produced by the
 * Digital Bazaar JavaScript stack (`@digitalbazaar/vc` 6.3.0 +
 * `@digitalbazaar/ed25519-signature-2020` 5.4.0, jsonld.js 9.0.0, rdf-canonize 5.0.0),
 * which shares no code with the titanium-json-ld/titanium-rdfc pipeline used here. The
 * signing key is the key pair published in the W3C vc-di-eddsa test vectors, with issuer
 * `did:key:z6MkrJVnaZkeFzdQyMZu1cgjg7k1pZZ6pvBQ7XJPt4swbTQ2`. See
 * `src/test/resources/interop/README.md` for full provenance (sources, dates, generator
 * script).
 *
 * A `Valid` outcome proves end-to-end agreement with an independent stack on:
 * JSON-LD expansion + RDFC-1.0 canonicalization (document AND reconstructed proof
 * options), Data Integrity payload composition (`SHA-256(options) || SHA-256(document)`),
 * multibase (base58-btc) `proofValue` decoding, did:key verification-method resolution,
 * `assertionMethod` authorization, and Ed25519 signature verification.
 */
class Ed25519Signature2020InteropTest {

    companion object {
        private const val ALUMNI_CONTEXT_URL = "https://example.org/contexts/alumni/v1"

        init {
            // The claim vocabulary declared by the fixture credential. The SAME bytes were
            // served to the Digital Bazaar stack when the fixture was signed (see README).
            JsonLdContextLoader.registerContext(
                ALUMNI_CONTEXT_URL,
                resourceText("/interop/contexts/trustweave-alumni-test-v1.jsonld")
            )
        }

        private fun resourceText(path: String): String =
            requireNotNull(Ed25519Signature2020InteropTest::class.java.getResourceAsStream(path)) {
                "Missing test resource: $path"
            }.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private val fixture: JsonObject =
        Json.parseToJsonElement(resourceText("/interop/digitalbazaar/signed-credential.json")).jsonObject

    /**
     * Map the wire-format fixture to the credential model used by the engine.
     *
     * Strict on purpose: any fixture key this mapping does not understand fails the test
     * instead of being silently dropped (a dropped field would weaken the interop claim,
     * because the engine re-canonicalizes the document from the model).
     */
    private fun parseFixture(json: JsonObject): VerifiableCredential {
        val allowedKeys = setOf("@context", "id", "type", "issuer", "issuanceDate", "credentialSubject", "proof")
        val unknown = json.keys - allowedKeys
        require(unknown.isEmpty()) { "Fixture has keys not mapped to the credential model: $unknown" }

        val subjectJson = json.getValue("credentialSubject").jsonObject
        val proofJson = json.getValue("proof").jsonObject
        val allowedProofKeys = setOf("type", "created", "verificationMethod", "proofPurpose", "proofValue")
        val unknownProof = proofJson.keys - allowedProofKeys
        require(unknownProof.isEmpty()) { "Fixture proof has unmapped keys: $unknownProof" }

        return VerifiableCredential(
            context = json.getValue("@context").jsonArray.map { it.jsonPrimitive.content },
            id = CredentialId(json.getValue("id").jsonPrimitive.content),
            type = json.getValue("type").jsonArray.map { CredentialType.fromString(it.jsonPrimitive.content) },
            issuer = Issuer.IriIssuer(Iri(json.getValue("issuer").jsonPrimitive.content)),
            issuanceDate = Instant.parse(json.getValue("issuanceDate").jsonPrimitive.content),
            credentialSubject = CredentialSubject(
                id = subjectJson["id"]?.let { Iri(it.jsonPrimitive.content) },
                claims = subjectJson.filterKeys { it != "id" }
            ),
            proof = CredentialProof.LinkedDataProof(
                type = proofJson.getValue("type").jsonPrimitive.content,
                created = Instant.parse(proofJson.getValue("created").jsonPrimitive.content),
                verificationMethod = proofJson.getValue("verificationMethod").jsonPrimitive.content,
                proofPurpose = proofJson.getValue("proofPurpose").jsonPrimitive.content,
                proofValue = proofJson.getValue("proofValue").jsonPrimitive.content
            )
        )
    }

    /**
     * Offline DID-document fixture for the external issuer's did:key, with the proof's
     * verification method authorized for `assertionMethod` (the engine enforces this).
     */
    private fun issuerEnvironment(credential: VerifiableCredential): Pair<DidDocument, DidResolver> {
        val issuerDidValue = (credential.issuer as Issuer.IriIssuer).id.value
        val issuerDid = Did(issuerDidValue)
        val multibaseKey = issuerDidValue.removePrefix("did:key:")
        val vmId = VerificationMethodId.parse(
            (credential.proof as CredentialProof.LinkedDataProof).verificationMethod,
            issuerDid
        )
        val document = DidDocument(
            id = issuerDid,
            verificationMethod = listOf(
                VerificationMethod(
                    id = vmId,
                    type = "Ed25519VerificationKey2020",
                    controller = issuerDid,
                    publicKeyMultibase = multibaseKey
                )
            ),
            assertionMethod = listOf(vmId),
            authentication = listOf(vmId)
        )
        val resolver = object : DidResolver {
            override suspend fun resolve(did: Did): DidResolutionResult {
                require(did.value == issuerDid.value) { "Unexpected DID resolution request: ${did.value}" }
                return DidResolutionResult.Success(document)
            }
        }
        return document to resolver
    }

    private fun engineFor(resolver: DidResolver) = VcLdProofEngine(
        config = ProofEngineConfig(didResolver = resolver)
    )

    // --- Positive: the externally signed credential must verify -------------------------

    @Test
    fun `credential signed by the Digital Bazaar stack verifies through the real engine path`() = runBlocking {
        val credential = parseFixture(fixture)
        val (_, resolver) = issuerEnvironment(credential)

        val result = engineFor(resolver).verify(credential, VerificationOptions())

        assertTrue(
            result is VerificationResult.Valid,
            "Externally signed Ed25519Signature2020 credential must verify; got " +
                "${result::class.simpleName}: ${(result as? VerificationResult.Invalid)?.errors}"
        )
        assertEquals(Iri("did:key:z6MkrJVnaZkeFzdQyMZu1cgjg7k1pZZ6pvBQ7XJPt4swbTQ2"), result.issuerIri)
    }

    // --- Negatives: any tampering must invalidate the external signature ----------------

    @Test
    fun `flipping a claim on the externally signed credential fails verification`() = runBlocking {
        val credential = parseFixture(fixture)
        val (_, resolver) = issuerEnvironment(credential)
        val tampered = credential.copy(
            credentialSubject = credential.credentialSubject.copy(
                claims = credential.credentialSubject.claims + ("alumniOf" to JsonPrimitive("The School of Forgers"))
            )
        )

        val result = engineFor(resolver).verify(tampered, VerificationOptions())

        assertTrue(
            result is VerificationResult.Invalid,
            "Tampered claim must fail verification, got ${result::class.simpleName}"
        )
    }

    @Test
    fun `rewriting the proof created timestamp fails verification`() = runBlocking {
        val credential = parseFixture(fixture)
        val (_, resolver) = issuerEnvironment(credential)
        val proof = credential.proof as CredentialProof.LinkedDataProof
        val tampered = credential.copy(
            proof = proof.copy(created = Instant.parse("2024-01-01T00:00:00Z"))
        )

        val result = engineFor(resolver).verify(tampered, VerificationOptions())

        assertTrue(
            result is VerificationResult.Invalid,
            "Tampered proof.created must fail verification (proof options are signed), " +
                "got ${result::class.simpleName}"
        )
    }
}
