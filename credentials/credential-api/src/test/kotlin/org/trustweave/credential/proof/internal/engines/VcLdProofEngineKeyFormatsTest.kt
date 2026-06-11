package org.trustweave.credential.proof.internal.engines

import org.trustweave.core.identifiers.Iri
import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.util.encodeBase58
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.internal.JsonLdContextLoader
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.proof.proofOptions
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineConfig
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import org.bouncycastle.jce.provider.BouncyCastleProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Provider
import java.security.Security
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trip tests for verification-method key formats and JWS algorithms:
 *
 * - **Finding 17**: `publicKeyMultibase` verification methods (z6Mk... did:key style) must
 *   verify — multibase decode, multicodec `ed25519-pub` prefix stripping, Ed25519 key
 *   construction; wrong multicodec prefixes fail closed.
 * - **Finding 13**: the JsonWebSignature2020 (detached JWS) path must embed ECDSA
 *   signatures in IEEE P1363 form. A KMS whose backend emits ASN.1 DER (JCA, AWS KMS, ...)
 *   is transcoded before the signature segment is base64url-embedded. ES256K (secp256k1)
 *   is supported for sign and verify via Bouncy Castle.
 */
class VcLdProofEngineKeyFormatsTest {

    companion object {
        private const val TEST_CONTEXT_URL = "https://trustweave.example/contexts/key-formats-claims/v1"

        init {
            JsonLdContextLoader.registerContext(
                TEST_CONTEXT_URL,
                """
                {
                  "@context": {
                    "name": "https://schema.org/name"
                  }
                }
                """.trimIndent()
            )
        }

        private fun bouncyCastle(): Provider {
            Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)?.let { return it }
            val provider = BouncyCastleProvider()
            Security.addProvider(provider)
            return provider
        }
    }

    // --- Finding 17: publicKeyMultibase verification methods ----------------------------

    @Test
    fun `credential verifies when issuer DID document only carries publicKeyMultibase`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val issuerDocument = didMethod.createDid()

        // Replace the JWK with the equivalent multibase value:
        // base58btc(multicodec ed25519-pub 0xED 0x01 || raw 32-byte key) — the encoding used
        // by did:key / Ed25519VerificationKey2020 documents (z6Mk... values).
        val rawPublicKey = Base64.getUrlDecoder().decode(
            issuerDocument.verificationMethod.first().publicKeyJwk!!["x"] as String
        )
        val multibase = "z" + (byteArrayOf(0xED.toByte(), 0x01) + rawPublicKey).encodeBase58()
        assertTrue(multibase.startsWith("z6Mk"), "ed25519-pub multibase values start with z6Mk, got $multibase")
        didMethod.updateDid(issuerDocument.id) { document ->
            document.copy(
                verificationMethod = document.verificationMethod.map {
                    it.copy(publicKeyJwk = null, publicKeyMultibase = multibase)
                }
            )
        }

        val engine = VcLdProofEngine(
            config = ProofEngineConfig(
                properties = mapOf("kms" to kms),
                didResolver = object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                }
            )
        )

        val credential = engine.issue(
            issuanceRequest(
                issuerDid = issuerDocument.id,
                issuerKeyId = issuerDocument.verificationMethod.first().id
            )
        )

        val result = engine.verify(credential, VerificationOptions())
        assertTrue(
            result is VerificationResult.Valid,
            "Verification against a publicKeyMultibase verification method must succeed, got " +
                "${result::class.simpleName}: " +
                ((result as? VerificationResult.Invalid)?.errors ?: emptyList<String>())
        )
    }

    @Test
    fun `credential verification fails closed when multibase carries a non-ed25519 multicodec`() = runBlocking {
        val kms = InMemoryKeyManagementService()
        val didMethod = DidKeyMockMethod(kms)
        val issuerDocument = didMethod.createDid()

        val rawPublicKey = Base64.getUrlDecoder().decode(
            issuerDocument.verificationMethod.first().publicKeyJwk!!["x"] as String
        )
        // 0xE7 0x01 is the multicodec secp256k1-pub prefix: the key bytes are genuine, but the
        // declared key type is wrong — extraction must fail closed.
        val multibase = "z" + (byteArrayOf(0xE7.toByte(), 0x01) + rawPublicKey).encodeBase58()
        didMethod.updateDid(issuerDocument.id) { document ->
            document.copy(
                verificationMethod = document.verificationMethod.map {
                    it.copy(publicKeyJwk = null, publicKeyMultibase = multibase)
                }
            )
        }

        val engine = VcLdProofEngine(
            config = ProofEngineConfig(
                properties = mapOf("kms" to kms),
                didResolver = object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult = didMethod.resolveDid(did)
                }
            )
        )

        val credential = engine.issue(
            issuanceRequest(
                issuerDid = issuerDocument.id,
                issuerKeyId = issuerDocument.verificationMethod.first().id
            )
        )

        val result = engine.verify(credential, VerificationOptions())
        assertTrue(
            result is VerificationResult.Invalid,
            "A wrong multicodec prefix must fail verification (fail-closed)"
        )
    }

    // --- Finding 13: ECDSA JWS signatures must be P1363 ---------------------------------

    @Test
    fun `ES256 JsonWebSignature2020 round-trip with a DER-emitting KMS`() = runBlocking {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()

        assertEcdsaJwsRoundTrip(
            keyPair = keyPair,
            curve = Curve.P_256,
            crv = "P-256",
            jwsAlgorithm = "ES256",
            expectedSignatureLength = 64,
            signatureProvider = null
        )
    }

    @Test
    fun `ES256K JsonWebSignature2020 round-trip with a DER-emitting KMS`() = runBlocking {
        val bc = bouncyCastle()
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", bc)
        keyPairGenerator.initialize(ECGenParameterSpec("secp256k1"))
        val keyPair = keyPairGenerator.generateKeyPair()

        assertEcdsaJwsRoundTrip(
            keyPair = keyPair,
            curve = Curve.SECP256K1,
            crv = "secp256k1",
            jwsAlgorithm = "ES256K",
            expectedSignatureLength = 64,
            signatureProvider = bc
        )
    }

    private suspend fun assertEcdsaJwsRoundTrip(
        keyPair: KeyPair,
        curve: Curve,
        crv: String,
        jwsAlgorithm: String,
        expectedSignatureLength: Int,
        signatureProvider: Provider?
    ) {
        val issuerDid = Did("did:example:ec-issuer-${jwsAlgorithm.lowercase()}")
        val verificationMethodId = VerificationMethodId.parse("${issuerDid.value}#key-1")

        // Publish the public key as an EC JWK in the issuer's DID document.
        val nimbusKey = ECKey.Builder(curve, keyPair.public as ECPublicKey).build()
        val verificationMethod = VerificationMethod(
            id = verificationMethodId,
            type = "JsonWebKey2020",
            controller = issuerDid,
            publicKeyJwk = mapOf(
                "kty" to "EC",
                "crv" to crv,
                "x" to nimbusKey.x.toString(),
                "y" to nimbusKey.y.toString()
            )
        )
        val didDocument = DidDocument(
            id = issuerDid,
            verificationMethod = listOf(verificationMethod),
            authentication = listOf(verificationMethodId),
            assertionMethod = listOf(verificationMethodId)
        )

        val engine = VcLdProofEngine(
            config = ProofEngineConfig(
                properties = mapOf("kms" to DerEmittingEcKms(keyPair, signatureProvider)),
                didResolver = object : DidResolver {
                    override suspend fun resolve(did: Did): DidResolutionResult =
                        if (did.value == issuerDid.value) {
                            DidResolutionResult.Success(didDocument)
                        } else {
                            DidResolutionResult.Failure.NotFound(did, "Not found")
                        }
                }
            )
        )

        val credential = engine.issue(
            issuanceRequest(
                issuerDid = issuerDid,
                issuerKeyId = verificationMethodId,
                proofType = "JsonWebSignature2020",
                jwsAlgorithm = jwsAlgorithm
            )
        )

        // The detached JWS signature segment must be raw P1363, not the DER the KMS emitted.
        val proof = credential.proof as CredentialProof.LinkedDataProof
        val parts = proof.proofValue.split(".")
        assertEquals(3, parts.size, "proofValue must be a detached JWS (header..signature)")
        val header = JWSHeader.parse(Base64URL(parts[0]))
        assertEquals(jwsAlgorithm, header.algorithm.name, "JWS header alg must match the requested algorithm")
        val signatureBytes = Base64URL(parts[2]).decode()
        assertEquals(
            expectedSignatureLength, signatureBytes.size,
            "$jwsAlgorithm JWS signature segment must be $expectedSignatureLength-byte P1363 r||s " +
                "(got ${signatureBytes.size} bytes — DER embedded raw?)"
        )

        val result = engine.verify(credential, VerificationOptions())
        assertTrue(
            result is VerificationResult.Valid,
            "$jwsAlgorithm JsonWebSignature2020 round-trip must verify, got " +
                "${result::class.simpleName}: " +
                ((result as? VerificationResult.Invalid)?.errors ?: emptyList<String>())
        )
    }

    // --- helpers ------------------------------------------------------------------------

    private fun issuanceRequest(
        issuerDid: Did,
        issuerKeyId: VerificationMethodId,
        proofType: String? = null,
        jwsAlgorithm: String? = null
    ): IssuanceRequest = IssuanceRequest(
        format = ProofSuiteId.VC_LD,
        issuer = Issuer.IriIssuer(Iri(issuerDid.value)),
        issuerKeyId = issuerKeyId,
        credentialSubject = CredentialSubject(
            id = Iri("did:example:holder"),
            claims = mapOf("name" to JsonPrimitive("Alice"))
        ),
        type = listOf(CredentialType.fromString("VerifiableCredential")),
        issuedAt = Clock.System.now(),
        proofOptions = proofOptions {
            option(JsonLdDocumentBuilder.CONTEXTS_OPTION, listOf(TEST_CONTEXT_URL))
            proofType?.let { option("proofType", it) }
            jwsAlgorithm?.let { option("jwsAlgorithm", it) }
        }
    )

    /**
     * Test KMS over a local JCA EC key pair that signs with `SHA256withECDSA`, which emits
     * **ASN.1 DER** — exercising the DER -> P1363 transcode path of the JWS signing code
     * end-to-end (the historical behaviour of JCA-, AWS- and GCP-backed providers).
     */
    private class DerEmittingEcKms(
        private val keyPair: KeyPair,
        private val provider: Provider?
    ) : KeyManagementService {

        override suspend fun getSupportedAlgorithms(): Set<Algorithm> =
            setOf(Algorithm.P256, Algorithm.Secp256k1)

        override suspend fun generateKey(
            algorithm: Algorithm,
            options: Map<String, Any?>
        ): GenerateKeyResult = GenerateKeyResult.Failure.Error(
            algorithm = algorithm,
            reason = "Key generation is not supported by this test KMS"
        )

        override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult =
            GetPublicKeyResult.Failure.KeyNotFound(keyId = keyId)

        override suspend fun sign(
            keyId: KeyId,
            data: ByteArray,
            algorithm: Algorithm?
        ): SignResult {
            val signer = if (provider != null) {
                Signature.getInstance("SHA256withECDSA", provider)
            } else {
                Signature.getInstance("SHA256withECDSA")
            }
            signer.initSign(keyPair.private)
            signer.update(data)
            // DER-encoded SEQUENCE { INTEGER r, INTEGER s } — deliberately NOT P1363.
            return SignResult.Success(signer.sign())
        }

        override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult = DeleteKeyResult.NotFound
    }
}
