package org.trustweave.credential.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.trustweave.core.util.decodeBase58
import org.trustweave.credential.internal.infrastructure.DefaultEd25519SignatureVerificationAdapter
import org.trustweave.credential.proof.internal.engines.ProofEngineUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.VerificationMethod
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Known-answer tests for JSON-LD canonicalization (RDFC-1.0/URDNA2015) against vectors
 * produced by INDEPENDENT implementations — not by this library.
 *
 * Vectors: W3C "Data Integrity EdDSA Cryptosuites v1.0" (vc-di-eddsa), section
 * "Representation: Ed25519Signature2020"
 * (https://www.w3.org/TR/vc-di-eddsa/#representation-ed25519signature2020).
 * See `src/test/resources/interop/README.md` for provenance and retrieval details.
 *
 * Every previous canonicalization test in this module was self-referential (the same
 * titanium-json-ld + titanium-rdfc pipeline on both sides). These tests prove byte-level
 * interoperability of [JsonLdUtils.canonicalizeDocument] — the exact code path used for
 * proof signing and verification — with the canonical N-Quads and SHA-256 hashes
 * published by the W3C working group.
 *
 * Normalization note: the ONLY normalization applied is line-ending handling of the
 * stored fixture files (CRLF→LF, in case of git/editor line-ending translation on
 * Windows). The SHA-256 assertions pin the exact canonical bytes — including the
 * trailing newline — to the hash values printed in the specification, so no byte
 * drift can hide behind fixture normalization.
 */
class CanonicalizationKnownAnswerTest {

    companion object {
        private const val VECTOR_DIR = "/interop/w3c-vc-di-eddsa"

        /** SHA-256 (hex) of the canonical credential document, per vc-di-eddsa Example 43. */
        private const val EXPECTED_DOCUMENT_HASH_HEX =
            "517744132ae165a5349155bef0bb0cf2258fff99dfe1dbd914b938d775a36017"

        /** SHA-256 (hex) of the canonical Ed25519Signature2020 proof options, per Example 46. */
        private const val EXPECTED_PROOF_OPTIONS_HASH_HEX =
            "04e14bcf5727cba0c0aa04a04d22a56fef915d5f8f7756bb92ae67cb1d0c4847"

        /** Raw Ed25519 signature (hex) over the combined hashes, per Example 48. */
        private const val EXPECTED_SIGNATURE_HEX =
            "cd8d023e8a9b462d563bbbd24c4499d8172738eb3f5235d74f65971e9be36dd7" +
                "f23a1e201791e9a6747e45b8fa877a984f51f591567365c4d8222ecad39be60c"

        /** Issuer key published with the spec test vectors (Example 40). */
        private const val SPEC_PUBLIC_KEY_MULTIBASE = "z6MkrJVnaZkeFzdQyMZu1cgjg7k1pZZ6pvBQ7XJPt4swbTQ2"
        private const val SPEC_DID = "did:key:$SPEC_PUBLIC_KEY_MULTIBASE"

        init {
            // The spec credential declares https://www.w3.org/ns/credentials/examples/v2,
            // which is not bundled with the main source set. Register the official context
            // document (see interop/README.md) so offline canonicalization can resolve it.
            JsonLdContextLoader.registerContext(
                "https://www.w3.org/ns/credentials/examples/v2",
                resourceText("/interop/contexts/credentials-examples-v2.jsonld")
            )
        }

        private fun resourceText(path: String): String =
            requireNotNull(CanonicalizationKnownAnswerTest::class.java.getResourceAsStream(path)) {
                "Missing test resource: $path"
            }.use { it.readBytes().toString(Charsets.UTF_8) }

        /** CRLF→LF only; see class KDoc. */
        private fun fixtureNQuads(name: String): String =
            resourceText("$VECTOR_DIR/$name").replace("\r\n", "\n")

        private fun fixtureJson(name: String): JsonObject =
            Json.parseToJsonElement(resourceText("$VECTOR_DIR/$name")).jsonObject

        private fun sha256Hex(data: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(data.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    // --- 1. Credential document canonicalization -----------------------------------------

    @Test
    fun `unsecured spec credential canonicalizes to the exact N-Quads published by W3C`() {
        val credential = fixtureJson("unsecured-credential.json")

        val canonical = JsonLdUtils.canonicalizeDocument(credential)

        assertEquals(
            fixtureNQuads("canonical-credential.nq"),
            canonical,
            "Canonical N-Quads must be byte-identical to the W3C vc-di-eddsa vector"
        )
        assertEquals(
            EXPECTED_DOCUMENT_HASH_HEX,
            sha256Hex(canonical),
            "SHA-256 of canonical document must match the hash printed in the spec"
        )
    }

    @Test
    fun `signed spec credential minus proof canonicalizes to the same N-Quads (verification-side document)`() {
        // Verification canonicalizes the received document without its proof node. The
        // secured document additionally declares the ed25519-2020/v1 suite context, which
        // must not change the credential's canonical statements.
        val withoutProof = JsonObject(fixtureJson("signed-credential.json").filterKeys { it != "proof" })

        val canonical = JsonLdUtils.canonicalizeDocument(withoutProof)

        assertEquals(fixtureNQuads("canonical-credential.nq"), canonical)
        assertEquals(EXPECTED_DOCUMENT_HASH_HEX, sha256Hex(canonical))
    }

    // --- 2. Proof options canonicalization ------------------------------------------------

    @Test
    fun `spec proof options document canonicalizes to the exact N-Quads published by W3C`() {
        val proofOptions = fixtureJson("proof-options.json")

        val canonical = JsonLdUtils.canonicalizeDocument(proofOptions)

        assertEquals(
            fixtureNQuads("canonical-proof-options.nq"),
            canonical,
            "Canonical proof-options N-Quads must be byte-identical to the W3C vector"
        )
        assertEquals(EXPECTED_PROOF_OPTIONS_HASH_HEX, sha256Hex(canonical))
    }

    @Test
    fun `proof options reconstructed the way verification does match the W3C canonical form`() {
        // Mirror VcLdProofEngine.verify: rebuild the proof options from the received
        // proof's fields (minus proofValue) via ProofEngineUtils.buildProofOptionsDocument,
        // then canonicalize. This proves the verification-side reconstruction — not just a
        // verbatim spec document — produces the spec's canonical bytes.
        val signed = fixtureJson("signed-credential.json")
        val proof = signed["proof"]!!.jsonObject

        val reconstructed = ProofEngineUtils.buildProofOptionsDocument(
            context = signed["@context"]!!.jsonArray.map { it.jsonPrimitive.content },
            proofType = proof["type"]!!.jsonPrimitive.content,
            created = proof["created"]!!.jsonPrimitive.content,
            verificationMethod = proof["verificationMethod"]!!.jsonPrimitive.content,
            proofPurpose = proof["proofPurpose"]!!.jsonPrimitive.content
        )
        val canonical = JsonLdUtils.canonicalizeDocument(reconstructed)

        assertEquals(fixtureNQuads("canonical-proof-options.nq"), canonical)
        assertEquals(EXPECTED_PROOF_OPTIONS_HASH_HEX, sha256Hex(canonical))
    }

    // --- 3. Payload composition + signature verification against the spec key -------------

    @Test
    fun `composed Data Integrity payload matches the spec's combined hash and verifies the spec signature`() {
        val signed = fixtureJson("signed-credential.json")
        val withoutProof = JsonObject(signed.filterKeys { it != "proof" })
        val proof = signed["proof"]!!.jsonObject

        val canonicalDocument = JsonLdUtils.canonicalizeDocument(withoutProof)
        val canonicalProofOptions = JsonLdUtils.canonicalizeDocument(
            ProofEngineUtils.buildProofOptionsDocument(
                context = signed["@context"]!!.jsonArray.map { it.jsonPrimitive.content },
                proofType = proof["type"]!!.jsonPrimitive.content,
                created = proof["created"]!!.jsonPrimitive.content,
                verificationMethod = proof["verificationMethod"]!!.jsonPrimitive.content,
                proofPurpose = proof["proofPurpose"]!!.jsonPrimitive.content
            )
        )

        val payload = ProofEngineUtils.composeDataIntegrityPayload(canonicalProofOptions, canonicalDocument)
        assertEquals(
            EXPECTED_PROOF_OPTIONS_HASH_HEX + EXPECTED_DOCUMENT_HASH_HEX,
            payload.joinToString("") { "%02x".format(it) },
            "Signing payload must be SHA-256(proof options) || SHA-256(document), matching Example 47"
        )

        // The spec's proofValue is multibase base58-btc ('z' prefix) of the raw signature.
        val proofValue = proof["proofValue"]!!.jsonPrimitive.content
        assertTrue(proofValue.startsWith("z"), "Ed25519Signature2020 proofValue must be multibase base58-btc")
        val signature = proofValue.substring(1).decodeBase58()
        assertEquals(EXPECTED_SIGNATURE_HEX, signature.joinToString("") { "%02x".format(it) })

        // Verify the spec's signature over OUR canonicalized payload through the module's
        // real Ed25519 verification adapter and key extraction (publicKeyMultibase).
        val did = Did(SPEC_DID)
        val verificationMethod = VerificationMethod(
            id = VerificationMethodId.parse("$SPEC_DID#$SPEC_PUBLIC_KEY_MULTIBASE", did),
            type = "Ed25519VerificationKey2020",
            controller = did,
            publicKeyMultibase = SPEC_PUBLIC_KEY_MULTIBASE
        )
        val adapter = DefaultEd25519SignatureVerificationAdapter()
        assertTrue(
            adapter.verify(payload, signature, verificationMethod, "Ed25519Signature2020"),
            "The W3C-published signature must verify over our canonicalized payload — " +
                "if this fails, our canonicalization or payload composition deviates from the spec"
        )

        // Tamper guard: a flipped payload byte must not verify.
        val tampered = payload.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFalse(adapter.verify(tampered, signature, verificationMethod, "Ed25519Signature2020"))
    }
}
